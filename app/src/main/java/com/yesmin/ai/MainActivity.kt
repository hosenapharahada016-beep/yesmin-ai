package com.yesmin.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yesmin.ai.ui.screens.AboutScreen
import com.yesmin.ai.ui.screens.AutomationScreen
import com.yesmin.ai.ui.screens.CallScreen
import com.yesmin.ai.ui.screens.ChatScreen
import com.yesmin.ai.ui.screens.CodingScreen
import com.yesmin.ai.ui.screens.HomeScreen
import com.yesmin.ai.ui.screens.MemoryScreen
import com.yesmin.ai.ui.screens.PermissionsScreen
import com.yesmin.ai.ui.screens.SettingsScreen
import com.yesmin.ai.ui.screens.ToolsScreen
import com.yesmin.ai.ui.theme.YesminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val theme by vm.themeMode.collectAsState()
            val pending by vm.pending.collectAsState()

            YesminTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val back: () -> Unit = { nav.popBackStack() }
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") { HomeScreen(vm) { nav.navigate(it) } }
                        composable("chat") { ChatScreen(vm, back) }
                        composable("call") { CallScreen(vm, back) }
                        composable("coding") { CodingScreen(vm, back) }
                        composable("automation") { AutomationScreen(vm, back) }
                        composable("memory") { MemoryScreen(vm, back) }
                        composable("tools") { ToolsScreen(vm, back) }
                        composable("settings") { SettingsScreen(vm, back) }
                        composable("permissions") { PermissionsScreen(vm, back) }
                        composable("about") { AboutScreen(back) }
                    }
                }

                pending?.let { p ->
                    AlertDialog(
                        onDismissRequest = { vm.dismissPending() },
                        title = { Text(if (p.type == "summary") "Save call summary?" else "Remember this?") },
                        text = {
                            Text(
                                "\"${p.text}\"\n\nSaved only on this phone. You can edit or delete it later in Memory. " +
                                    "Choose Not now if it contains anything private."
                            )
                        },
                        confirmButton = { TextButton(onClick = { vm.confirmPending() }) { Text("Save") } },
                        dismissButton = { TextButton(onClick = { vm.dismissPending() }) { Text("Not now") } }
                    )
                }
            }
        }
    }
}
