package com.yesmin.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads text replies aloud with Android's built-in text-to-speech (works offline for installed voices). */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
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
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
