package com.yesmin.ai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.ai.ModelInfo
import com.yesmin.ai.ui.components.InfoCard
import com.yesmin.ai.ui.components.ScreenScaffold
import com.yesmin.ai.yesmin
import android.widget.Toast

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = ctx.yesmin.settings
    val hasKey by vm.hasKey.collectAsState()
    val status by vm.connectionStatus.collectAsState()
    val ok by vm.connectionOk.collectAsState()
    val busy by vm.settingsBusy.collectAsState()
    val models by vm.models.collectAsState()
    val theme by vm.themeMode.collectAsState()

    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var chatModel by remember { mutableStateOf(s.chatModel) }
    var liveModel by remember { mutableStateOf(s.liveModel) }
    var temp by remember { mutableFloatStateOf(s.temperature) }
    var persona by remember { mutableStateOf(s.personaExtra) }
    var userName by remember { mutableStateOf(s.userName) }
    var voice by remember { mutableStateOf(s.voiceName) }
    var picker by remember { mutableStateOf<String?>(null) } // "chat" or "live"

    var assistantName by remember { mutableStateOf(s.assistantName) }
    var avatarOn by remember { mutableStateOf(s.avatarEnabled) }
    var headMotion by remember { mutableStateOf(s.avatarHeadMotion) }
    var aiExpr by remember { mutableStateOf(s.avatarAiExpressions) }
    var quality by remember { mutableStateOf(s.avatarQuality) }
    val modelStatus by vm.avatar.modelStatus.collectAsState()
    val avatarNotice by vm.avatarNotice.collectAsState()
    val pickGlb = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAvatarModel(uri)
    }

    ScreenScaffold("Settings", onBack) {
        Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
        Text(
            if (hasKey) "A key is saved (hidden). Enter a new one to replace it." else "No key saved yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = keyInput, onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") }, singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
            Button(
                onClick = { vm.saveKey(keyInput); keyInput = ""; showKey = false },
                enabled = keyInput.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Save") }
            OutlinedButton(onClick = { vm.removeKey() }, enabled = hasKey, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Remove")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.testConnection() }, enabled = hasKey && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Test connection")
            }
            OutlinedButton(onClick = { vm.loadModels() }, enabled = hasKey && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Load models")
            }
            if (busy) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
        }
        status?.let {
            Text(
                it,
                color = when (ok) {
                    true -> MaterialTheme.colorScheme.secondary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        InfoCard(
            "About key safety",
            "Your key is encrypted with the Android Keystore and kept only on this phone, never in the app's code or logs. " +
                "But a key inside any installed app can be extracted by someone with full access to the phone, " +
                "so use a key with a spending limit, and never share the APK together with your key."
        )

        Text("Models", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = chatModel, onValueChange = { chatModel = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Chat model") }, singleLine = true
        )
        if (models.isNotEmpty()) TextButton(onClick = { picker = "chat" }) { Text("Pick from your key's models") }
        OutlinedTextField(
            value = liveModel, onValueChange = { liveModel = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Live voice model") }, singleLine = true
        )
        if (models.isNotEmpty()) TextButton(onClick = { picker = "live" }) { Text("Pick a Live-capable model") }
        Text(
            "Model names change over time. If a model stops working, tap Load models and pick a current one.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Personality", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = assistantName, onValueChange = { assistantName = it.take(24) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Assistant name (shown on home screen)") }, singleLine = true
        )
        OutlinedTextField(
            value = userName, onValueChange = { userName = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("What should Yesmin call you?") }, singleLine = true
        )
        OutlinedTextField(
            value = persona, onValueChange = { persona = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Extra personality notes") },
            placeholder = { Text("e.g. more playful, use Hinglish, call me boss") },
            minLines = 3, maxLines = 6
        )
        Text("Creativity (temperature): %.1f".format(temp))
        Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..2f)
        OutlinedTextField(
            value = voice, onValueChange = { voice = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Live voice name (e.g. Aoede, Puck, Kore)") }, singleLine = true
        )

        Text("3D avatar", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = avatarOn, onCheckedChange = { avatarOn = it; vm.setAvatarEnabled(it) })
            Text("Show 3D avatar on home screen")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = headMotion, onCheckedChange = { headMotion = it; s.avatarHeadMotion = it })
            Text("Gentle head motion (restart to apply)")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = aiExpr, onCheckedChange = { aiExpr = it; s.avatarAiExpressions = it })
            Text("Let the AI choose expressions in calls")
        }
        Text("Avatar quality (restart to apply)", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to "Auto", "low" to "Battery saver", "high" to "High").forEach { (v, label) ->
                FilterChip(selected = quality == v, onClick = { quality = v; s.avatarQuality = v }, label = { Text(label) })
            }
        }
        Text(
            "Current profile: ${vm.avatar.config.profile}, up to ${vm.avatar.config.targetFps} fps, " +
                "model limit ${vm.avatar.config.maxModelBytes / (1024 * 1024)} MB.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickGlb.launch(arrayOf("*/*")) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Import .glb model")
            }
            OutlinedButton(onClick = { vm.removeAvatarModel() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Remove imported")
            }
        }
        avatarNotice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        InfoCard(
            "Avatar status",
            when (val m = modelStatus) {
                is com.yesmin.ai.avatar.ModelStatus.Ready -> "3D model loaded.\n" + m.info.describe()
                is com.yesmin.ai.avatar.ModelStatus.Placeholder -> "Placeholder in use. " + m.reason
                is com.yesmin.ai.avatar.ModelStatus.Error -> "Problem: " + m.message
                com.yesmin.ai.avatar.ModelStatus.Loading -> "Loading…"
                com.yesmin.ai.avatar.ModelStatus.NotLoaded -> "Open the home screen to load the avatar."
            } + "\n\nA bundled model goes in app/src/main/assets/avatar/avatar.glb. An imported model replaces it without rebuilding."
        )

        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (v, label) ->
                FilterChip(selected = theme == v, onClick = { vm.setTheme(v) }, label = { Text(label) })
            }
        }

        Button(
            onClick = {
                s.chatModel = chatModel; s.liveModel = liveModel; s.temperature = temp
                s.personaExtra = persona; s.userName = userName
                vm.setAssistantName(assistantName)
                if (voice.isNotBlank()) s.voiceName = voice
                Toast.makeText(ctx, "Settings saved", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) { Text("Save settings") }
    }

    picker?.let { kind ->
        val options: List<ModelInfo> =
            if (kind == "live") models.filter { it.supportsLive } else models.filter { it.supportsText }
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(if (kind == "live") "Live models" else "Chat models") },
            text = {
                if (options.isEmpty()) Text("Your key lists no models of this kind.")
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options) { m ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (kind == "live") liveModel = m.id else chatModel = m.id
                                    picker = null
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(m.id)
                            if (m.displayName.isNotBlank()) {
                                Text(m.displayName, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("Close") } }
        )
    }
}
