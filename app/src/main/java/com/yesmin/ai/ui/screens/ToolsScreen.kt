package com.yesmin.ai.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.ai.Attachment
import com.yesmin.ai.tools.DocTool
import com.yesmin.ai.tools.ImageTools
import com.yesmin.ai.ui.components.InfoCard
import com.yesmin.ai.ui.components.ScreenScaffold
import com.yesmin.ai.ui.components.rememberSpeechInput
import kotlinx.coroutines.launch

private enum class Tool(
    val label: String,
    val hint: String,
    val needsPhoto: Boolean = false,
    val needsFile: Boolean = false,
    val search: Boolean = false,
    val mic: Boolean = false,
    val ocr: Boolean = false
) {
    Search("Web search", "What do you want to look up?", search = true),
    Translate("Translate", "Text to translate, and the target language (e.g. 'to Hindi: ...')"),
    Study("Study / Write", "Topic, question or draft you want help with"),
    Plan("Daily plan", "Tell me your tasks, time and energy for today"),
    Photo("Describe photo", "What should I look at? (optional)", needsPhoto = true),
    Ocr("Read text (OCR)", "", needsPhoto = true, ocr = true),
    Doc("Summarize file", "Any focus? (optional)", needsFile = true),
    Voice("Voice note", "Tap Speak, then Run to get a summary", mic = true)
}

@Composable
fun ToolsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val result by vm.toolResult.collectAsState()
    val busy by vm.toolBusy.collectAsState()
    val error by vm.toolError.collectAsState()

    var tool by remember { mutableStateOf(Tool.Search) }
    var input by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) bitmap = bmp
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) bitmap = ImageTools.loadBitmap(ctx, uri)
        if (uri != null && bitmap == null) Toast.makeText(ctx, "Could not open that image", Toast.LENGTH_SHORT).show()
    }
    val filePick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { fileUri = uri; fileName = DocTool.nameOf(ctx, uri) }
    }
    val listen = rememberSpeechInput { input = if (input.isBlank()) it else "$input $it" }

    fun run() {
        val t = tool
        when {
            t.ocr -> {
                val b = bitmap ?: return vm.showToolError("Pick or take a photo first.")
                scope.launch {
                    ImageTools.ocr(b)
                        .onSuccess { vm.showToolText(it.ifBlank { "No text found in the image." }) }
                        .onFailure { vm.showToolError("Text recognition failed.") }
                }
            }
            t.needsPhoto -> {
                val b = bitmap ?: return vm.showToolError("Pick or take a photo first.")
                vm.runTool(
                    "Describe what you see and help with: ${input.ifBlank { "explain this image" }}",
                    Attachment(ImageTools.toJpeg(b), "image/jpeg")
                )
            }
            t.needsFile -> {
                val u = fileUri ?: return vm.showToolError("Choose a file first.")
                scope.launch {
                    val mime = DocTool.mimeOf(ctx, u)
                    val focus = input.ifBlank { "Give the key points." }
                    if (mime == "application/pdf") {
                        DocTool.readBytes(ctx, u)
                            .onSuccess { vm.runTool("Summarize this document. $focus", Attachment(it, mime)) }
                            .onFailure { vm.showToolError(it.message ?: "Could not read the file.") }
                    } else if (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml")) {
                        DocTool.readText(ctx, u)
                            .onSuccess { vm.runTool("Summarize this document. $focus\n\n$it") }
                            .onFailure { vm.showToolError(it.message ?: "Could not read the file.") }
                    } else {
                        vm.showToolError("Only PDF and plain-text files are supported for now.")
                    }
                }
            }
            t.search -> vm.runTool(
                "Search the web and answer, with sources if available: $input", useSearch = true
            )
            t.mic -> vm.runTool("Turn this voice note into a short summary and a to-do list:\n$input")
            t == Tool.Translate -> vm.runTool(
                "Translate as requested. Also give a short pronunciation guide and one tip for learning it:\n$input"
            )
            t == Tool.Plan -> vm.runTool(
                "Make a realistic, friendly plan for today with times, breaks and priorities. Details:\n$input"
            )
            else -> vm.runTool(input)
        }
    }

    ScreenScaffold("Tools & Documents", onBack) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tool.values().forEach { t ->
                FilterChip(
                    selected = tool == t,
                    onClick = { tool = t; vm.showToolText(""); vm.toolResult.value = null },
                    label = { Text(t.label) }
                )
            }
        }
        if (!tool.ocr) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tool.hint) }, minLines = 3, maxLines = 8
            )
        }
        if (tool.mic) OutlinedButton(onClick = listen, modifier = Modifier.heightIn(min = 48.dp)) { Text("🎤 Speak") }
        if (tool.needsPhoto) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { camera.launch(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📷 Camera") }
                OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("🖼️ Gallery") }
            }
            if (bitmap != null) Text("Photo ready ✓", color = MaterialTheme.colorScheme.secondary)
        }
        if (tool.needsFile) {
            OutlinedButton(
                onClick = { filePick.launch(arrayOf("application/pdf", "text/*")) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("📄 Choose PDF or text file") }
            if (fileName.isNotEmpty()) Text("Selected: $fileName", color = MaterialTheme.colorScheme.secondary)
        }
        Button(
            onClick = { run() },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) { Text(if (tool.ocr) "Read text" else "Run") }

        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.takeIf { it.isNotBlank() }?.let { r ->
            InfoCard("Result", r)
            OutlinedButton(onClick = {
                clip.setText(AnnotatedString(r))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        }
        InfoCard(
            "Notes",
            "Photos and files you choose here are sent to Gemini to get an answer. OCR runs on your phone and " +
                "reads Latin-script text only. PDFs up to 15 MB. Web search uses Gemini's Google Search tool, so it " +
                "depends on your model and plan."
        )
    }
}
