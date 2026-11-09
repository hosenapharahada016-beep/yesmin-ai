package com.yesmin.ai.ai

import android.util.Base64
import com.yesmin.ai.avatar.AvatarExpression
import com.yesmin.ai.voice.MicCapture
import com.yesmin.ai.voice.PcmPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

enum class CallState { Idle, Connecting, Listening, Thinking, Speaking, Ended, Error }

data class CaptionLine(val fromUser: Boolean, val text: String)

/**
 * One live voice call over the Gemini Live API WebSocket (BidiGenerateContent).
 *
 * Protocol used (per Google's Live API docs):
 *  - first message: {"setup": {...}}, wait for "setupComplete"
 *  - mic audio:     {"realtimeInput":{"audio":{"data":<b64>,"mimeType":"audio/pcm;rate=16000"}}}
 *  - server audio:  serverContent.modelTurn.parts[].inlineData (PCM16 24 kHz)
 *  - serverContent.interrupted => the user barged in; queued playback is dropped
 *  - optional function call "set_avatar_expression": {"toolCall":...} answered with {"toolResponse":...}
 *
 * Guards: start() is a no-op unless the session is Idle/Ended/Error, so there is never
 * more than one microphone capture or one socket per instance.
 *
 * The avatar hooks ([onOutputLevel], [onExpression], [onInterrupted]) are plain callbacks, so this class
 * does not depend on the avatar package for anything except the expression name list.
 */
