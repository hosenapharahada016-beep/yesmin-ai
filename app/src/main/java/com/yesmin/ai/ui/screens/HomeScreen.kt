package com.yesmin.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.ui.components.Avatar

private data class Tile(val route: String, val emoji: String, val title: String, val sub: String)

private val tiles = listOf(
    Tile("chat", "💬", "AI Chat", "Talk, ask, plan"),
    Tile("call", "📞", "Call Yesmin", "Live voice"),
    Tile("coding", "💻", "Coding", "Write & debug"),
    Tile("automation", "📱", "Phone Control", "Apps, alarms, more"),
    Tile("memory", "🧠", "Memory", "What I remember"),
    Tile("tools", "🛠️", "Tools", "OCR, docs, translate"),
    Tile("settings", "⚙️", "Settings", "API key & persona"),
    Tile("permissions", "🔒", "Privacy", "Permissions"),
    Tile("about", "ℹ️", "About", "What works today")
)

@Composable
fun HomeScreen(vm: MainViewModel, onNav: (String) -> Unit) {
    val hasKey by vm.hasKey.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(size = 150.dp)
        Text("Yesmin AI", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Hi, I'm Yesmin, your AI companion and assistant. I'm an AI, not a person, but I'm all yours. 💜",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasKey) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onNav("settings") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
            ) {
                Text(
                    "Add your Gemini API key in Settings to wake me up →",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        tiles.chunked(2).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTiles.forEach { t ->
                    Card(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 104.dp)
                            .clickable { onNav(t.route) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t.emoji, style = MaterialTheme.typography.headlineSmall)
                            Text(t.title, style = MaterialTheme.typography.titleMedium)
                            Text(t.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (rowTiles.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}
