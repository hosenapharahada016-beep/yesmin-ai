package com.yesmin.ai.avatar

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turns "what the avatar should feel" into smooth face weights and head angles every frame.
 * Pure Kotlin: it knows nothing about Filament or any AI provider.
 *
 * Two inputs decide the face:
 *  - phase: what the conversation is doing (listening, thinking, speaking)
 *  - mood: an expression requested by the AI, which lasts for a limited time
 */
class AvatarExpressionController(
    private val config: AvatarConfiguration,
    private val random: Random = Random.Default
) {
    @Volatile private var mood = AvatarExpression.IDLE
    @Volatile private var moodUntilMs = 0L
    @Volatile private var phase = ConversationPhase.IDLE

    /** Current face weights, indexed by [FaceShape.ordinal]. Read these after [update]. */
    val weights = FloatArray(FaceShape.values().size)

    /** Head angles in degrees. */
    var headPitch = 0f; private set
    var headYaw = 0f; private set
    var headRoll = 0f; private set

    /** The expression currently shown (after combining phase and mood). */
    @Volatile var effective = AvatarExpression.IDLE; private set

    private val target = FloatArray(weights.size)
    private var baseBlink = 0f
    private var time = 0f
    private var nextBlinkAt = 2.5f
    private var blinkT = -1f

    fun setMood(expression: AvatarExpression, holdMs: Long, nowMs: Long) {
        mood = expression
        moodUntilMs = if (expression == AvatarExpression.IDLE) 0L else nowMs + holdMs
    }

    fun setPhase(p: ConversationPhase) {
        phase = p
    }

    /** Which expression applies at [nowMs]. THINKING and LISTENING phases take priority over an old mood. */
    fun effectiveAt(nowMs: Long): AvatarExpression {
        val moodActive = mood != AvatarExpression.IDLE && nowMs < moodUntilMs
        return when (phase) {
            ConversationPhase.THINKING -> AvatarExpression.THINKING
            ConversationPhase.SPEAKING -> if (moodActive) mood else AvatarExpression.SPEAKING
            ConversationPhase.LISTENING -> if (moodActive && mood != AvatarExpression.THINKING) mood else AvatarExpression.LISTENING
            ConversationPhase.IDLE -> if (moodActive) mood else AvatarExpression.IDLE
        }
    }

    /** @param mouthLevel 0..1 voice loudness from [AvatarVoiceSynchronizer] */
    fun update(dt: Float, nowMs: Long, mouthLevel: Float) {
        val step = dt.coerceIn(0f, 0.1f)
        time += step
        val eff = effectiveAt(nowMs)
        effective = eff

        java.util.Arrays.fill(target, 0f)
        ExpressionPresets.shapes(eff).forEach { (shape, w) -> target[shape.ordinal] = w }

        var mouth = mouthLevel
        if (eff == AvatarExpression.LAUGHING) {
            mouth = max(mouth, 0.25f + 0.2f * sin(2f * PI.toFloat() * 5f * time))
        }
        val m = FaceShape.MOUTH_OPEN.ordinal
        target[m] = max(target[m], mouth * config.mouthGain).coerceIn(0f, 1f)

        for (i in weights.indices) {
            if (i == FaceShape.BLINK.ordinal) continue
            val rate = if (i == m) 22f else 9f
            weights[i] += (target[i] - weights[i]) * (1f - exp(-rate * step))
        }
        baseBlink += (target[FaceShape.BLINK.ordinal] - baseBlink) * (1f - exp(-6f * step))
        weights[FaceShape.BLINK.ordinal] = max(baseBlink, blinkEnvelope(step, eff))

        updateHead(step, eff, mouth)
    }

    private fun blinkEnvelope(dt: Float, eff: AvatarExpression): Float {
        if (blinkT < 0f) {
            if (time >= nextBlinkAt) blinkT = 0f else return 0f
        }
        blinkT += dt
        val duration = if (eff == AvatarExpression.SLEEPY) 0.3f else 0.17f
        if (blinkT >= duration) {
            blinkT = -1f
            val double = random.nextFloat() < 0.12f
            nextBlinkAt = time + if (double) 0.2f else 2.5f + random.nextFloat() * 3.5f
            return 0f
        }
        val p = blinkT / duration
        return if (p < 0.4f) p / 0.4f else (1f - p) / 0.6f
    }

    private fun updateHead(dt: Float, eff: AvatarExpression, mouth: Float) {
        val s = config.motionScale
        val two = 2f * PI.toFloat()
        val calm = if (eff == AvatarExpression.SLEEPY) 0.5f else 1f
        val (bp, by, br) = ExpressionPresets.headPose(eff)
        var pitch = bp + 1.1f * sin(two * 0.29f * time + 1f) * calm
        var yaw = by + 2.2f * sin(two * 0.21f * time) * calm
        val roll = br + 0.9f * sin(two * 0.17f * time + 2f) * calm
        if (eff == AvatarExpression.SPEAKING || phase == ConversationPhase.SPEAKING) {
            pitch += 1.6f * sin(two * 1.6f * time) * mouth
        }
        if (eff == AvatarExpression.LAUGHING) pitch += 3f * sin(two * 4.5f * time)
        if (eff == AvatarExpression.EXCITED) yaw += 2f * sin(two * 1.2f * time)

        val k = 1f - exp(-4f * dt)
        headPitch += (pitch * s - headPitch) * k
        headYaw += (yaw * s - headYaw) * k
        headRoll += (roll * s - headRoll) * k
    }
}
