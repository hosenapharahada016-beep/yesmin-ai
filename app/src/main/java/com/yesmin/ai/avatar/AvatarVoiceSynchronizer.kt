package com.yesmin.ai.avatar

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/**
 * Turns the loudness of the audio being played into a mouth-open value.
 *
 * This is amplitude based only. It opens and closes the mouth in time with the voice but does NOT
 * produce correct mouth shapes for each sound. Real phoneme lip sync needs viseme blend shapes in the
 * model plus a phoneme or viseme source (for example an audio-analysis library or a TTS that reports
 * visemes). Gemini Live does not supply viseme timing.
 *
 * Levels arrive from the audio thread a little before the sound is actually heard, so each sample is
 * held back by [latencyMs] before it is used.
 */
class AvatarVoiceSynchronizer(private val latencyMs: Int) {

    private class Sample(val dueMs: Long, val level: Float)

    private val lock = Any()
    private val queue = ArrayDeque<Sample>()
    private var target = 0f
    private var shown = 0f

    /** @param rms loudness of a short audio slice, 0..1 */
    fun push(rms: Float, nowMs: Long, delayMs: Int = latencyMs) {
        synchronized(lock) {
            queue.addLast(Sample(nowMs + delayMs, normalize(rms)))
            while (queue.size > 500) queue.removeFirst()
        }
    }

    /** Short mouth movement without audio, used for text-to-speech word boundaries. */
    fun pulse(nowMs: Long) {
        synchronized(lock) {
            queue.addLast(Sample(nowMs, 0.75f))
            queue.addLast(Sample(nowMs + 110, 0.1f))
        }
    }

    /** Close the mouth immediately (interruption or end of speech). */
    fun clear() {
        synchronized(lock) {
            queue.clear()
            target = 0f
            shown = 0f
        }
    }

    /** Call once per frame. @return smoothed mouth opening, 0..1 */
    fun level(nowMs: Long, dtSec: Float): Float {
        synchronized(lock) {
            while (queue.isNotEmpty() && queue.first().dueMs <= nowMs) {
                target = queue.removeFirst().level
                lastSampleMs = nowMs
            }
            // If audio stops arriving, the mouth must not stay stuck open.
            if (queue.isEmpty() && nowMs - lastSampleMs > 300L) target = 0f
            val tau = if (target > shown) 0.035f else 0.09f
            shown += (target - shown) * (1f - exp(-dtSec / tau))
            return shown.coerceIn(0f, 1f)
        }
    }

    private var lastSampleMs = 0L

    companion object {
        /** Quiet noise stays at 0; normal speech spreads over the 0..1 range. */
        fun normalize(rms: Float): Float {
            if (rms < 0.012f) return 0f
            return min(1f, (rms - 0.012f) * 5f).pow(0.75f)
        }
    }
}
