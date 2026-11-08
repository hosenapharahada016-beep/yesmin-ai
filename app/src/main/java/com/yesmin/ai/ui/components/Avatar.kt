package com.yesmin.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yesmin.ai.ui.theme.YCyan
import com.yesmin.ai.ui.theme.YPink
import com.yesmin.ai.ui.theme.YPurple

/** Stylized vector avatar for Yesmin, drawn in code so it needs no image assets. */
@Composable
fun Avatar(size: Dp = 120.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2, h / 2)

        // background aura
        drawCircle(
            Brush.radialGradient(listOf(YPurple.copy(alpha = 0.55f), Color.Transparent), center = c, radius = w / 2),
            radius = w / 2, center = c
        )
        drawCircle(YCyan.copy(alpha = 0.6f), radius = w * 0.47f, center = c, style = Stroke(width = 3f))

        // back hair
        drawOval(
            Brush.verticalGradient(listOf(Color(0xFF3B1F7A), Color(0xFF7C3AED), YPink.copy(alpha = 0.8f))),
            topLeft = Offset(w * 0.22f, h * 0.12f), size = Size(w * 0.56f, h * 0.74f)
        )
        // neck + shoulders
        drawOval(Color(0xFFF3C9B5), topLeft = Offset(w * 0.43f, h * 0.62f), size = Size(w * 0.14f, h * 0.18f))
        drawOval(
            Brush.horizontalGradient(listOf(YPurple, YCyan)),
            topLeft = Offset(w * 0.24f, h * 0.76f), size = Size(w * 0.52f, h * 0.3f)
        )
        // face
        drawOval(Color(0xFFF7D6C4), topLeft = Offset(w * 0.33f, h * 0.24f), size = Size(w * 0.34f, h * 0.44f))
        // fringe
        val fringe = Path().apply {
            moveTo(w * 0.32f, h * 0.40f)
            quadraticBezierTo(w * 0.40f, h * 0.17f, w * 0.52f, h * 0.22f)
            quadraticBezierTo(w * 0.64f, h * 0.20f, w * 0.68f, h * 0.40f)
            quadraticBezierTo(w * 0.58f, h * 0.30f, w * 0.50f, h * 0.33f)
            quadraticBezierTo(w * 0.40f, h * 0.30f, w * 0.32f, h * 0.40f)
            close()
        }
        drawPath(fringe, Brush.horizontalGradient(listOf(Color(0xFF5B2BB5), Color(0xFF9F67FF))))
        // eyes
        val eyeY = h * 0.46f
        drawOval(Color.White, Offset(w * 0.39f, eyeY), Size(w * 0.07f, h * 0.04f))
        drawOval(Color.White, Offset(w * 0.54f, eyeY), Size(w * 0.07f, h * 0.04f))
        drawCircle(YCyan, w * 0.017f, Offset(w * 0.425f, eyeY + h * 0.02f))
        drawCircle(YCyan, w * 0.017f, Offset(w * 0.575f, eyeY + h * 0.02f))
        // blush
        drawCircle(YPink.copy(alpha = 0.35f), w * 0.03f, Offset(w * 0.38f, h * 0.55f))
        drawCircle(YPink.copy(alpha = 0.35f), w * 0.03f, Offset(w * 0.62f, h * 0.55f))
        // smile
        val smile = Path().apply {
            moveTo(w * 0.44f, h * 0.58f)
            quadraticBezierTo(w * 0.50f, h * 0.63f, w * 0.56f, h * 0.58f)
        }
        drawPath(smile, Color(0xFFD9467A), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
        // earpiece glow (JARVIS touch)
        drawCircle(YCyan, w * 0.014f, Offset(w * 0.32f, h * 0.50f))
    }
}
