package com.yesmin.ai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yesmin.ai.BuildConfig
import com.yesmin.ai.ui.components.Avatar
import com.yesmin.ai.ui.components.InfoCard
import com.yesmin.ai.ui.components.ScreenScaffold

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold("About Yesmin AI", onBack) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(130.dp)
            Text("Yesmin AI  v${BuildConfig.VERSION_NAME}")
        }
        InfoCard(
            "I'm an AI",
            "Yesmin is an AI companion powered by Google's Gemini. She is not a human and has no real feelings or life, " +
                "even though she is warm and friendly. She can make mistakes, so double-check anything important."
        )
        InfoCard(
            "Works in this version",
            "• Chat in English, Hindi and Hinglish, with optional spoken replies\n" +
                "• Live voice call over the Gemini Live API (needs a key with Live access)\n" +
                "• Memory with your approval, edit and delete\n" +
                "• Phone commands through Android intents (apps, YouTube search, alarms, timers, calls, messages, maps, calendar drafts)\n" +
                "• Optional accessibility control and notification reading\n" +
                "• Coding mode with copy and save-to-file\n" +
                "• Tools: web search, translate, study help, daily plan, photo understanding, OCR, PDF/text summary, voice notes"
        )
        InfoCard(
            "Needs setup or has limits",
            "• Live calls need a Live-capable model and key; otherwise use AI Chat with 🎤 and spoken replies\n" +
                "• YouTube opens search results; you tap the video\n" +
                "• OCR reads Latin script only\n" +
                "• Accessibility and notification features need you to enable them in Android settings"
        )
        InfoCard(
            "Not built yet",
            "Screen capture, floating assistant button, custom wake word, editing existing project files, scheduled reminders inside the app."
        )
    }
}
