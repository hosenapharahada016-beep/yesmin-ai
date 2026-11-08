import androidx.compose.runtime.getValue
package com.yesmin.ai.ui.components
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yesmin.ai.ai.CallState
import com.yesmin.ai.ui.theme.YCyan
import com.yesmin.ai.ui.theme.YPink
import com.yesmin.ai.ui.theme.YPurple

/** Animated circular voice indicator. Color and motion follow the call state. */
@Composable
fun Orb(state: CallState, level: Float, size: Dp = 220.dp) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )
    val pulse by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse"
    )
    val base: Color = when (state) {
        CallState.Listening -> YCyan
        CallState.Thinking -> YPurple
        CallState.Speaking -> YPink
        CallState.Connecting -> YPurple
        CallState.Error -> Color(0xFFFF6B8A)
        else -> Color(0xFF6B5FA0)
    }
    val active = state == CallState.Listening || state == CallState.Speaking ||
        state == CallState.Thinking || state == CallState.Connecting

    Box(Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            val amp = if (state == CallState.Listening) level * 0.35f else if (state == CallState.Speaking) 0.18f * pulse else 0.05f * pulse
            val core = r * (0.42f + amp + if (active) 0.04f * pulse else 0f)

            // soft glow
            drawCircle(
                Brush.radialGradient(listOf(base.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r),
                radius = r, center = c
            )
            // rotating rings
            for (i in 0 until 3) {
                val rr = r * (0.62f + i * 0.14f)
                drawCircle(
                    color = (if (i % 2 == 0) YCyan else YPurple).copy(alpha = if (active) 0.55f else 0.2f),
                    radius = rr, center = c,
                    style = Stroke(width = 2.5f)
                )
            }
            // orbiting spark
            val ang = Math.toRadians((spin).toDouble())
            val orbitR = r * 0.76f
            drawCircle(
                YPink.copy(alpha = if (active) 0.9f else 0.3f), radius = 7f,
                center = Offset(c.x + (orbitR * Math.cos(ang)).toFloat(), c.y + (orbitR * Math.sin(ang)).toFloat())
            )
            // core
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), base, YPurple.copy(alpha = 0.9f)), center = c, radius = core),
                radius = core, center = c
            )
        }
    }
}
