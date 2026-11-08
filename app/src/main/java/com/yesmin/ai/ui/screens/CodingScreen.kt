package com.yesmin.ai.ui.screens

import androidx.compose.runtime.Composable
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.ui.components.ChatPanel
import com.yesmin.ai.ui.components.InfoCard
import com.yesmin.ai.ui.components.ScreenScaffold

@Composable
fun CodingScreen(vm: MainViewModel, onBack: () -> Unit) {
    ScreenScaffold("Coding Assistant", onBack) {
        InfoCard(
            "Honest note",
            "I write code, but I can't compile or run it here. Treat all code as untested until you build it yourself. " +
                "Use \"Save to file…\" on a code block to write it where you choose."
        )
        ChatPanel(
            state = vm.coding,
            placeholder = "Describe the code you need, or paste code / an error…",
            suggestions = listOf(
                "Explain this code simply:\n",
                "Fix this build error:\n",
                "Create a Kotlin Android screen that ",
                "Make a simple HTML/CSS/JS page that ",
                "How do I build an APK in Termux?"
            ),
            enableSave = true,
            onSend = { vm.send(vm.coding, it, coding = true) },
            onClear = { vm.clearChat(vm.coding) },
            micEnabled = false
        )
    }
}
