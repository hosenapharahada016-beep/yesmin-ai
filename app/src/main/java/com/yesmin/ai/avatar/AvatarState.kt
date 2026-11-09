package com.yesmin.ai.avatar

/**
 * The restricted expression vocabulary. Anything an AI sends that is not in this list is
 * treated as IDLE. This file has no Android or Gemini dependencies.
 */
enum class AvatarExpression {
    IDLE, HAPPY, LAUGHING, FRIENDLY, THINKING, LISTENING, SPEAKING,
    SURPRISED, SAD, CONFUSED, EXCITED, SERIOUS, SLEEPY;

    companion object {
        /** Moods an AI may choose. LISTENING and SPEAKING come from the voice session, not the AI. */
        val aiChoosable: List<AvatarExpression> = listOf(
            IDLE, HAPPY, LAUGHING, FRIENDLY, THINKING, SURPRISED, SAD, CONFUSED, EXCITED, SERIOUS, SLEEPY
        )

        /** Strict parse used for any value from outside. Unknown or null becomes IDLE. */
        fun parse(raw: String?): AvatarExpression {
            val key = raw?.trim()?.uppercase()?.replace(' ', '_')?.replace('-', '_') ?: return IDLE
            return values().firstOrNull { it.name == key } ?: IDLE
        }

        /** Same as [parse] but only accepts the moods an AI is allowed to choose. */
        fun parseFromAi(raw: String?): AvatarExpression {
            val e = parse(raw)
            return if (e in aiChoosable) e else IDLE
        }
    }
}

/** What the conversation is doing right now. Set by whoever owns the voice session. */
enum class ConversationPhase { IDLE, LISTENING, THINKING, SPEAKING }

/** Gesture categories. A gesture is only played if the loaded model has a matching clip. */
enum class Gesture(val aliases: List<String>) {
    IDLE(listOf("idle", "breath", "stand")),
    WAVE(listOf("wave", "greet", "hello")),
    NOD(listOf("nod")),
    HEAD_TILT(listOf("tilt")),
    HAPPY(listOf("happy", "joy", "cheer")),
    LAUGH(listOf("laugh")),
    THINKING(listOf("think", "ponder")),
    LISTENING(listOf("listen")),
    SPEAKING(listOf("talk", "speak"))
}

/** Result of trying to put a model on screen. */
sealed class ModelStatus {
    object NotLoaded : ModelStatus()
    object Loading : ModelStatus()

    /** No usable model (usually: none installed). The UI shows a clearly labelled placeholder. */
    data class Placeholder(val reason: String) : ModelStatus()

    data class Ready(val info: ModelInfo) : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

/** What was actually found in the loaded model. Nothing here is assumed. */
data class ModelInfo(
    val source: String,
    val sizeBytes: Long,
    val animationNames: List<String>,
    val morphTargetCount: Int,
    val matchedFaceShapes: Set<FaceShape>,
    val gesturesAvailable: Set<Gesture>,
    val headBoneFound: Boolean,
    val hasSkin: Boolean,
    val warnings: List<String>
) {
    val canBlink get() = FaceShape.BLINK in matchedFaceShapes
    val canMoveMouth get() = FaceShape.MOUTH_OPEN in matchedFaceShapes
    val canShowExpressions get() = matchedFaceShapes.any { it != FaceShape.BLINK && it != FaceShape.MOUTH_OPEN }

    /** Human-readable capability report, shown in Settings. */
    fun describe(): String = buildString {
        appendLine("Source: $source (${sizeBytes / 1024} KB)")
        appendLine("Morph targets: $morphTargetCount, matched face shapes: " +
            (if (matchedFaceShapes.isEmpty()) "none" else matchedFaceShapes.joinToString { it.name.lowercase() }))
        appendLine("Facial expressions: " + if (canShowExpressions) "yes (limited to matched shapes)" else "NOT available (model has no matching face morph targets)")
        appendLine("Blinking: ${if (canBlink) "yes" else "no"}   Mouth movement: ${if (canMoveMouth) "yes" else "no"}")
        appendLine("Head motion: " + if (headBoneFound) "head bone found" else "no head bone, whole-model sway only")
        appendLine("Animation clips: " + if (animationNames.isEmpty()) "none" else animationNames.joinToString())
        appendLine("Gestures available: " + if (gesturesAvailable.isEmpty()) "none" else gesturesAvailable.joinToString { it.name.lowercase() })
        warnings.forEach { appendLine("Warning: $it") }
    }.trim()
}
