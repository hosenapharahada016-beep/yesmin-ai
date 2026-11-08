package com.yesmin.ai.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Plays 24 kHz mono PCM16 (the Live API output format) from a queue.
 * clear() drops queued audio instantly, which is what barge-in needs.
 */
class PcmPlayer {

    private val queue = LinkedBlockingQueue<ByteArray>()
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    @Volatile private var active = false
    @Volatile private var playing = false

    /** Called on a background thread when queued audio has fully played out. */
    var onDrained: (() -> Unit)? = null

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
                        onDrained?.invoke()
                    }
                    continue
                }
                playing = true
                runCatching { t.write(chunk, 0, chunk.size) }
            }
        }
    }

    fun enqueue(pcm: ByteArray) {
        if (active) queue.offer(pcm)
    }

    /** Interrupt: drop everything queued and flush the device buffer. */
    fun clear() {
        queue.clear()
        runCatching {
            track?.pause()
            track?.flush()
            track?.play()
        }
        playing = false
    }

    fun stop() {
        active = false
        queue.clear()
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        worker = null
        playing = false
    }

    companion object {
        const val RATE = 24000
    }
}
