package com.yesmin.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Reads text replies aloud with Android's built-in text-to-speech (works offline for installed voices). */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    /** Speech events, used to animate the avatar. Called on background threads. */
    interface Listener {
        fun onStart()
        /** A word boundary. Only some TTS engines report these. */
        fun onWord()
        fun onDone()
    }

    var listener: Listener? = null

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { listener?.onStart() }
            override fun onDone(utteranceId: String?) { listener?.onDone() }
            @Deprecated("Required by the base class")
            override fun onError(utteranceId: String?) { listener?.onDone() }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { listener?.onDone() }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) { listener?.onWord() }
        })
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!ready) return
        val clean = text
            .replace(Regex("```[\\s\\S]*?```"), " code omitted. ")
            .replace(Regex("[*_#`>]"), "")
            .trim()
        if (clean.isEmpty()) return
        val hasDevanagari = clean.any { it.code in 0x0900..0x097F }
        val locale = if (hasDevanagari) Locale("hi", "IN") else Locale.getDefault()
        val avail = engine.isLanguageAvailable(locale)
        if (avail >= TextToSpeech.LANG_AVAILABLE) engine.language = locale
        engine.speak(clean.take(3500), TextToSpeech.QUEUE_FLUSH, null, "yesmin")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        listener = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
