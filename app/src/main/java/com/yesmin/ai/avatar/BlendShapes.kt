package com.yesmin.ai.avatar

/**
 * The small set of face controls Yesmin drives. Each one is looked up by name in the model's
 * morph targets (blend shapes). If the model has no matching name, that control simply does nothing.
 */
enum class FaceShape(val aliases: List<String>) {
    BLINK(listOf("eyeblinkleft", "eyeblinkright", "blink", "blinkleft", "blinkright", "eyesclosed")),
    SMILE(listOf("mouthsmileleft", "mouthsmileright", "mouthsmile", "smile", "joy", "fun", "happy")),
    EYE_WIDE(listOf("eyewideleft", "eyewideright", "eyewide")),
    SQUINT(listOf("eyesquintleft", "eyesquintright", "cheeksquintleft", "cheeksquintright")),
    BROW_UP(listOf("browinnerup", "browouterupleft", "browouterupright", "browup")),
    BROW_DOWN(listOf("browdownleft", "browdownright", "browdown", "angry")),
    FROWN(listOf("mouthfrownleft", "mouthfrownright", "mouthfrown", "sorrow", "sad")),
    MOUTH_OPEN(listOf("jawopen", "mouthopen", "a", "visemeaa", "aa"))
}

/** Matches a model's morph target names to [FaceShape]s. Names are compared ignoring case and punctuation. */
object BlendShapeResolver {

    fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** @return for each matched shape, the indices of the morph targets that implement it. */
    fun resolve(targetNames: List<String>): Map<FaceShape, IntArray> {
        val normalized = targetNames.map(::normalize)
        val result = LinkedHashMap<FaceShape, IntArray>()
        for (shape in FaceShape.values()) {
            val wanted = shape.aliases.map(::normalize).toSet()
            val idx = normalized.withIndex().filter { it.value in wanted }.map { it.index }
            if (idx.isNotEmpty()) result[shape] = idx.toIntArray()
        }
        return result
    }
}

/** Target face shape weights (0..1) and head pose (degrees) per expression. */
object ExpressionPresets {

    private val none = emptyMap<FaceShape, Float>()

    fun shapes(e: AvatarExpression): Map<FaceShape, Float> = when (e) {
        AvatarExpression.IDLE -> none
        AvatarExpression.HAPPY -> mapOf(FaceShape.SMILE to 0.7f, FaceShape.SQUINT to 0.15f, FaceShape.BROW_UP to 0.1f)
        AvatarExpression.LAUGHING -> mapOf(FaceShape.SMILE to 1f, FaceShape.SQUINT to 0.45f, FaceShape.BROW_UP to 0.15f)
        AvatarExpression.FRIENDLY -> mapOf(FaceShape.SMILE to 0.45f, FaceShape.BROW_UP to 0.12f)
        AvatarExpression.THINKING -> mapOf(FaceShape.BROW_DOWN to 0.25f, FaceShape.SQUINT to 0.15f, FaceShape.FROWN to 0.1f)
        AvatarExpression.LISTENING -> mapOf(FaceShape.BROW_UP to 0.2f, FaceShape.EYE_WIDE to 0.12f, FaceShape.SMILE to 0.12f)
        AvatarExpression.SPEAKING -> mapOf(FaceShape.SMILE to 0.15f)
        AvatarExpression.SURPRISED -> mapOf(FaceShape.BROW_UP to 0.9f, FaceShape.EYE_WIDE to 0.8f, FaceShape.MOUTH_OPEN to 0.35f)
        AvatarExpression.SAD -> mapOf(FaceShape.FROWN to 0.6f, FaceShape.BROW_UP to 0.3f)
        AvatarExpression.CONFUSED -> mapOf(FaceShape.BROW_DOWN to 0.3f, FaceShape.BROW_UP to 0.25f, FaceShape.FROWN to 0.2f)
        AvatarExpression.EXCITED -> mapOf(FaceShape.SMILE to 0.9f, FaceShape.EYE_WIDE to 0.4f, FaceShape.BROW_UP to 0.4f, FaceShape.MOUTH_OPEN to 0.15f)
        AvatarExpression.SERIOUS -> mapOf(FaceShape.BROW_DOWN to 0.3f)
        AvatarExpression.SLEEPY -> mapOf(FaceShape.BLINK to 0.55f, FaceShape.SQUINT to 0.1f)
    }

    /** Resting head pose as (pitch, yaw, roll) in degrees. Pitch is positive when looking down. */
    fun headPose(e: AvatarExpression): Triple<Float, Float, Float> = when (e) {
        AvatarExpression.THINKING -> Triple(-3f, 6f, 6f)
        AvatarExpression.CONFUSED -> Triple(0f, 0f, -8f)
        AvatarExpression.LISTENING -> Triple(2f, 0f, 3f)
        AvatarExpression.SAD -> Triple(5f, 0f, 0f)
        AvatarExpression.SLEEPY -> Triple(7f, 0f, 0f)
        AvatarExpression.HAPPY -> Triple(-1f, 0f, 0f)
        AvatarExpression.EXCITED -> Triple(-2f, 0f, 0f)
        AvatarExpression.SURPRISED -> Triple(-3f, 0f, 0f)
        else -> Triple(0f, 0f, 0f)
    }
}
