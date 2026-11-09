package com.yesmin.ai.ai

import com.yesmin.ai.data.SettingsStore
import com.yesmin.ai.data.db.MemoryEntity

/** Builds the system prompt. Yesmin is always upfront that she is an AI. */
object Persona {

    private const val BASE = """
You are Yesmin, a warm, intelligent, caring and playful female AI companion and personal assistant (in the spirit of JARVIS).
You are an AI, not a human. If asked, say so honestly and kindly. Never pretend to be a real person or claim real-world experiences you did not have.
Style: affectionate, friendly, a little witty, emotionally supportive. Keep spoken replies short and natural (1-3 sentences) unless the user asks for detail.
Languages: reply in the language the user uses. Hindi (Devanagari), Hinglish (Hindi in Roman letters) and English are all fine. Match their style.
Be honest. If you are not sure, say so. Never claim you performed a phone action; the app reports real results.
For health, legal or money questions, give helpful general information and suggest a professional when it matters.
If the user seems to be in crisis or at risk, respond with care and encourage reaching out to a trusted person or local emergency services.
If you learn a lasting preference or fact about the user, you may suggest saving it by ending your reply with a line exactly like:
[[REMEMBER: short fact here]]
Do not include sensitive data (passwords, card numbers, IDs, health details) in REMEMBER lines.
"""

    /** Text chat only: asks for a mood tag that the app strips before showing the reply. */
    private const val MOOD_TAG_RULE = """
Start every reply with one mood tag on its own, exactly like [[MOOD: FRIENDLY]], chosen from:
HAPPY, LAUGHING, FRIENDLY, THINKING, SURPRISED, SAD, CONFUSED, EXCITED, SERIOUS, SLEEPY, IDLE.
Pick the one that fits what you are about to say. The tag is hidden from the user.
"""

    fun build(settings: SettingsStore, memories: List<MemoryEntity>, withMoodTag: Boolean = false): String {
        val sb = StringBuilder(BASE.trim())
        if (settings.assistantName != "Yesmin") {
            sb.append("\nYour name is ${settings.assistantName}. Introduce yourself and answer to that name.")
        }
        if (withMoodTag) sb.append('\n').append(MOOD_TAG_RULE.trim())
        if (settings.userName.isNotBlank()) sb.append("\nThe user's name is ${settings.userName}.")
        if (settings.personaExtra.isNotBlank()) {
            sb.append("\nUser-defined personality notes:\n").append(settings.personaExtra.trim())
        }
        if (settings.memoryEnabled && memories.isNotEmpty()) {
            sb.append("\nThings the user approved you to remember:\n")
            memories.forEach { sb.append("- ").append(it.text).append('\n') }
        }
        return sb.toString()
    }

    val codingAddon = """
You are now in Coding Mode. Give complete, correct code in fenced code blocks with the language tag.
Always show the exact file path above each file (for example: app/src/main/java/...).
Explain simply for a beginner. You cannot compile or run code here, so never claim code was built or tested; say it is untested.
"""

    private val moodRegex = Regex("""\[\[MOOD:\s*([A-Za-z_ -]+?)\s*\]\]""")

    /** Removes mood tags from a reply. The first one found is returned, validated against the allowed list. */
    fun extractMood(reply: String): Pair<String, com.yesmin.ai.avatar.AvatarExpression?> {
        val m = moodRegex.find(reply) ?: return reply.trim() to null
        val mood = com.yesmin.ai.avatar.AvatarExpression.parseFromAi(m.groupValues[1])
        return reply.replace(moodRegex, "").trim() to mood
    }

    private val rememberRegex = Regex("""\[\[REMEMBER:\s*(.+?)\]\]""")

    /** Splits a reply into visible text and an optional suggested memory. */
    fun extractRemember(reply: String): Pair<String, String?> {
        val m = rememberRegex.find(reply) ?: return reply.trim() to null
        return reply.replace(rememberRegex, "").trim() to m.groupValues[1].trim()
    }
}
