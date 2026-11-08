package com.yesmin.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.data.db.MemoryEntity
import com.yesmin.ai.ui.components.InfoCard
import com.yesmin.ai.ui.components.ScreenScaffold
import com.yesmin.ai.yesmin
import java.text.DateFormat
import java.util.Date

@Composable
fun MemoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val list by vm.memories.collectAsState()
    var newText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(ctx.yesmin.settings.memoryEnabled) }

    ScreenScaffold("Memory", onBack) {
        InfoCard(
            "You're in control",
            "Memories live only on this phone, separate from your API key. Yesmin never saves anything without " +
                "asking you first. Please don't store passwords, card numbers or ID numbers here."
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = enabled, onCheckedChange = { enabled = it; ctx.yesmin.settings.memoryEnabled = it })
            Text("Use memory in conversations")
        }

        OutlinedTextField(
            value = newText, onValueChange = { newText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Add a memory (e.g. I like jazz)") }
        )
        Button(
            onClick = { vm.addMemory("preference", newText); newText = "" },
            enabled = newText.isNotBlank(),
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text("Save memory") }

        if (list.isEmpty()) {
            Text("Nothing saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        list.forEach { m ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        m.type.uppercase() + " · " + DateFormat.getDateInstance().format(Date(m.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(m.text)
                    Row {
                        TextButton(onClick = { editing = m; editText = m.text }) { Text("Edit") }
                        TextButton(onClick = { vm.deleteMemory(m.id) }) { Text("Delete") }
                    }
                }
            }
        }
        if (list.isNotEmpty()) {
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Clear all memory")
            }
        }
    }

    editing?.let { m ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit memory") },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it }) },
            confirmButton = {
                TextButton(onClick = { vm.updateMemory(m, editText); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all memory?") },
            text = { Text("This permanently deletes every saved memory on this phone.") },
            confirmButton = {
                TextButton(onClick = { vm.clearMemories(); confirmClear = false }) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
