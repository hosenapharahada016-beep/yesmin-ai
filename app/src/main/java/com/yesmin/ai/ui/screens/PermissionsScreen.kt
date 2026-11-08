package com.yesmin.ai.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
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
fun PermissionsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val actions = remember { Actions(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val mic = tick >= 0 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    ScreenScaffold("Permissions & Privacy", onBack) {
        StatusRow("Microphone (calls, voice notes)", mic, "App settings") {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        StatusRow("Screen control (Accessibility)", tick >= 0 && YesminAccessibilityService.isEnabled, "Open") {
            actions.openAccessibilitySettings()
        }
        StatusRow("Notification access", tick >= 0 && YesminNotificationListener.isConnected, "Open") {
            actions.openNotificationAccessSettings()
        }

        InfoCard(
            "What each permission is for",
            "• Internet: talk to Gemini.\n" +
                "• Microphone: live calls. Asked only when you start a call.\n" +
                "• Notifications: shows the \"call in progress\" notice while the mic is on.\n" +
                "• Alarms: sends alarm requests to your clock app.\n" +
                "• Accessibility and Notification access: optional, off by default, enabled only by you in Android settings."
        )
        InfoCard(
            "Where your data goes",
            "• Your messages, calls' audio, and any photo or file you choose are sent to Google's Gemini API to get answers, under your own API key and Google's terms.\n" +
                "• Memories and settings stay on this phone.\n" +
                "• Your API key is encrypted on this phone and sent only to Google.\n" +
                "• There is no Yesmin server, analytics or ads."
        )
        InfoCard(
            "Safety",
            "Yesmin can't bypass Android's security. Calls, messages, alarms and calendar events always open the real app for you to confirm."
        )
    }
}
