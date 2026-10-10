package com.mrpool.eightball.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PlayerProfile

/** The cue rack: twelve cues, the first one free and the rest priced from 200 coins up. */
@Composable
fun CueShopScreen(
    profile: PlayerProfile,
    onBack: () -> Unit,
    onBuy: (CueStick) -> Unit,
    onEquip: (CueStick) -> Unit
) {
    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Choose Cue Stick",
                subtitle = "${profile.ownedCueIds.size} of ${CueStick.ALL.size} unlocked",
                coins = profile.coins,
                onBack = onBack
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp, end = 18.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(CueStick.ALL, key = { it.id }) { cue ->
                    CueCard(
                        cue = cue,
                        owned = profile.owns(cue),
                        equipped = profile.equippedCue.id == cue.id,
                        affordable = profile.coins >= cue.price,
                        onBuy = { onBuy(cue) },
                        onEquip = { onEquip(cue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CueCard(
    cue: CueStick,
    owned: Boolean,
    equipped: Boolean,
    affordable: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    val accent = Color(cue.accentColor.toInt())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(InkSoft)
            .border(
                BorderStroke(if (equipped) 2.dp else 1.dp, if (equipped) Gold else accent.copy(alpha = 0.4f)),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cue.name, style = MaterialTheme.typography.titleLarge, color = Chalk)
                Text(
                    cue.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Chalk.copy(alpha = 0.55f)
                )
            }
            if (equipped) {
                Text(
                    "EQUIPPED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Gold)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        CuePreview(cue)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StarRating("POWER", cue.powerStars)
                StarRating("AIM  ", cue.aimStars)
                StarRating("SPIN ", cue.spinStars)
            }
            when {
                owned && equipped -> Unit
                owned -> Button(
                    onClick = onEquip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6E4A))
                ) { Text("Equip", color = Chalk) }
                else -> Button(
                    onClick = onBuy,
                    enabled = affordable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (affordable) Gold else Color(0xFF32383B)
                    )
                ) {
                    Text(
                        if (affordable) "Buy ${formatCoins(cue.price)}" else "Need ${formatCoins(cue.price)}",
                        color = if (affordable) Ink else Chalk.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/** A flat side-on drawing of the cue, coloured exactly like the 3D model in the game. */
@Composable
private fun CuePreview(cue: CueStick) {
    val shaft = Color(cue.shaftColor.toInt())
    val butt = Color(cue.buttColor.toInt())
    val accent = Color(cue.accentColor.toInt())

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        val w = size.width
        val h = size.height

        // Laid on a slight diagonal and tapered, because a cue drawn as a flat bar of one
        // thickness is a stick drawn by somebody who has not held one.
        val tipX = w * 0.015f
        val buttX = w * 0.985f
        val tipY = h * 0.62f
        val buttY = h * 0.40f
        val tipR = h * 0.075f        // a cue tip is about 13mm
        val buttR = h * 0.165f       // the butt is roughly twice that

        fun yAt(t: Float) = tipY + (buttY - tipY) * t
        fun rAt(t: Float) = tipR + (buttR - tipR) * t
        fun xAt(t: Float) = tipX + (buttX - tipX) * t

        /** One tapered length of the cue, as a four cornered sliver. */
        fun segment(t0: Float, t1: Float): Path = Path().apply {
            moveTo(xAt(t0), yAt(t0) - rAt(t0))
            lineTo(xAt(t1), yAt(t1) - rAt(t1))
            lineTo(xAt(t1), yAt(t1) + rAt(t1))
            lineTo(xAt(t0), yAt(t0) + rAt(t0))
            close()
        }

        /** Rounded across its width, so it reads as a rod and not a plank. */
        fun shade(t0: Float, t1: Float, c: Color) {
            drawPath(
                path = segment(t0, t1),
                brush = Brush.verticalGradient(
                    0f to c.darken(0.45f),
                    0.30f to c.lighten(0.30f),
                    0.55f to c,
                    1f to c.darken(0.55f),
                    startY = yAt(t0) - rAt(t1),
                    endY = yAt(t0) + rAt(t1)
                )
            )
        }

        // The shadow it casts on the card.
        drawPath(
            path = Path().apply {
                moveTo(tipX, tipY + tipR * 2.6f)
                lineTo(buttX, buttY + buttR * 2.2f)
                lineTo(buttX, buttY + buttR * 3.4f)
                lineTo(tipX, tipY + tipR * 4.2f)
                close()
            },
            color = Color(0x33000000)
        )

        shade(0.045f, 0.52f, shaft)                 // shaft
        shade(0.52f, 0.60f, accent)                 // joint collar
        shade(0.60f, 0.78f, butt.lighten(0.10f))    // forearm
        shade(0.78f, 0.93f, butt.darken(0.18f))     // wrap, darker where it is held
        shade(0.93f, 1f, accent.darken(0.25f))      // butt cap

        // Ferrule and tip, the two small things that say "cue" at a glance.
        shade(0.018f, 0.045f, Color(0xFFF2EDE2))
        drawPath(segment(0f, 0.018f), color = Color(0xFF5B86C4))

        // Inlay points up the forearm.
        for (i in 0..2) {
            val t = 0.635f + i * 0.045f
            drawPath(
                path = Path().apply {
                    moveTo(xAt(t), yAt(t) - rAt(t) * 0.80f)
                    lineTo(xAt(t + 0.030f), yAt(t) - rAt(t) * 0.05f)
                    lineTo(xAt(t), yAt(t) + rAt(t) * 0.70f)
                    close()
                },
                color = accent.lighten(0.25f)
            )
        }

        // One long highlight along the top of the rod. This is the line that does most of
        // the work of making it look round rather than flat.
        drawPath(
            path = Path().apply {
                moveTo(xAt(0.05f), yAt(0.05f) - rAt(0.05f) * 0.52f)
                lineTo(xAt(1f), yAt(1f) - rAt(1f) * 0.58f)
                lineTo(xAt(1f), yAt(1f) - rAt(1f) * 0.22f)
                lineTo(xAt(0.05f), yAt(0.05f) - rAt(0.05f) * 0.14f)
                close()
            },
            color = Color(0x3AFFFFFF)
        )
    }
}

