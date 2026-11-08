package com.yesmin.ai.automation

import android.content.Context

/**
 * @param message what Yesmin shows/says. Written from what Android actually reported.
 * @param data    optional extra text (screen contents, notifications) the AI can summarize.
 */
class RouterResult(val message: String, val success: Boolean, val data: String? = null)

/**
 * Keyword-based command parser for English / Hinglish. Returns null when the text is not
 * a phone command, so it can go to Gemini as normal chat.
 */
class CommandRouter(private val context: Context) {

    private val actions = Actions(context)

    private fun r(a: ActionResult) = RouterResult(a.message, a !is ActionResult.Failed)

    fun handle(raw: String): RouterResult? {
        val t = raw.trim()
        val s = t.lowercase()

        // --- Accessibility / screen (only if the user enabled the service) ---
        when {
            s in setOf("read screen", "read the screen", "screen padho", "what's on my screen", "whats on my screen") -> {
                val svc = YesminAccessibilityService.instance
                    ?: return RouterResult("Screen access is off. Turn it on in Phone Automation first.", false)
                val text = svc.readScreen()
                return if (text.isBlank()) RouterResult("I couldn't read any text on this screen.", false)
                else RouterResult("Here's what I can see on screen.", true, text)
            }
            s == "scroll down" || s == "neeche scroll karo" -> return gesture { it.scrollDown() }
            s == "scroll up" || s == "upar scroll karo" -> return gesture { it.scrollUp() }
            s == "go back" || s == "back" || s == "peeche jao" -> return gesture { it.back() }
            s == "go home" || s == "home screen" -> return gesture { it.home() }
            s.startsWith("tap ") || s.startsWith("click ") -> {
                val label = t.substringAfter(' ').trim()
                val svc = YesminAccessibilityService.instance
                    ?: return RouterResult("Screen control is off. Turn it on in Phone Automation first.", false)
                return if (svc.clickByText(label)) RouterResult("Tapped \"$label\".", true)
                else RouterResult("I couldn't find a tappable \"$label\" on this screen.", false)
            }
            s.contains("read notifications") || s.contains("notifications padho") -> {
                val l = YesminNotificationListener.instance
                    ?: return RouterResult("Notification access is off. Grant it in Phone Automation first.", false)
                val list = l.snapshot()
                return if (list.isEmpty()) RouterResult("You have no notifications right now.", true)
                else RouterResult("Here are your latest notifications.", true, list.joinToString("\n"))
            }
        }

        // --- Simple intents ---
        Regex("""^(?:play|chalao|bajao|gaana)\s+(.+?)(?:\s+(?:on|pe|par)\s+youtube)?$""").find(s)?.let {
            if (s.contains("youtube") || s.startsWith("play ")) return r(actions.youtubeSearch(it.groupValues[1]))
        }
        Regex("""^youtube\s+(?:pe|par|on)?\s*(.+)$""").find(s)?.let { return r(actions.youtubeSearch(it.groupValues[1])) }

        Regex("""^(?:set\s+)?alarm(?:\s+(?:for|at))?\s+(\d{1,2})(?:[:.\s](\d{2}))?\s*(am|pm)?""").find(s)?.let {
            var h = it.groupValues[1].toInt()
            val m = it.groupValues[2].ifEmpty { "0" }.toInt()
            when (it.groupValues[3]) {
                "pm" -> if (h < 12) h += 12
                "am" -> if (h == 12) h = 0
            }
            if (h !in 0..23 || m !in 0..59) return RouterResult("That time doesn't look valid.", false)
            return r(actions.setAlarm(h, m, "Yesmin alarm"))
        }

        Regex("""^(?:set\s+)?(?:a\s+)?timer(?:\s+for)?\s+(\d+)\s*(second|sec|minute|min|hour|hr)""").find(s)?.let {
            val n = it.groupValues[1].toInt()
            val secs = when (it.groupValues[2]) {
                "second", "sec" -> n
                "minute", "min" -> n * 60
                else -> n * 3600
            }
            return r(actions.setTimer(secs, "Yesmin timer"))
        }

        Regex("""^(?:call|dial)\s+(\+?[\d\s-]{5,})$""").find(s)?.let { return r(actions.dial(it.groupValues[1].trim())) }

        Regex("""^(?:navigate to|directions to|map|maps)\s+(.+)$""").find(s)?.let { return r(actions.openMaps(it.groupValues[1])) }
        Regex("""^(?:search|google)\s+(.+)$""").find(s)?.let { return r(actions.webSearch(it.groupValues[1])) }

        if (s in setOf("open camera", "camera kholo")) return r(actions.openCamera())
        if (s in setOf("open settings", "settings kholo")) return r(actions.openSettings())
        if (s in setOf("open wifi settings", "wifi settings")) return r(actions.openWifiSettings())
        if (s in setOf("open bluetooth settings", "bluetooth settings")) return r(actions.openBluetoothSettings())

        // Only short phrases count as commands, so normal sentences like "open to ideas" go to chat.
        val shortPhrase = s.split(Regex("\\s+")).size <= 4
        if (shortPhrase) {
            Regex("""^(?:open|launch|start)\s+(.+)$""").find(s)?.let { return r(actions.openApp(it.groupValues[1])) }
            Regex("""^(.+?)\s+(?:kholo|khol do|chalu karo)$""").find(s)?.let { return r(actions.openApp(it.groupValues[1])) }
        }

        return null
    }

    private fun gesture(block: (YesminAccessibilityService) -> Boolean): RouterResult {
        val svc = YesminAccessibilityService.instance
            ?: return RouterResult("Screen control is off. Turn it on in Phone Automation first.", false)
        // Android only confirms the gesture was dispatched, not that the app reacted.
        return if (block(svc)) RouterResult("Done (sent to the screen).", true)
        else RouterResult("Android didn't accept that action.", false)
    }
}
