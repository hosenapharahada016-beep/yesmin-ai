package com.yesmin.ai.avatar

/** One animation clip found in the model. */
data class ClipInfo(val index: Int, val name: String, val durationSec: Float)

/** What to show this frame: which clip, at what time. */
data class ClipFrame(val index: Int, val timeSec: Float)

/**
 * Chooses which skeletal clip to play. Pure Kotlin, no rendering calls.
 *
 * Priority: one-shot gesture > clip for the current conversation phase > idle loop.
 * Gestures that the model has no clip for are reported as unavailable (never faked).
 *
 * Not implemented: cross-fading between two skeletal clips. Clips switch directly. Face expressions
 * and head motion are smoothed separately by [AvatarExpressionController].
 */
class AvatarAnimationController {

    private var clips = emptyMap<Gesture, ClipInfo>()
    private var oneShot: Gesture? = null
    private var oneShotTime = 0f
    private var phaseLoop: Gesture? = null
    private var loopKey: Gesture? = null
    private var loopTime = 0f
    private var lastAutoMs = Long.MIN_VALUE / 2

    /** Playback speed multiplier, 0.25 to 2. */
    @Volatile var speed = 1f
        set(v) { field = v.coerceIn(0.25f, 2f) }

    val available: Set<Gesture> get() = clips.keys

    /** Map the model's clip names to gestures by keyword (case-insensitive). */
    fun setClips(found: List<ClipInfo>) {
        val map = LinkedHashMap<Gesture, ClipInfo>()
        for (g in Gesture.values()) {
            val match = found.firstOrNull { c -> g.aliases.any { c.name.lowercase().contains(it) } }
            if (match != null) map[g] = match
        }
        clips = map
        oneShot = null
        loopKey = null
        loopTime = 0f
    }

    /** Which gesture loop belongs to a conversation phase. */
    fun setPhase(p: ConversationPhase) {
        phaseLoop = when (p) {
            ConversationPhase.LISTENING -> Gesture.LISTENING
            ConversationPhase.THINKING -> Gesture.THINKING
            ConversationPhase.SPEAKING -> Gesture.SPEAKING
            ConversationPhase.IDLE -> null
        }
    }

    /**
     * Ask for a one-shot gesture.
     * @param force false applies a cooldown so gestures are not constantly repeated
     * @return false if the model has no such clip or it was skipped by the cooldown
     */
    fun request(g: Gesture, nowMs: Long, force: Boolean = false): Boolean {
        if (g !in clips) return false
        if (!force && nowMs - lastAutoMs < AUTO_COOLDOWN_MS) return false
        lastAutoMs = nowMs
        oneShot = g
        oneShotTime = 0f
        return true
    }

    /** @return the frame to show, or null when the model has no usable clip. */
    fun update(dt: Float): ClipFrame? {
        val step = dt.coerceIn(0f, 0.1f) * speed
        oneShot?.let { g ->
            val clip = clips[g]
            if (clip != null) {
                oneShotTime += step
                if (oneShotTime < clip.durationSec) return ClipFrame(clip.index, oneShotTime)
            }
            oneShot = null
        }
        val key = phaseLoop?.takeIf { it in clips } ?: Gesture.IDLE
        val clip = clips[key] ?: return null
        if (key != loopKey) {
            loopKey = key
            loopTime = 0f
        }
        loopTime += step
        if (clip.durationSec > 0f) loopTime %= clip.durationSec
        return ClipFrame(clip.index, loopTime)
    }

    companion object {
        const val AUTO_COOLDOWN_MS = 8_000L
    }
}
