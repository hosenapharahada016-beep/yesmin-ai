package com.yesmin.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yesmin.ai.ai.Attachment
import com.yesmin.ai.ai.CallState
import com.yesmin.ai.ai.CaptionLine
import com.yesmin.ai.ai.GeminiException
import com.yesmin.ai.ai.LiveSession
import com.yesmin.ai.ai.ModelInfo
import com.yesmin.ai.ai.Persona
import com.yesmin.ai.ai.Turn
import com.yesmin.ai.automation.CommandRouter
import com.yesmin.ai.automation.RouterResult
import com.yesmin.ai.avatar.AvatarConfiguration
import com.yesmin.ai.avatar.AvatarController
import com.yesmin.ai.avatar.AvatarExpression
import com.yesmin.ai.avatar.AvatarModelLoader
import com.yesmin.ai.avatar.ConversationPhase
import com.yesmin.ai.avatar.ExpressionInference
import com.yesmin.ai.data.db.MemoryEntity
import com.yesmin.ai.voice.CallService
import com.yesmin.ai.voice.Speaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMsg(val fromUser: Boolean, val text: String, val note: Boolean = false)

class ChatState {
    val messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
}

/** A memory Yesmin proposes. Nothing is saved until the user taps Save. */
data class PendingMemory(val type: String, val text: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val a = application as YesminApp
    private val router = CommandRouter(application)
    private val dao = a.db.memoryDao()

    val chat = ChatState()
    val coding = ChatState()
    val speaker = Speaker(application)
    val speakReplies = MutableStateFlow(false)

    // ---------- avatar (independent of Gemini; only this class connects the two) ----------
    val avatar = AvatarController(
        AvatarConfiguration.detect(application, a.settings.avatarQuality, a.settings.avatarHeadMotion)
    )
    val assistantName = MutableStateFlow(a.settings.assistantName)
    val avatarEnabled = MutableStateFlow(a.settings.avatarEnabled)
    val avatarNotice = MutableStateFlow<String?>(null)

    init {
        speaker.listener = object : Speaker.Listener {
            override fun onStart() { if (!liveActive()) avatar.setPhase(ConversationPhase.SPEAKING) }
            override fun onWord() { avatar.pulseMouth() }
            override fun onDone() { if (!liveActive()) avatar.setPhase(ConversationPhase.IDLE) }
        }
    }

    fun setAssistantName(name: String) {
        a.settings.assistantName = name
        assistantName.value = a.settings.assistantName
    }

    fun setAvatarEnabled(on: Boolean) {
        a.settings.avatarEnabled = on
        avatarEnabled.value = on
    }

    fun importAvatarModel(uri: Uri) {
        viewModelScope.launch {
            avatarNotice.value = "Checking model…"
            AvatarModelLoader(getApplication(), avatar.config).importFrom(uri)
                .onSuccess {
                    avatarNotice.value = "Model imported (${it / 1024} KB). Loading…"
                    avatar.reload()
                }
                .onFailure { avatarNotice.value = "Import failed: ${it.message ?: "unknown error"}" }
        }
    }

    fun removeAvatarModel() {
        val ok = AvatarModelLoader(getApplication(), avatar.config).removeCustomModel()
        avatarNotice.value = if (ok) "Imported model removed." else "Could not remove the model file."
        avatar.reload()
    }

    private fun liveActive(): Boolean = when (session.value?.state?.value) {
        CallState.Connecting, CallState.Listening, CallState.Thinking, CallState.Speaking -> true
        else -> false
    }

    // ---------- settings / key ----------
    val themeMode = MutableStateFlow(a.settings.theme)
    val hasKey = MutableStateFlow(a.keys.hasKey())
    val connectionStatus = MutableStateFlow<String?>(null)
    val connectionOk = MutableStateFlow<Boolean?>(null)
    val models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val settingsBusy = MutableStateFlow(false)

    fun saveKey(key: String) {
        if (key.isBlank()) return
        a.keys.save(key)
        hasKey.value = true
        connectionStatus.value = "Key saved. Press Test connection to verify it."
        connectionOk.value = null
    }

    fun removeKey() {
        a.keys.clear()
        hasKey.value = false
        models.value = emptyList()
        connectionStatus.value = "Key removed."
        connectionOk.value = null
    }

    fun testConnection() {
        viewModelScope.launch {
            settingsBusy.value = true
            a.gemini.testConnection()
                .onSuccess {
                    connectionOk.value = true
                    connectionStatus.value = "Connected. Your key can see $it models."
                }
                .onFailure {
                    connectionOk.value = false
                    connectionStatus.value = friendly(it)
                }
            settingsBusy.value = false
        }
    }

    fun loadModels() {
        viewModelScope.launch {
            settingsBusy.value = true
            a.gemini.listModels()
                .onSuccess { list ->
                    models.value = list.sortedBy { it.id }
                    connectionOk.value = true
                    connectionStatus.value = "Loaded ${list.size} models."
                }
                .onFailure {
                    connectionOk.value = false
                    connectionStatus.value = friendly(it)
                }
            settingsBusy.value = false
        }
    }

    fun setTheme(mode: String) {
        a.settings.theme = mode
        themeMode.value = mode
    }

    // ---------- memory ----------
    val memories: StateFlow<List<MemoryEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pending = MutableStateFlow<PendingMemory?>(null)

    fun addMemory(type: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { dao.insert(MemoryEntity(type = type, text = text.trim())) }
    }

    fun updateMemory(m: MemoryEntity, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch { dao.update(m.copy(text = newText.trim())) }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun clearMemories() {
        viewModelScope.launch { dao.clearAll() }
    }

    fun confirmPending() {
        pending.value?.let { addMemory(it.type, it.text) }
        pending.value = null
    }

    fun dismissPending() {
        pending.value = null
    }

    // ---------- chat / coding ----------
    fun send(
        state: ChatState,
        text: String,
        coding: Boolean = false,
        useSearch: Boolean = false
    ) {
        if (text.isBlank() || state.busy.value) return
        state.error.value = null
        state.messages.value = state.messages.value + ChatMsg(true, text)

        if (!coding) {
            val cmd: RouterResult? = router.handle(text)
            if (cmd != null) {
                state.messages.value = state.messages.value + ChatMsg(false, cmd.message, note = true)
                if (cmd.data != null) {
                    ask(state, false, useSearch = false,
                        override = "Explain this in simple words and point out anything important:\n${cmd.data}")
                }
                return
            }
            // Face only: expressions never trigger phone actions.
            if (!liveActive()) ExpressionInference.fromUser(text)?.let { avatar.react(it) }
        }
        ask(state, coding, useSearch, null)
    }

    private fun ask(state: ChatState, coding: Boolean, useSearch: Boolean, override: String?) {
        viewModelScope.launch {
            state.busy.value = true
            if (!coding && !liveActive()) avatar.setPhase(ConversationPhase.THINKING)
            val mems = if (a.settings.memoryEnabled) dao.recent(30) else emptyList()
            var system = Persona.build(a.settings, mems, withMoodTag = !coding)
            if (coding) system += "\n" + Persona.codingAddon

            val turns = state.messages.value.filter { !it.note }
                .takeLast(20)
                .map { Turn(if (it.fromUser) "user" else "model", it.text) }
                .dropWhile { it.role != "user" }
                .toMutableList()
            if (override != null && turns.isNotEmpty()) turns[turns.lastIndex] = Turn("user", override)

            a.gemini.generate(a.settings.chatModel, system, turns, a.settings.temperature, null, useSearch)
                .onSuccess { raw ->
                    val (afterMood, mood) = Persona.extractMood(raw)
                    val (shown, remember) = Persona.extractRemember(afterMood)
                    state.messages.value = state.messages.value + ChatMsg(false, shown)
                    if (remember != null && a.settings.memoryEnabled && pending.value == null) {
                        pending.value = PendingMemory("fact", remember)
                    }
                    if (!coding && !liveActive()) {
                        avatar.setPhase(ConversationPhase.IDLE)
                        val reaction = ExpressionInference.fromAssistant(shown)
                        when {
                            reaction != null -> avatar.react(reaction)
                            mood != null -> avatar.setMood(mood, 6_000L)
                        }
                    }
                    if (speakReplies.value) speaker.speak(shown)
                }
                .onFailure {
                    state.error.value = friendly(it)
                    if (!liveActive()) avatar.setPhase(ConversationPhase.IDLE)
                }
            state.busy.value = false
        }
    }

    fun clearChat(state: ChatState) {
        state.messages.value = emptyList()
        state.error.value = null
        speaker.stop()
    }

    // ---------- tools ----------
    val toolResult = MutableStateFlow<String?>(null)
    val toolBusy = MutableStateFlow(false)
    val toolError = MutableStateFlow<String?>(null)

    fun runTool(prompt: String, attachment: Attachment? = null, useSearch: Boolean = false) {
        if (toolBusy.value) return
        viewModelScope.launch {
            toolBusy.value = true
            toolError.value = null
            toolResult.value = null
            val system = Persona.build(a.settings, emptyList()) +
                "\nFor this task, give a clear, well-organized answer."
            a.gemini.generate(
                a.settings.chatModel, system, listOf(Turn("user", prompt)),
                a.settings.temperature, attachment, useSearch
            )
                .onSuccess { toolResult.value = Persona.extractRemember(it).first }
                .onFailure { toolError.value = friendly(it) }
            toolBusy.value = false
        }
    }

    fun showToolText(text: String) {
        toolError.value = null
        toolResult.value = text
    }

    fun showToolError(msg: String) {
        toolResult.value = null
        toolError.value = msg
    }

    // ---------- automation panel ----------
    val commandResult = MutableStateFlow<RouterResult?>(null)

    fun runCommand(text: String) {
        commandResult.value = router.handle(text)
            ?: RouterResult("That isn't a phone command I know. Try the examples below, or chat with me in AI Chat.", false)
    }

    // ---------- live call ----------
    val session = MutableStateFlow<LiveSession?>(null)
    val callNotice = MutableStateFlow<String?>(null)

    private var avatarJobs: List<Job> = emptyList()
    private var lastReactionKey = ""

    fun startCall() {
        val key = a.keys.get()
        if (key.isNullOrBlank()) {
            callNotice.value = "Add your Gemini API key in Settings first."
            return
        }
        if (liveActive()) return // already in a call
        callNotice.value = null
        viewModelScope.launch {
            val mems = if (a.settings.memoryEnabled) dao.recent(30) else emptyList()
            val system = Persona.build(a.settings, mems) +
                "\nThis is a live voice call. Speak naturally and briefly."
            val s = LiveSession(
                scope = viewModelScope,
                http = a.gemini.http,
                apiKey = key,
                model = a.settings.liveModel,
                systemPrompt = system,
                voiceName = a.settings.voiceName,
                enableExpressionTool = a.settings.avatarAiExpressions
            )
            bindAvatar(s)
            session.value = s
            CallService.start(getApplication())
            s.start()
        }
    }

    /** Wires one call to the avatar. Nothing in LiveSession knows about the avatar's internals. */
    private fun bindAvatar(s: LiveSession) {
        avatarJobs.forEach { it.cancel() }
        lastReactionKey = ""
        s.onOutputLevel = { avatar.onAssistantAudioLevel(it) }
        s.onExpression = { raw -> avatar.applyAiExpression(raw) } // validated inside; unknown becomes IDLE
        s.onInterrupted = { avatar.interrupt() }
        avatarJobs = listOf(
            viewModelScope.launch {
                s.state.collect { st ->
                    avatar.setPhase(
                        when (st) {
                            CallState.Listening -> ConversationPhase.LISTENING
                            CallState.Thinking -> ConversationPhase.THINKING
                            CallState.Speaking -> ConversationPhase.SPEAKING
                            else -> ConversationPhase.IDLE
                        }
                    )
                }
            },
            viewModelScope.launch { s.captions.collect { reactToCaptions(s, it) } }
        )
    }

    private fun reactToCaptions(s: LiveSession, lines: List<CaptionLine>) {
        val last = lines.lastOrNull() ?: return
        if (last.fromUser) {
            if (ExpressionInference.isStopSpeaking(last.text)) {
                val key = "stop:${lines.size}"
                if (key != lastReactionKey) {
                    lastReactionKey = key
                    s.interrupt() // existing voice-session control; it also resets the avatar
                }
                return
            }
            val r = ExpressionInference.fromUser(last.text) ?: return
            val key = "u:${lines.size}:${r.mood}"
            if (key != lastReactionKey) {
                lastReactionKey = key
                avatar.react(r)
            }
        } else {
            val r = ExpressionInference.fromAssistant(last.text.takeLast(60)) ?: return
            val key = "a:${lines.size}:${r.mood}"
            if (key != lastReactionKey) {
                lastReactionKey = key
                avatar.react(r)
            }
        }
    }

    fun endCall() {
        val s = session.value ?: return
        val transcript = s.transcriptText()
        s.end()
        avatar.setPhase(ConversationPhase.IDLE)
        CallService.stop(getApplication())
        if (transcript.length > 80 && a.settings.memoryEnabled) summarizeCall(transcript)
    }

    /** Called by the UI when a call fails or is closed so the foreground notification goes away. */
    fun callFinished() {
        CallService.stop(getApplication())
        avatar.setPhase(ConversationPhase.IDLE)
    }

    private fun summarizeCall(transcript: String) {
        viewModelScope.launch {
            val prompt = "Summarize this voice call in 1-2 short sentences, keeping only useful, " +
                "non-sensitive facts or preferences about the user:\n$transcript"
            a.gemini.generate(
                a.settings.chatModel, "You write brief neutral summaries.",
                listOf(Turn("user", prompt)), 0.3f
            ).onSuccess { pending.value = PendingMemory("summary", it) }
        }
    }

    private fun friendly(t: Throwable): String =
        (t as? GeminiException)?.message ?: "Something went wrong (${t.javaClass.simpleName})."

    override fun onCleared() {
        avatarJobs.forEach { it.cancel() }
        session.value?.end()
        CallService.stop(getApplication())
        speaker.shutdown()
        super.onCleared()
    }
}
