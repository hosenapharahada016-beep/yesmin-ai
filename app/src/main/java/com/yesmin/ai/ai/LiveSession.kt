package com.yesmin.ai.ai

import android.util.Base64
import com.yesmin.ai.voice.MicCapture
import com.yesmin.ai.voice.PcmPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * Guards: start() is a no-op unless the session is Idle/Ended/Error, so there is never
 * more than one microphone capture or one socket per instance.
 */
class LiveSession(
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val voiceName: String
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

    private val mic = MicCapture(scope)
    private val player = PcmPlayer()
    private var socket: WebSocket? = null
    private var ticker: Job? = null
    @Volatile private var ready = false
    @Volatile private var lastActivity = System.currentTimeMillis()
    @Volatile private var modelTurnOpen = false

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
        _state.value = CallState.Connecting

        // Documented auth for raw WebSocket: key as a query parameter. OkHttp does not log URLs.
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val req = Request.Builder().url(url).build()
        socket = http.newWebSocket(req, Listener())
    }

    /** Barge-in button: stop Yesmin talking right now and go back to listening. */
    fun interrupt() {
        player.clear()
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

    private fun cleanup() {
        ready = false
        ticker?.cancel(); ticker = null
        mic.stop()
        player.stop()
        socket?.let { runCatching { it.close(1000, "bye") } }
        socket = null
        _level.value = 0f
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
        val setup = JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", gen)
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        ws.send(JSONObject().put("setup", setup).toString())
    }

    private fun beginStreaming() {
        player.onDrained = {
            // Audio finished playing and the model's turn is over: return to listening.
            if (!modelTurnOpen && _state.value == CallState.Speaking) _state.value = CallState.Listening
        }
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
            while (true) {
                delay(1000)
                _seconds.value = _seconds.value + 1
                if (System.currentTimeMillis() - lastActivity > IDLE_LIMIT_MS) {
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
        val sc = json.optJSONObject("serverContent") ?: return
        lastActivity = System.currentTimeMillis()

        if (sc.optBoolean("interrupted")) {
            player.clear()
            modelTurnOpen = false
            _state.value = CallState.Listening
        }
        sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
            appendCaption(true, it)
            if (_state.value == CallState.Listening) _state.value = CallState.Thinking
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
                    player.enqueue(Base64.decode(data, Base64.DEFAULT))
                    _state.value = CallState.Speaking
                }
            }
        }
        if (sc.optBoolean("turnComplete")) {
            modelTurnOpen = false
            if (!player.isPlaying) _state.value = CallState.Listening
            // else: onDrained returns us to Listening once playback ends.
        }
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

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sendSetup(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (_state.value == CallState.Ended) return
            val code = response?.code
            val msg = when (code) {
                400, 401, 403 -> "Live connection was refused (HTTP $code). Check that your API key is valid and has Live API access."
                404 -> "Live model not found (HTTP 404). Choose another Live model in Settings."
                429 -> "Live quota reached (HTTP 429). Try again later."
                else -> "Connection lost. Check your internet connection."
            }
            fail(msg)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (_state.value == CallState.Ended || _state.value == CallState.Error) return
            if (code == 1000) {
                end()
            } else {
                val why = if (reason.isNotBlank()) " Reason: $reason" else ""
                fail("The live call was closed by the server (code $code).$why")
            }
        }
    }

    companion object {
        private const val IDLE_LIMIT_MS = 5 * 60 * 1000L
    }
}
