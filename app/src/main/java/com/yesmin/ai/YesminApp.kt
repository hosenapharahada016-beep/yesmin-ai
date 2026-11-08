package com.yesmin.ai

import android.app.Application
import android.content.Context
import com.yesmin.ai.ai.GeminiClient
import com.yesmin.ai.data.SecureKeyStore
import com.yesmin.ai.data.SettingsStore
import com.yesmin.ai.data.db.AppDb

/** Holds app-wide singletons. Keys and chat/memory data live in separate stores. */
class YesminApp : Application() {
    val keys by lazy { SecureKeyStore(this) }
    val settings by lazy { SettingsStore(this) }
    val db by lazy { AppDb.create(this) }
    val gemini by lazy { GeminiClient(keys) }
}

val Context.yesmin: YesminApp get() = applicationContext as YesminApp
