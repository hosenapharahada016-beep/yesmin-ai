package com.yesmin.ai.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures 16 kHz mono PCM16 (the Live API input format) in ~100 ms chunks.
 * Only one instance may be running; start() while running is ignored.
 * Uses VOICE_COMMUNICATION source so the platform can apply echo cancellation.
 */
class MicCapture(private val scope: CoroutineScope) {

    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var job: Job? = null

    @Volatile var muted = false

    val running get() = job?.isActive == true

    /** Returns false if the microphone could not be opened. */
    @SuppressLint("MissingPermission") // Caller checks RECORD_AUDIO before calling.
    fun start(onChunk: (ByteArray) -> Unit, onLevel: (Float) -> Unit): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val chunkBytes = RATE / 10 * 2 // 100 ms
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, chunkBytes * 4)
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        record = rec
        if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true }
        if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true }
        rec.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(chunkBytes)
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                val chunk = buf.copyOf(n)
                onLevel(rms(chunk))
                // When muted we send nothing, so the model hears silence from us.
                if (!muted) onChunk(chunk)
            }
        }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { record?.stop() }
        runCatching { aec?.release() }
        runCatching { ns?.release() }
        runCatching { record?.release() }
        aec = null; ns = null; record = null
    }

    private fun rms(b: ByteArray): Float {
        var sum = 0.0
        var i = 0
        val samples = b.size / 2
        while (i + 1 < b.size) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
        }
        if (samples == 0) return 0f
        return (kotlin.math.sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val RATE = 16000
    }
}
