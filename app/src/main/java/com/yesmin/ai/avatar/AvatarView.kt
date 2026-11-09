package com.yesmin.ai.avatar

import android.view.TextureView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.yesmin.ai.ai.CallState
import com.yesmin.ai.ui.components.Avatar
import com.yesmin.ai.ui.components.Orb
import com.yesmin.ai.ui.theme.YCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Shows the 3D avatar when a model is loaded, a loading indicator while it loads, and a clearly
 * labelled placeholder when there is no model or loading failed. It never crashes the screen.
 *
 * @param enabled false skips the 3D engine completely (saves battery and memory)
 */
@Composable
fun AvatarView(
    brain: AvatarController,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val appContext = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val status by brain.modelStatus.collectAsState()
    val reload by brain.reloadToken.collectAsState()
    val phase by brain.phase.collectAsState()
    val expression by brain.effectiveExpression.collectAsState()

    val renderer = remember { AvatarRenderer(brain.config, brain) }
    val loader = remember { AvatarModelLoader(appContext, brain.config) }

    // Release all GPU resources when this view leaves the screen.
    DisposableEffect(renderer) {
        renderer.onFatalError = { message ->
            renderer.unloadModel()
            brain.setModelStatus(ModelStatus.Error(message))
        }
        onDispose {
            renderer.onFatalError = null
            renderer.destroy()
            brain.setModelStatus(ModelStatus.NotLoaded)
        }
    }

    // Draw only while the screen is visible.
    DisposableEffect(owner, renderer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> renderer.setVisible(true)
                Lifecycle.Event.ON_PAUSE -> renderer.setVisible(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Find and load the model.
    LaunchedEffect(reload, enabled) {
        renderer.unloadModel()
        if (!enabled) {
            brain.setModelStatus(ModelStatus.Placeholder("The 3D avatar is turned off in Settings."))
            return@LaunchedEffect
        }
        brain.setModelStatus(ModelStatus.Loading)
        when (val result = loader.load()) {
            is AvatarModelLoader.LoadResult.Missing ->
                brain.setModelStatus(ModelStatus.Placeholder(result.reason))
            is AvatarModelLoader.LoadResult.Invalid ->
                brain.setModelStatus(ModelStatus.Error(result.message))
            is AvatarModelLoader.LoadResult.Success -> {
                val engineError = renderer.ensureEngine()
                if (engineError != null) {
                    brain.setModelStatus(ModelStatus.Error(engineError))
                } else {
                    try {
                        val info = renderer.loadModel(result.buffer, result.glb, result.source, result.bytes)
                        brain.setModelStatus(ModelStatus.Ready(info))
                    } catch (t: Throwable) {
                        renderer.unloadModel()
                        brain.setModelStatus(ModelStatus.Error(t.message ?: "The 3D model could not be shown."))
                    }
                }
            }
        }
    }

    // When the 3D renderer is not running, a light 15 fps loop keeps expression state moving for the placeholder.
    val needsPlaceholderTick = status !is ModelStatus.Ready
    LaunchedEffect(needsPlaceholderTick, owner) {
        if (needsPlaceholderTick) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                brain.resetClock()
                while (isActive) {
                    brain.tick()
                    delay(66)
                }
            }
        }
    }

    Box(modifier) {
        val showSurface = status is ModelStatus.Loading || status is ModelStatus.Ready
        if (showSurface) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (status is ModelStatus.Ready) 1f else 0f),
                factory = { ctx -> TextureView(ctx).also { renderer.attach(it) } },
                onRelease = { renderer.detachSurface() }
            )
        }

        when (val s = status) {
            is ModelStatus.Loading, ModelStatus.NotLoaded -> LoadingOverlay()
            is ModelStatus.Placeholder -> PlaceholderAvatar(phase, brain, "3D model not installed", s.reason, null)
            is ModelStatus.Error -> PlaceholderAvatar(phase, brain, "3D avatar unavailable", s.message) { brain.reload() }
            is ModelStatus.Ready -> Unit
        }

        // small label, useful for checking that expressions follow the conversation
        Text(
            expression.name.lowercase(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = YCyan.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun LoadingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = YCyan)
            Text("Loading 3D avatar…", color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Stand-in shown when no 3D model is available. It reacts to the conversation but is NOT the 3D character. */
@Composable
private fun PlaceholderAvatar(
    phase: ConversationPhase,
    brain: AvatarController,
    title: String,
    detail: String,
    onRetry: (() -> Unit)?
) {
    val mouth by brain.mouthLevel.collectAsState()
    val orbState = when (phase) {
        ConversationPhase.LISTENING -> CallState.Listening
        ConversationPhase.THINKING -> CallState.Thinking
        ConversationPhase.SPEAKING -> CallState.Speaking
        ConversationPhase.IDLE -> CallState.Idle
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight) * 0.85f
            Orb(orbState, mouth, size = side)
            Avatar(size = side * 0.55f)
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("PLACEHOLDER · $title", style = MaterialTheme.typography.labelLarge, color = YCyan)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}
