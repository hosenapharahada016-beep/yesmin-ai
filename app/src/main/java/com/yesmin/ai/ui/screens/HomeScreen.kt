package com.yesmin.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.ai.CallState
import com.yesmin.ai.ai.LiveSession
import com.yesmin.ai.avatar.AvatarView
import com.yesmin.ai.ui.theme.YCyan

private data class Dest(val route: String, val emoji: String, val title: String)

private val destinations = listOf(
    Dest("chat", "💬", "Chat"),
    Dest("call", "📞", "Call"),
    Dest("coding", "💻", "Coding"),
    Dest("automation", "📱", "Phone"),
    Dest("memory", "🧠", "Memory"),
    Dest("tools", "🛠️", "Tools"),
    Dest("settings", "⚙️", "Settings"),
    Dest("permissions", "🔒", "Privacy"),
    Dest("about", "ℹ️", "About")
)

private val SkyTop = Color(0xFF020B1A)
private val SkyMid = Color(0xFF06223F)
private val SkyLow = Color(0xFF0A3550)

@Composable
fun HomeScreen(vm: MainViewModel, onNav: (String) -> Unit) {
    val name by vm.assistantName.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val session by vm.session.collectAsState()
    val avatarOn by vm.avatarEnabled.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyMid, SkyLow)))
    ) {
        HudGrid()
        Column(Modifier.fillMaxSize()) {
            Header(name)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AvatarView(vm.avatar, Modifier.fillMaxSize(), enabled = avatarOn)
            }

            if (!hasKey) {
                Text(
                    "Add your Gemini API key in Settings to wake $name up →",
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x3322D3EE))
                        .clickable { onNav("settings") }
                        .padding(12.dp),
                    color = Color.White
                )
            }

            val s = session
            if (s == null) {
                StartControls(vm, name, null)
            } else {
                LiveControls(vm, s, name)
            }

            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(destinations) { d ->
                    AssistChip(
                        onClick = { onNav(d.route) },
                        label = { Text("${d.emoji} ${d.title}", color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x22FFFFFF)),
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HudGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val line = YCyan.copy(alpha = 0.06f)
        val step = 48.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(
            Brush.radialGradient(
                listOf(YCyan.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.4f),
                radius = size.width * 0.8f
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width / 2, size.height * 0.4f)
        )
    }
}

@Composable
private fun Header(name: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            name.uppercase(),
            color = YCyan,
            fontSize = 26.sp,
            letterSpacing = 8.sp
        )
        Text("AI companion · assistant", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

/** Starts a call after asking for microphone permission. Shared by the idle and error states. */
@Composable
private fun StartControls(vm: MainViewModel, name: String, message: String?) {
    val ctx = LocalContext.current
    val notice by vm.callNotice.collectAsState()
    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) {
            denied = false
            vm.startCall()
        } else {
            denied = true
        }
    }

    fun begin() {
        val hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            denied = false
            vm.startCall()
        } else {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            launcher.launch(perms.toTypedArray())
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        if (denied) {
            Text(
                "The microphone permission was denied, so $name can't hear you. Allow it in Privacy → app settings.",
                color = Color(0xFFFFB4C4), textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = { begin() },
            colors = ButtonDefaults.buttonColors(containerColor = YCyan, contentColor = Color(0xFF00252B)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) { Text("🎙️  Talk to $name", fontSize = 17.sp) }
    }
}

@Composable
private fun LiveControls(vm: MainViewModel, s: LiveSession, name: String) {
    val state by s.state.collectAsState()
    val secs by s.seconds.collectAsState()
    val error by s.error.collectAsState()
    val captions by s.captions.collectAsState()
    var muted by remember(s) { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state == CallState.Ended || state == CallState.Error) vm.callFinished()
    }

    val live = state == CallState.Connecting || state == CallState.Listening ||
        state == CallState.Thinking || state == CallState.Speaking

    if (!live) {
        StartControls(vm, name, error)
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            when (state) {
                CallState.Connecting -> "Connecting…"
                CallState.Listening -> if (muted) "Muted" else "Listening…"
                CallState.Thinking -> "Thinking…"
                CallState.Speaking -> "Speaking…"
                else -> ""
            } + "   ·   %02d:%02d".format(secs / 60, secs % 60),
            color = YCyan
        )
        captions.takeLast(2).forEach {
            Text(
                (if (it.fromUser) "You: " else "$name: ") + it.text,
                color = Color.White.copy(alpha = if (it.fromUser) 0.65f else 0.95f),
                fontSize = 14.sp,
                maxLines = 3,
                textAlign = TextAlign.Center
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { muted = !muted; s.muted = muted },
                modifier = Modifier.heightIn(min = 52.dp)
            ) { Text(if (muted) "🔇 Unmute" else "🎙️ Mute", color = Color.White) }
            OutlinedButton(
                onClick = { s.interrupt() },
                enabled = state == CallState.Speaking,
                modifier = Modifier.heightIn(min = 52.dp)
            ) { Text("✋ Interrupt", color = Color.White) }
            Button(
                onClick = { vm.endCall() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 52.dp)
            ) { Text("End") }
        }
    }
}
