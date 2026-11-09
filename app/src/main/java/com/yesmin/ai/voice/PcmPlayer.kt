package com.yesmin.ai.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Plays 24 kHz mono PCM16 (the Live API output format) from a queue.
 * clear() drops queued audio instantly, which is what barge-in needs.
 *
 * Audio is written in 20 ms slices. Before each slice is written, its loudness is reported through
 * [onLevel] so the avatar's mouth can follow the voice. Because AudioTrack.write blocks when its buffer
 * is full, the report happens close to (a little before) the moment the slice is heard.
 */
class PcmPlayer {

    private val queue = LinkedBlockingQueue<ByteArray>()
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    @Volatile private var active = false
    @Volatile private var playing = false
    @Volatile private var generation = 0

    /** Called on a background thread when queued audio has fully played out. */
    var onDrained: (() -> Unit)? = null

    /** Called on a background thread with 0..1 loudness of the next 20 ms slice. 0 when playback ends. */
    var onLevel: ((Float) -> Unit)? = null

    val isPlaying get() = playing || queue.isNotEmpty()

    fun start() {
        if (active) return
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, RATE / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        active = true
        worker = thread(name = "yesmin-pcm", isDaemon = true) {
            while (active) {
                val chunk = queue.poll(150, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (chunk == null) {
                    if (playing) {
                        playing = false
                        onLevel?.invoke(0f)
                        onDrained?.invoke()
                    }
                    continue
                }
                playing = true
                val gen = generation
                var offset = 0
                while (offset < chunk.size && active && gen == generation) {
                    val n = minOf(SLICE_BYTES, chunk.size - offset)
                    onLevel?.invoke(rms(chunk, offset, n))
                    runCatching { t.write(chunk, offset, n) }
                    offset += n
                }
            }
        }
    }

    fun enqueue(pcm: ByteArray) {
        if (active) queue.offer(pcm)
    }

    /** Interrupt: drop everything queued, stop the slice in progress, and flush the device buffer. */
    fun clear() {
        generation++
        queue.clear()
        runCatching {
            track?.pause()
            track?.flush()
            track?.play()
        }
        playing = false
        onLevel?.invoke(0f)
    }

    fun stop() {
        active = false
        generation++
        queue.clear()
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        worker = null
        playing = false
        onLevel?.invoke(0f)
    }

    private fun rms(b: ByteArray, offset: Int, length: Int): Float {
        var sum = 0.0
        var i = offset
        val end = offset + length - 1
        var samples = 0
        while (i < end) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        return (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val RATE = 24000
        private const val SLICE_BYTES = RATE / 50 * 2 // 20 ms of 16-bit mono
    }
}
