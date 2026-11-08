package com.yesmin.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.ui.components.ChatPanel
import com.yesmin.ai.ui.components.ScreenScaffold

@Composable
fun ChatScreen(vm: MainViewModel, onBack: () -> Unit) {
    val speak by vm.speakReplies.collectAsState()
    var web by remember { mutableStateOf(false) }

    ScreenScaffold("AI Chat", onBack) {
        ChatPanel(
            state = vm.chat,
            placeholder = "Talk to Yesmin (English, Hindi or Hinglish)…",
            suggestions = listOf(
                "Good morning Yesmin 💜",
                "Plan my day",
                "Open YouTube",
                "Play lofi songs on youtube",
                "Set alarm 7:30 am"
            ),
            enableSave = false,
            onSend = { vm.send(vm.chat, it, coding = false, useSearch = web) },
            onClear = { vm.clearChat(vm.chat) },
            headerContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(checked = speak, onCheckedChange = {
                        vm.speakReplies.value = it
                        if (!it) vm.speaker.stop()
                    })
                    Text("Speak replies")
                    Switch(checked = web, onCheckedChange = { web = it })
                    Text("Web search")
                }
            }
        )
    }
}
