package com.yesmin.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.automation.Actions
import com.yesmin.ai.automation.YesminAccessibilityService
import com.yesmin.ai.automation.YesminNotificationListener
import com.yesmin.ai.ui.components.InfoCard
import com.yesmin.ai.ui.components.ScreenScaffold
import com.yesmin.ai.ui.components.StatusRow

@Composable
fun AutomationScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val actions = remember { Actions(ctx) }
    val result by vm.commandResult.collectAsState()
    var cmd by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    // Re-check service status when returning from Android settings.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val a11yOn = tick >= 0 && YesminAccessibilityService.isEnabled
    val notifOn = tick >= 0 && YesminNotificationListener.isConnected

    // Calendar form
    var evTitle by remember { mutableStateOf("") }
    var evMinutes by remember { mutableStateOf("60") }

    ScreenScaffold("Phone Automation", onBack) {
        InfoCard(
            "How this works",
            "Yesmin asks Android to do things. Android decides whether it happens. " +
                "Calls, messages, alarms and events open the real app so YOU confirm. " +
                "I only say \"done\" for what Android reports back."
        )

        Text("Type a command", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = cmd, onValueChange = { cmd = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. open whatsapp") }, maxLines = 2
        )
        Button(
            onClick = { vm.runCommand(cmd) },
            enabled = cmd.isNotBlank(),
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text("Run") }

        result?.let { r ->
            InfoCard(if (r.success) "Result" else "Couldn't do that", r.message + (r.data?.let { "\n\n$it" } ?: ""))
        }

        Text("Try", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "open camera", "open settings", "open youtube", "play arijit singh on youtube",
                "alarm 6:45 am", "timer 5 minutes", "navigate to railway station",
                "call 112", "search weather today"
            ).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c -> AssistChip(onClick = { cmd = c }, label = { Text(c) }) }
                }
            }
        }

        Text("Calendar event", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = evTitle, onValueChange = { evTitle = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true
        )
        OutlinedTextField(
            value = evMinutes, onValueChange = { evMinutes = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Starts in (minutes)") }, singleLine = true
        )
        Button(
            onClick = {
                val start = System.currentTimeMillis() + (evMinutes.toLongOrNull() ?: 60L) * 60_000L
                val r = actions.addCalendarEvent(evTitle, start, start + 60 * 60_000L)
                vm.commandResult.value = com.yesmin.ai.automation.RouterResult(
                    r.message, r !is com.yesmin.ai.automation.ActionResult.Failed
                )
            },
            enabled = evTitle.isNotBlank(),
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text("Create in calendar app") }

        Text("Optional access (off by default)", style = MaterialTheme.typography.titleMedium)
        StatusRow(
            "Screen control (Accessibility)", a11yOn,
            "Open settings"
        ) { actions.openAccessibilitySettings() }
        Text(
            "Lets Yesmin read screen text and do taps, scrolls and back/home when you type or say a command like " +
                "\"read screen\", \"scroll down\" or \"tap Search\". Android will show a warning when you enable it. " +
                "Password fields are never read. Turn it off any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusRow(
            "Notification access", notifOn,
            "Open settings"
        ) { actions.openNotificationAccessSettings() }
        Text(
            "Lets Yesmin read your current notifications when you say \"read notifications\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        InfoCard(
            "Not included in this version",
            "Screen capture (screenshot understanding), a floating overlay button and an always-on wake word are not built yet."
        )
    }
}
