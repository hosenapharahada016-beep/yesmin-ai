package com.yesmin.ai.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yesmin.ai.MainViewModel
import com.yesmin.ai.ai.CallState
import com.yesmin.ai.ai.LiveSession
import com.yesmin.ai.ui.components.InfoCard
import com.yesmin.ai.ui.components.Orb
import com.yesmin.ai.ui.components.ScreenScaffold

@Composable
fun CallScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val session by vm.session.collectAsState()
    val notice by vm.callNotice.collectAsState()
    var denied by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
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
            permLauncher.launch(perms.toTypedArray())
        }
    }

    ScreenScaffold("Call Yesmin", onBack, scroll = false) {
        val s = session
        if (s == null) {
            Idle(onStart = ::begin)
        } else {
            CallBody(vm, s, onRestart = ::begin)
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (denied) {
            InfoCard(
                "Microphone permission needed",
                "Yesmin can't hear you without the microphone. Allow it in app settings to make calls."
            )
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Open app settings") }
        }
    }
}

@Composable
private fun Idle(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Orb(CallState.Idle, 0f)
        Text(
            "Start a live voice call. Use headphones for the best experience, since speaker audio can be picked up by the mic.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onStart, modifier = Modifier.heightIn(min = 56.dp)) { Text("📞  Start call") }
    }
}

@Composable
private fun CallBody(vm: MainViewModel, s: LiveSession, onRestart: () -> Unit) {
    val state by s.state.collectAsState()
    val level by s.level.collectAsState()
    val secs by s.seconds.collectAsState()
    val error by s.error.collectAsState()
    val captions by s.captions.collectAsState()
    var muted by remember(s) { mutableStateOf(false) }

    // Remove the foreground notification once the call is over for any reason.
    LaunchedEffect(state) {
        if (state == CallState.Ended || state == CallState.Error) vm.callFinished()
    }
    DisposableEffect(s) { onDispose { /* call keeps running if the user leaves the screen */ } }

    val live = state == CallState.Connecting || state == CallState.Listening ||
        state == CallState.Thinking || state == CallState.Speaking

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Orb(state, level, size = 200.dp)
        Text(
            when (state) {
                CallState.Connecting -> "Connecting…"
                CallState.Listening -> if (muted) "Muted" else "Listening…"
                CallState.Thinking -> "Thinking…"
                CallState.Speaking -> "Speaking…"
                CallState.Ended -> "Call ended"
                CallState.Error -> "Call failed"
                CallState.Idle -> "Ready"
            },
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "%02d:%02d".format(secs / 60, secs % 60) + "  ·  " + if (live) "Connected" else "Offline",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Text(
                "Tip: you can still talk with Yesmin in AI Chat using 🎤 Speak and \"Speak replies\". " +
                    "Check the Live model name in Settings (Load models shows what your key supports).",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                OutlinedButton(
                    onClick = { muted = !muted; s.muted = muted },
                    modifier = Modifier.heightIn(min = 52.dp)
                ) { Text(if (muted) "🔇 Unmute" else "🎙️ Mute") }
                OutlinedButton(
                    onClick = { s.interrupt() },
                    enabled = state == CallState.Speaking,
                    modifier = Modifier.heightIn(min = 52.dp)
                ) { Text("✋ Interrupt") }
                Button(
                    onClick = { vm.endCall() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 52.dp)
                ) { Text("End") }
            } else {
                Button(onClick = onRestart, modifier = Modifier.heightIn(min = 52.dp)) { Text("📞 Call again") }
            }
        }

        Text("Subtitles", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(captions) { c ->
                Text(
                    (if (c.fromUser) "You: " else "Yesmin: ") + c.text,
                    color = if (c.fromUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
