package com.yesmin.ai.avatar

/**
 * Picks a mood from what was actually said, in English, Hinglish and Hindi.
 * Used as a backup when the AI does not choose an expression itself.
 * This only produces avatar expressions. It never triggers phone actions.
 */
object ExpressionInference {

    data class Reaction(val mood: AvatarExpression, val gesture: Gesture? = null, val holdMs: Long = 6_000L)

    private val i = RegexOption.IGNORE_CASE

    private val stop = Regex("""(stop (speaking|talking)|be quiet|shut up|chup( ho)?( jao)?|ruko|bas karo)|चुप|रुको""", i)
    private val greeting = Regex("""(^|\s)(hello|hi|hey|namaste|namaskar|salaam|good (morning|evening|afternoon))(\s|!|,|\.|$)|नमस्ते""", i)
    private val funny = Regex("""(funny|joke|hasao|mazaak|mazak)|मज़ाक|मजाक|चुटकुला""", i)
    private val thinkAsk = Regex("""(think (carefully|about|hard)|let me think|soch ke|sochke)|सोच""", i)
    private val sad = Regex("""(i am sad|i'm sad|feeling (sad|down|low)|depressed|upset|dukhi|udaas)|उदास|दुखी""", i)
    private val confused = Regex("""(i don't understand|i do not understand|confused|samajh nahi|what do you mean)|समझ नहीं""", i)
    private val surprised = Regex("""(^|\s)(wow|no way|seriously|really\?)""", i)
    private val excited = Regex("""(awesome|amazing|we won|congrat|great news)""", i)
    private val sleepy = Regex("""(sleepy|tired|good night|goodnight|neend)|नींद""", i)
    private val serious = Regex("""(urgent|emergency|important|serious)""", i)
    private val help = Regex("""(can you help|help me|need help|madad)|मदद""", i)

    private val aiLaugh = Regex("""(haha|hehe|lol)|😂|🤣|😄""", i)
    private val aiSad = Regex("""(sorry to hear|that sounds (hard|tough|difficult))""", i)

    fun isStopSpeaking(userText: String): Boolean = stop.containsMatchIn(userText)

    fun fromUser(text: String): Reaction? = when {
        greeting.containsMatchIn(text) -> Reaction(AvatarExpression.FRIENDLY, Gesture.WAVE)
        funny.containsMatchIn(text) -> Reaction(AvatarExpression.HAPPY)
        thinkAsk.containsMatchIn(text) -> Reaction(AvatarExpression.THINKING, holdMs = 4_000L)
        sad.containsMatchIn(text) -> Reaction(AvatarExpression.SAD, holdMs = 8_000L)
        confused.containsMatchIn(text) -> Reaction(AvatarExpression.CONFUSED)
        excited.containsMatchIn(text) -> Reaction(AvatarExpression.EXCITED, Gesture.HAPPY)
        surprised.containsMatchIn(text) -> Reaction(AvatarExpression.SURPRISED, holdMs = 4_000L)
        sleepy.containsMatchIn(text) -> Reaction(AvatarExpression.SLEEPY, holdMs = 8_000L)
        serious.containsMatchIn(text) -> Reaction(AvatarExpression.SERIOUS)
        help.containsMatchIn(text) -> Reaction(AvatarExpression.FRIENDLY)
        else -> null
    }

    fun fromAssistant(text: String): Reaction? = when {
        aiLaugh.containsMatchIn(text) -> Reaction(AvatarExpression.LAUGHING, Gesture.LAUGH, 3_500L)
        aiSad.containsMatchIn(text) -> Reaction(AvatarExpression.SAD, holdMs = 7_000L)
        else -> null
    }
}