class LiveSession(
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val voiceName: String,
    enableExpressionTool: Boolean = false
) {
    private val _state = MutableStateFlow(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _captions = MutableStateFlow<List<CaptionLine>>(emptyList())
    val captions: StateFlow<List<CaptionLine>> = _captions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    /** Loudness (0..1) of the assistant voice slice about to be heard. Called on an audio thread. */
    @Volatile var onOutputLevel: ((Float) -> Unit)? = null

    /** The model asked for this expression name (not yet validated). Called on the network thread. */
    @Volatile var onExpression: ((String?) -> Unit)? = null

    /** Playback was cut off, by the user or by the server's barge-in detection. */
    @Volatile var onInterrupted: (() -> Unit)? = null

    private val mic = MicCapture(scope)
    private val player = PcmPlayer()
    private var socket: WebSocket? = null
    private var ticker: Job? = null
    @Volatile private var ready = false
    @Volatile private var lastActivity = System.currentTimeMillis()
    @Volatile private var modelTurnOpen = false
    @Volatile private var generation = 0

    // Thinking = the user finished talking and the model has not started answering yet.
    @Volatile private var userSpeechAt = 0L
    @Volatile private var awaitingReply = false

    private var toolsActive = enableExpressionTool
    private var retriedWithoutTools = false

    /** True while the expression function call is part of this session's setup. */
    val usingExpressionTool: Boolean get() = toolsActive

    var muted: Boolean
        get() = mic.muted
        set(v) { mic.muted = v }

    fun start() {
        val s = _state.value
        if (s == CallState.Connecting || s == CallState.Listening ||
            s == CallState.Thinking || s == CallState.Speaking
        ) return // already running: prevents duplicate mic sessions
        _error.value = null
        _captions.value = emptyList()
        _seconds.value = 0
        ready = false
        awaitingReply = false
        _state.value = CallState.Connecting
        connect()
    }

    private fun connect() {
        val gen = ++generation
        // Documented auth for a raw WebSocket: the key as a query parameter. OkHttp does not log URLs.
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        socket = http.newWebSocket(Request.Builder().url(url).build(), Listener(gen))
    }

    /** Barge-in button: stop the assistant talking right now and go back to listening. */
    fun interrupt() {
        player.clear()
        modelTurnOpen = false
        onInterrupted?.invoke()
        if (_state.value == CallState.Speaking) _state.value = CallState.Listening
    }

    fun end() {
        if (_state.value == CallState.Ended || _state.value == CallState.Idle) return
        cleanup()
        if (_state.value != CallState.Error) _state.value = CallState.Ended
    }

    fun transcriptText(): String =
        _captions.value.joinToString("\n") { (if (it.fromUser) "User: " else "Yesmin: ") + it.text }

    private fun fail(msg: String) {
        _error.value = msg
        cleanup()
        _state.value = CallState.Error
    }

    /** If the server refuses a setup that includes the expression tool, try once more without it. */
    private fun failOrRetry(msg: String) {
        if (!ready && toolsActive && !retriedWithoutTools) {
            retriedWithoutTools = true
            toolsActive = false
            socket?.cancel()
            socket = null
            connect()
            return
        }
        fail(msg)
    }

    private fun cleanup() {
        ready = false
        generation++ // ignore any late callbacks from the old socket
        ticker?.cancel(); ticker = null
        mic.stop()
        player.onLevel = null
        player.stop()
        socket?.let { runCatching { it.close(1000, "bye") } }
        socket = null
        _level.value = 0f
        onOutputLevel?.invoke(0f)
    }

    private fun sendSetup(ws: WebSocket) {
        val gen = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put(
                "speechConfig", JSONObject().put(
                    "voiceConfig", JSONObject().put(
                        "prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)
                    )
                )
            )
        val prompt = if (toolsActive) systemPrompt + "\n" + TOOL_INSTRUCTION else systemPrompt
        val setup = JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", gen)
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        if (toolsActive) setup.put("tools", JSONArray().put(expressionTool()))
        ws.send(JSONObject().put("setup", setup).toString())
    }

    private fun expressionTool(): JSONObject {
        val names = JSONArray()
        AvatarExpression.aiChoosable.forEach { names.put(it.name) }
        val param = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject().put(
                    "expression",
                    JSONObject().put("type", "STRING").put("enum", names)
                        .put("description", "How the avatar's face should look while you answer.")
                )
            )
            .put("required", JSONArray().put("expression"))
        val decl = JSONObject()
            .put("name", TOOL_NAME)
            .put("description", "Sets the facial expression of the on-screen avatar. Has no other effect.")
            .put("parameters", param)
        return JSONObject().put("functionDeclarations", JSONArray().put(decl))
    }

    private fun beginStreaming() {
        player.onDrained = {
            // Audio finished playing and the model's turn is over: return to listening.
            if (!modelTurnOpen && _state.value == CallState.Speaking) _state.value = CallState.Listening
        }
        player.onLevel = { onOutputLevel?.invoke(it) }
        player.start()
        val ok = mic.start(
            onChunk = { chunk ->
                val ws = socket
                if (ready && ws != null) {
                    val msg = JSONObject().put(
                        "realtimeInput", JSONObject().put(
                            "audio", JSONObject()
                                .put("data", Base64.encodeToString(chunk, Base64.NO_WRAP))
                                .put("mimeType", "audio/pcm;rate=16000")
                        )
                    )
                    ws.send(msg.toString())
                }
            },
            onLevel = { _level.value = it }
        )
        if (!ok) {
            fail("Could not open the microphone. Another app may be using it.")
            return
        }
        _state.value = CallState.Listening
        lastActivity = System.currentTimeMillis()
        ticker = scope.launch {
            var quarter = 0
            while (isActive) {
                delay(250)
                val now = System.currentTimeMillis()
                if (++quarter % 4 == 0) _seconds.value = _seconds.value + 1
                if (awaitingReply) {
                    val quiet = now - userSpeechAt
                    if (_state.value == CallState.Listening && quiet > THINK_AFTER_MS) {
                        _state.value = CallState.Thinking
                    } else if (_state.value == CallState.Thinking && quiet > THINK_TIMEOUT_MS) {
                        awaitingReply = false
                        _state.value = CallState.Listening
                    }
                }
                if (now - lastActivity > IDLE_LIMIT_MS) {
                    fail("Call ended after a long silence.")
                    break
                }
            }
        }
    }

    private fun handle(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (json.has("setupComplete")) {
            ready = true
            beginStreaming()
            return
        }
        json.optJSONObject("toolCall")?.let { handleToolCall(it); return }

        val sc = json.optJSONObject("serverContent") ?: return
        lastActivity = System.currentTimeMillis()

        if (sc.optBoolean("interrupted")) {
            player.clear()
            modelTurnOpen = false
            onInterrupted?.invoke()
            _state.value = CallState.Listening
        }
        sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
            appendCaption(true, it)
            userSpeechAt = System.currentTimeMillis()
            awaitingReply = true
            if (_state.value == CallState.Thinking) _state.value = CallState.Listening
        }
        sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
            appendCaption(false, it)
        }
        val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val data = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data").orEmpty()
                if (data.isNotEmpty()) {
                    modelTurnOpen = true
                    awaitingReply = false
                    player.enqueue(Base64.decode(data, Base64.DEFAULT))
                    _state.value = CallState.Speaking
                }
            }
        }
        if (sc.optBoolean("turnComplete")) {
            modelTurnOpen = false
            awaitingReply = false
            if (!player.isPlaying) _state.value = CallState.Listening
            // else: onDrained returns us to Listening once playback ends.
        }
    }

    private fun handleToolCall(toolCall: JSONObject) {
        val calls = toolCall.optJSONArray("functionCalls") ?: return
        val responses = JSONArray()
        for (i in 0 until calls.length()) {
            val c = calls.getJSONObject(i)
            val result = if (c.optString("name") == TOOL_NAME) {
                onExpression?.invoke(c.optJSONObject("args")?.optString("expression"))
                JSONObject().put("result", "ok")
            } else {
                JSONObject().put("error", "unsupported function")
            }
            responses.put(
                JSONObject().put("id", c.optString("id")).put("name", c.optString("name")).put("response", result)
            )
        }
        socket?.send(
            JSONObject().put("toolResponse", JSONObject().put("functionResponses", responses)).toString()
        )
    }

    private fun appendCaption(fromUser: Boolean, piece: String) {
        val cur = _captions.value
        val last = cur.lastOrNull()
        _captions.value = if (last != null && last.fromUser == fromUser) {
            cur.dropLast(1) + last.copy(text = last.text + piece)
        } else {
            (cur + CaptionLine(fromUser, piece.trimStart())).takeLast(40)
        }
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (gen == generation) sendSetup(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (gen == generation) handle(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (gen == generation) handle(bytes.utf8())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (gen != generation || _state.value == CallState.Ended) return
            val code = response?.code
            val msg = when (code) {
                400, 401, 403 -> "Live connection was refused (HTTP $code). Check that your API key is valid and has Live API access."
                404 -> "Live model not found (HTTP 404). Choose another Live model in Settings."
                429 -> "Live quota reached (HTTP 429). Try again later."
                else -> "Connection lost. Check your internet connection."
            }
            failOrRetry(msg)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (gen != generation) return
            if (_state.value == CallState.Ended || _state.value == CallState.Error) return
            if (code == 1000) {
                end()
            } else {
                val why = if (reason.isNotBlank()) " Reason: $reason" else ""
                failOrRetry("The live call was closed by the server (code $code).$why")
            }
        }
    }

    companion object {
        private const val IDLE_LIMIT_MS = 5 * 60 * 1000L
        private const val THINK_AFTER_MS = 900L
        private const val THINK_TIMEOUT_MS = 8_000L
        const val TOOL_NAME = "set_avatar_expression"
        private const val TOOL_INSTRUCTION =
            "You have an on-screen avatar. At the start of each answer, call set_avatar_expression once " +
                "with the expression that fits your reply. Never mention or explain this function to the user."
    }
}
