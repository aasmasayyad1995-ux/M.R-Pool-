package com.mrpool.eightball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrpool.eightball.game.Vec2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How hard the shot is wound up, as the cue is drawn back.
 *
 * Read only on purpose. The shot is played by drawing the cue back on the table and letting
 * go, so a second way to set the power would only be a way to disagree with the first.
 */
@Composable
fun PowerMeter(
    power: Float,
    live: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(34.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x99101617))
            .border(
                1.dp,
                if (live) Gold else Chalk.copy(alpha = 0.18f),
                RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(power.coerceIn(0.02f, 1f))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Crimson, Color(0xFFE0A050), Color(0xFF4CC38A))
                    )
                )
        )
        Text(
            "${(power * 100).roundToInt()}",
            color = if (live) Chalk else Chalk.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
}

/**
 * The spin pad: a cue ball you drag the contact point around on, exactly like chalking up
 * before a shot. Centre is a plain stun hit.
 */
@Composable
fun SpinPad(
    spin: Vec2,
    limit: Float,
    enabled: Boolean,
    onSpinChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val size = 92.dp
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFF3F1EA))
            .border(2.dp, Chalk.copy(alpha = 0.35f), CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val radius = with(density) { size.toPx() } / 2f
                fun report(x: Float, y: Float) {
                    var nx = (x - radius) / radius
                    var ny = (y - radius) / radius
                    val length = sqrt(nx * nx + ny * ny)
                    if (length > 1f) {
                        nx /= length
                        ny /= length
                    }
                    // Screen Y grows downwards; top of the ball is follow.
                    onSpinChange(nx, -ny)
                }
                detectDragGestures(
                    onDragStart = { report(it.x, it.y) },
                    onDrag = { change, _ ->
                        report(change.position.x, change.position.y)
                        change.consume()
                    }
                )
            }
    ) {
        val normalized = if (limit > 0f) Vec2(spin.x / limit, spin.y / limit) else Vec2.ZERO
        val offsetX = (normalized.x * (size.value / 2f - 12f)).dp
        val offsetY = (-normalized.y * (size.value / 2f - 12f)).dp
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = offsetX, y = offsetY)
                .size(20.dp)
                .clip(CircleShape)
                .background(Crimson.copy(alpha = 0.85f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(3.dp)
                .clip(CircleShape)
                .background(Color(0x33000000))
        )
    }
}

/** Small round button used for the camera and fine tune controls. */
@Composable
fun RoundControl(
    label: String,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 42.dp,
    background: Color = Color(0xAA141B1D),
    contentColor: Color = Chalk,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, Chalk.copy(alpha = 0.2f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Full screen tint used behind the result dialog. */
@Composable
fun DimScrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC05090A)),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
