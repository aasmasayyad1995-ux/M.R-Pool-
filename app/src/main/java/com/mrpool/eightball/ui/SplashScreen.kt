package com.mrpool.eightball.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

/**
 * The studio card, shown once when the app opens.
 *
 * The infinity mark draws itself as a single continuous stroke — see [Lemniscate] for why
 * the shape is a real curve rather than a glyph — then the name fades up underneath it.
 * A tap anywhere skips straight to the lobby.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val strokeProgress = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }
    val nameAlpha = remember { Animatable(0f) }
    val nameLift = remember { Animatable(18f) }
    val ruleWidth = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        glowAlpha.animateTo(1f, tween(durationMillis = 420, easing = LinearEasing))
        strokeProgress.animateTo(1f, tween(durationMillis = 1150, easing = FastOutSlowInEasing))
        nameAlpha.animateTo(1f, tween(durationMillis = 520, easing = LinearEasing))
        nameLift.animateTo(0f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        ruleWidth.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        delay(HOLD_MILLIS)
        exitAlpha.animateTo(0f, tween(durationMillis = 320, easing = LinearEasing))
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF12211D), Ink),
                    radius = 1400f
                )
            )
            .alpha(exitAlpha.value)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { finish() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            InfinityMark(
                progress = strokeProgress.value,
                glow = glowAlpha.value,
                modifier = Modifier
                    .size(width = 190.dp, height = 96.dp)
            )

            Text(
                text = "INFINITY CORE",
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFF8E7A6), Gold, Color(0xFFE0A83C), Gold)
                    ),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 9.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .padding(top = 26.dp)
                    .alpha(nameAlpha.value)
            )

            // A hairline that opens out from the centre under the name.
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    // Guarded: a zero fraction is rejected by some Compose versions.
                    .fillMaxWidth((0.52f * ruleWidth.value).coerceIn(0.0001f, 1f))
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, GoldDim, Color.Transparent)
                        )
                    )
            )

            Text(
                text = "PRESENTS",
                color = Chalk.copy(alpha = 0.42f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 6.sp,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .alpha(ruleWidth.value)
            )
        }

        Text(
            text = "tap to skip",
            color = Chalk.copy(alpha = 0.22f),
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .alpha(nameAlpha.value * 0.9f)
        )
    }
}

/**
 * The mark itself: one stroke travelling around the lemniscate, with a couple of wider,
 * fainter passes underneath standing in for a glow (a real blur needs API 31).
 */
@Composable
private fun InfinityMark(progress: Float, glow: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val halfWidth = size.width / 2f - 10f
        val halfHeight = size.height / 2f - 10f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        val path = Path()
        val points = Lemniscate.points(steps = 260, halfWidth = halfWidth, halfHeight = halfHeight)
        points.forEachIndexed { index, point ->
            val x = centerX + point.x
            val y = centerY + point.y
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val measure = PathMeasure()
        measure.setPath(path, false)
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, true)

        val brush = Brush.linearGradient(
            colors = listOf(Cyan, Gold, Color(0xFFFFE9A8), Cyan),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )

        // Fainter, wider passes first: the further out, the dimmer.
        drawPath(drawn, brush, alpha = 0.10f * glow, style = Stroke(width = 18f, cap = StrokeCap.Round))
        drawPath(drawn, brush, alpha = 0.22f * glow, style = Stroke(width = 10f, cap = StrokeCap.Round))
        drawPath(drawn, brush, alpha = glow, style = Stroke(width = 4.2f, cap = StrokeCap.Round))

        // A bright bead riding the leading edge while the stroke is still being drawn.
        if (progress > 0.02f && progress < 0.995f) {
            val head = measure.getPosition(measure.length * progress)
            drawCircle(color = Cyan, radius = 11f, center = head, alpha = 0.25f * glow)
            drawCircle(color = Color(0xFFFFF3C4), radius = 5.5f, center = head, alpha = glow)
        }
    }
}

/** How long the finished card sits on screen before it fades out. */
private const val HOLD_MILLIS = 900L
