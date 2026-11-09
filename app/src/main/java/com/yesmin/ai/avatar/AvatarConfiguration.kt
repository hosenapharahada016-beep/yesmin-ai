package com.yesmin.ai.avatar

import android.app.ActivityManager
import android.content.Context

/**
 * Performance and behavior settings. Chosen once from the device's capabilities.
 * Changing the quality setting takes effect the next time the app starts.
 */
data class AvatarConfiguration(
    val profile: String,
    val targetFps: Int,
    val maxModelBytes: Long,
    val proceduralMotion: Boolean,
    val motionScale: Float,
    val antiAliasing: Boolean,
    val mouthLatencyMs: Int,
    val mouthGain: Float = 0.9f
) {
    companion object {
        /** Put the GLB here to ship it inside the APK: app/src/main/assets/avatar/avatar.glb */
        const val ASSET_DIR = "avatar"
        const val MODEL_FILE = "avatar.glb"
        const val ASSET_MODEL_PATH = "$ASSET_DIR/$MODEL_FILE"

        /** A model imported from Settings lives here (inside the app's private storage) and wins over the bundled one. */
        const val CUSTOM_MODEL_PATH = "avatar/avatar.glb"

        private const val MB = 1024L * 1024L

        /**
         * @param quality "auto", "low" or "high"
         * @param headMotion false disables procedural head and body motion
         */
        fun detect(context: Context, quality: String = "auto", headMotion: Boolean = true): AvatarConfiguration {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val cores = Runtime.getRuntime().availableProcessors()
            val weak = am.isLowRamDevice || am.memoryClass < 192 || cores <= 4
            val strong = !weak && cores >= 8 && am.memoryClass >= 256
            val tier = when (quality) {
                "low" -> "low"
                "high" -> "high"
                else -> if (weak) "low" else if (strong) "high" else "normal"
            }
            return when (tier) {
                "low" -> AvatarConfiguration("low", 24, 40 * MB, headMotion, 0.5f, false, 140)
                "high" -> AvatarConfiguration("high", 45, 120 * MB, headMotion, 1f, true, 100)
                else -> AvatarConfiguration("normal", 30, 80 * MB, headMotion, 1f, true, 120)
            }
        }
    }
}
