package com.yesmin.ai.data

import android.content.Context

/** Non-secret preferences. The API key lives in [SecureKeyStore], never here. */
class SettingsStore(context: Context) {

    private val p = context.getSharedPreferences("yesmin_settings", Context.MODE_PRIVATE)

    /** Text model. Editable in Settings; the list comes from the Gemini models.list endpoint. */
    var chatModel: String
        get() = p.getString("chat_model", DEFAULT_CHAT_MODEL) ?: DEFAULT_CHAT_MODEL
        set(v) = p.edit().putString("chat_model", v.trim()).apply()

    /** Live (voice) model. Must support bidiGenerateContent. Editable. */
    var liveModel: String
        get() = p.getString("live_model", DEFAULT_LIVE_MODEL) ?: DEFAULT_LIVE_MODEL
        set(v) = p.edit().putString("live_model", v.trim()).apply()

    var temperature: Float
        get() = p.getFloat("temperature", 0.9f)
        set(v) = p.edit().putFloat("temperature", v.coerceIn(0f, 2f)).apply()

    /** Extra personality instructions added by the user. */
    var personaExtra: String
        get() = p.getString("persona_extra", "") ?: ""
        set(v) = p.edit().putString("persona_extra", v).apply()

    var userName: String
        get() = p.getString("user_name", "") ?: ""
        set(v) = p.edit().putString("user_name", v.trim()).apply()

    /** Gemini prebuilt voice name used in Live mode. */
    var voiceName: String
        get() = p.getString("voice_name", "Aoede") ?: "Aoede"
        set(v) = p.edit().putString("voice_name", v.trim()).apply()

    /** "system", "dark" or "light". */
    var theme: String
        get() = p.getString("theme", "dark") ?: "dark"
        set(v) = p.edit().putString("theme", v).apply()

    /** When true, Yesmin may ask to save facts to memory (always with your confirmation). */
    var memoryEnabled: Boolean
        get() = p.getBoolean("memory_enabled", true)
        set(v) = p.edit().putBoolean("memory_enabled", v).apply()

    /** The name shown on the home screen and used by the AI to refer to itself. */
    var assistantName: String
        get() = p.getString("assistant_name", "Yesmin")?.ifBlank { "Yesmin" } ?: "Yesmin"
        set(v) = p.edit().putString("assistant_name", v.trim().take(24).ifBlank { "Yesmin" }).apply()

    /** false = never start the 3D engine; the home screen shows the placeholder. */
    var avatarEnabled: Boolean
        get() = p.getBoolean("avatar_enabled", true)
        set(v) = p.edit().putBoolean("avatar_enabled", v).apply()

    /** "auto", "low" or "high". Applied the next time the app starts. */
    var avatarQuality: String
        get() = p.getString("avatar_quality", "auto") ?: "auto"
        set(v) = p.edit().putString("avatar_quality", v).apply()

    /** Gentle head / body sway. Turn off if a model's head moves in a strange way. */
    var avatarHeadMotion: Boolean
        get() = p.getBoolean("avatar_head_motion", true)
        set(v) = p.edit().putBoolean("avatar_head_motion", v).apply()

    /** Let the Live model choose expressions through a function call. Falls back automatically if unsupported. */
    var avatarAiExpressions: Boolean
        get() = p.getBoolean("avatar_ai_expressions", true)
        set(v) = p.edit().putBoolean("avatar_ai_expressions", v).apply()

    companion object {
        // Defaults are only starting values. Use "Load models" in Settings to see what your key supports.
        // Checked against Google's docs on 2026-11-08: gemini-3.8-flash is listed as stable (text/chat),
        // and gemini-3.8-live is the model name used in Google's Live API WebSocket guide.
        const val DEFAULT_CHAT_MODEL = "gemini-3.8-flash"
        const val DEFAULT_LIVE_MODEL = "gemini-3.8-live"
    }
}
