package com.yesmin.ai.avatar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one object the rest of the app talks to. It has no dependency on Filament, Gemini or any
 * other AI provider, so the avatar can be reused with a different brain.
 *
 * Threading: the setters are safe to call from any thread. [tick] must be called from one place at a time
 * (the main thread), either by the 3D renderer's frame loop or by the placeholder's light loop.
 */
class AvatarController(val config: AvatarConfiguration) {

    val expression = AvatarExpressionController(config)
    val animation = AvatarAnimationController()
    val voice = AvatarVoiceSynchronizer(config.mouthLatencyMs)

    private val _model = MutableStateFlow<ModelStatus>(ModelStatus.NotLoaded)
    val modelStatus: StateFlow<ModelStatus> = _model.asStateFlow()

    private val _reload = MutableStateFlow(0)
    /** Increase to make the view load the model again (after import, removal or an error). */
    val reloadToken: StateFlow<Int> = _reload.asStateFlow()

    private val _phase = MutableStateFlow(ConversationPhase.IDLE)
    val phase: StateFlow<ConversationPhase> = _phase.asStateFlow()

    private val _effective = MutableStateFlow(AvatarExpression.IDLE)
    /** The expression currently shown. For labels and tests. */
    val effectiveExpression: StateFlow<AvatarExpression> = _effective.asStateFlow()

    private val _mouth = MutableStateFlow(0f)
    val mouthLevel: StateFlow<Float> = _mouth.asStateFlow()

    /** The clip chosen on the last [tick], or null if the model has none. */
    @Volatile var currentClip: ClipFrame? = null; private set

    private var lastTickNanos = 0L

    private fun nowMs() = System.nanoTime() / 1_000_000L

    // ---- conversation state ----

    fun setPhase(p: ConversationPhase) {
        expression.setPhase(p)
        animation.setPhase(p)
        if (p != ConversationPhase.SPEAKING) voice.clear()
        _phase.value = p
    }

    /** Show a mood for [holdMs]. Unknown values never reach here: callers parse with [AvatarExpression.parse]. */
    fun setMood(e: AvatarExpression, holdMs: Long = 6_000L, gesture: Gesture? = null) {
        expression.setMood(e, holdMs, nowMs())
        val g = gesture ?: when (e) {
            AvatarExpression.HAPPY, AvatarExpression.EXCITED -> Gesture.HAPPY
            AvatarExpression.LAUGHING -> Gesture.LAUGH
            else -> null
        }
        if (g != null) animation.request(g, nowMs(), force = gesture != null)
    }

    /** Expression chosen by an AI. Validated here; unsupported values become IDLE. */
    fun applyAiExpression(raw: String?) {
        setMood(AvatarExpression.parseFromAi(raw), 8_000L)
    }

    fun react(r: ExpressionInference.Reaction) = setMood(r.mood, r.holdMs, r.gesture)

    /** The user cut the assistant off, or pressed interrupt: stop the speaking animation now. */
    fun interrupt() {
        voice.clear()
        setPhase(ConversationPhase.LISTENING)
    }

    // ---- voice ----

    /** Loudness of the audio slice that is about to be played. Safe to call from the audio thread. */
    fun onAssistantAudioLevel(rms: Float) = voice.push(rms, nowMs())

    /** A word boundary from text-to-speech: move the mouth briefly. */
    fun pulseMouth() = voice.pulse(nowMs())

    // ---- model ----

    fun setModelStatus(s: ModelStatus) {
        _model.value = s
    }

    fun reload() {
        _reload.value = _reload.value + 1
    }

    // ---- frame update ----

    /** Advance expression, mouth and clip logic by the real time since the last call. */
    fun tick() {
        val now = System.nanoTime()
        val dt = if (lastTickNanos == 0L) 0.016f else ((now - lastTickNanos) / 1e9f).coerceIn(0f, 0.1f)
        if (lastTickNanos != 0L && dt < 0.004f) return
        lastTickNanos = now
        val nowMs = now / 1_000_000L
        val mouth = voice.level(nowMs, dt)
        expression.update(dt, nowMs, mouth)
        currentClip = animation.update(dt)
        _effective.value = expression.effective
        _mouth.value = mouth
    }

    /** Call when the view is hidden so the next frame does not see a huge time step. */
    fun resetClock() {
        lastTickNanos = 0L
    }
}
