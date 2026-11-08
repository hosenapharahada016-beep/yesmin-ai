package com.yesmin.ai.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yesmin.ai.ChatMsg
import com.yesmin.ai.ChatState

private sealed class Seg {
    class Text(val s: String) : Seg()
    class Code(val lang: String, val s: String) : Seg()
}

private val fence = Regex("```([A-Za-z0-9+#_-]*)\\n([\\s\\S]*?)```")

private fun split(text: String): List<Seg> {
    val out = mutableListOf<Seg>()
    var last = 0
    for (m in fence.findAll(text)) {
        if (m.range.first > last) out += Seg.Text(text.substring(last, m.range.first).trim())
        out += Seg.Code(m.groupValues[1], m.groupValues[2].trimEnd())
        last = m.range.last + 1
    }
    if (last < text.length) out += Seg.Text(text.substring(last).trim())
    return out.filter { !(it is Seg.Text && it.s.isEmpty()) }
}

@Composable
private fun Bubble(msg: ChatMsg, onSaveCode: ((String) -> Unit)?) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val bg = when {
        msg.note -> cs.surface
        msg.fromUser -> cs.primary
        else -> cs.surfaceVariant
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bg)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (msg.note) Text("📱 Phone", style = MaterialTheme.typography.labelSmall, color = cs.secondary)
            for (seg in split(msg.text)) when (seg) {
                is Seg.Text -> Text(seg.s, color = if (msg.fromUser) Color.White else cs.onSurface, fontSize = 16.sp)
                is Seg.Code -> Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0714))
                        .padding(8.dp)
                ) {
                    Text(seg.lang.ifBlank { "code" }, color = Color(0xFF22D3EE), fontSize = 12.sp)
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        Text(seg.s, color = Color(0xFFEDE9FE), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                    Row {
                        TextButton(onClick = {
                            clip.setText(AnnotatedString(seg.s))
                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                        if (onSaveCode != null) TextButton(onClick = { onSaveCode(seg.s) }) { Text("Save to file…") }
                    }
                }
            }
        }
    }
}

/**
 * Shared chat UI for AI Chat and Coding Mode.
 * Saving code goes through the system "create document" picker, so the app only writes
 * where the user explicitly chooses.
 */
@Composable
fun ChatPanel(
    state: ChatState,
    placeholder: String,
    suggestions: List<String>,
    enableSave: Boolean,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    headerContent: @Composable ColumnScope.() -> Unit = {},
    micEnabled: Boolean = true
) {
    val messages by state.messages.collectAsState()
    val busy by state.busy.collectAsState()
    val error by state.error.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val ctx = LocalContext.current

    var codeToSave by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val code = codeToSave
        if (uri != null && code != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(code.toByteArray()) } != null
            }.getOrDefault(false)
            Toast.makeText(ctx, if (ok) "Saved" else "Could not save the file", Toast.LENGTH_SHORT).show()
        }
        codeToSave = null
    }
    val listen = rememberSpeechInput { input = if (input.isBlank()) it else "$input $it" }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        headerContent()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { m ->
                Bubble(m, if (enableSave && !m.fromUser) ({ code ->
                    codeToSave = code
                    saver.launch("yesmin_code.txt")
                }) else null)
            }
            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                    Text("Yesmin is thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (suggestions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s -> AssistChip(onClick = { input = s }, label = { Text(s) }) }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            maxLines = 6
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { val t = input; input = ""; onSend(t) },
                enabled = input.isNotBlank() && !busy,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Send") }
            if (micEnabled) TextButton(onClick = listen, modifier = Modifier.heightIn(min = 48.dp)) { Text("🎤 Speak") }
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear chat") }
        }
    }
}
