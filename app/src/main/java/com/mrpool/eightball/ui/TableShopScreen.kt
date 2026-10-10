package com.mrpool.eightball.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.PoolTableSkin

/** Twenty tables, the first one free and the rest priced from 1,000 coins up. */
@Composable
fun TableShopScreen(
    profile: PlayerProfile,
    onBack: () -> Unit,
    onBuy: (PoolTableSkin) -> Unit,
    onEquip: (PoolTableSkin) -> Unit
) {
    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Table Selection",
                subtitle = "${profile.ownedTableIds.size} of ${PoolTableSkin.ALL.size} unlocked",
                coins = profile.coins,
                onBack = onBack
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(PoolTableSkin.ALL, key = { it.id }) { table ->
                    TableCard(
                        table = table,
                        owned = profile.owns(table),
                        equipped = profile.equippedTable.id == table.id,
                        affordable = profile.coins >= table.price,
                        onBuy = { onBuy(table) },
                        onEquip = { onEquip(table) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TableCard(
    table: PoolTableSkin,
    owned: Boolean,
    equipped: Boolean,
    affordable: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(InkSoft)
            .border(
                BorderStroke(if (equipped) 2.dp else 1.dp, if (equipped) Gold else Chalk.copy(alpha = 0.12f)),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TablePreview(table)
        Column(modifier = Modifier.weight(1f)) {
            Text(table.name, style = MaterialTheme.typography.titleMedium, color = Chalk)
            Text(
                "${table.venue} · ${table.clothSpeed.label}",
                style = MaterialTheme.typography.labelSmall,
                color = Chalk.copy(alpha = 0.5f)
            )
            Text(
                if (table.isFree) "Free · plays slow and forgiving"
                else "${formatCoins(table.price)} coins · free to play on",
                style = MaterialTheme.typography.bodyMedium,
                color = Gold.copy(alpha = 0.85f)
            )
        }
        when {
            equipped -> Text(
                "IN USE",
                style = MaterialTheme.typography.labelSmall,
                color = Ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Gold)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            owned -> Button(
                onClick = onEquip,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6E4A))
            ) { Text("Play", color = Chalk) }
            else -> Button(
                onClick = onBuy,
                enabled = affordable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (affordable) Gold else Color(0xFF32383B)
                )
            ) {
                Text(
                    formatCoins(table.price),
                    color = if (affordable) Ink else Chalk.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/** A miniature of the table, drawn from the same colours the 3D renderer uses. */
@Composable
private fun TablePreview(table: PoolTableSkin) {
    val felt = Color(table.feltColor.toInt())
    val rail = Color(table.railColor.toInt())
    val trim = Color(table.trimColor.toInt())

    Canvas(modifier = Modifier.size(width = 104.dp, height = 68.dp)) {
        val w = size.width
        val h = size.height

        // The table is drawn as if looked at from the near end and slightly above, so the
        // far rail is narrower than the near one. That one trapezoid is what makes a flat
        // rectangle read as a table standing in a room.
        val farInset = w * 0.17f
        val top = h * 0.17f
        val bottom = h * 0.86f

        fun outer(): Path = Path().apply {
            moveTo(farInset, top)
            lineTo(w - farInset, top)
            lineTo(w, bottom)
            lineTo(0f, bottom)
            close()
        }

        // A soft shadow on the floor, so the table sits rather than floats.
        drawOval(
            color = Color(0x55000000),
            topLeft = Offset(w * 0.06f, bottom - h * 0.06f),
            size = Size(w * 0.88f, h * 0.17f)
        )

        // The rail frame: lit along the top edge, dark along the bottom, which is where
        // the wood gets its thickness from.
        drawPath(
            path = outer(),
            brush = Brush.verticalGradient(
                0f to rail.lighten(0.35f),
                0.45f to rail,
                1f to rail.darken(0.45f),
                startY = top,
                endY = bottom
            )
        )

        // The cloth, inset inside the rails and keeping the same perspective.
        val clothFar = farInset + w * 0.085f
        val clothTop = top + h * 0.13f
        val clothBottom = bottom - h * 0.13f
        val cloth = Path().apply {
            moveTo(clothFar, clothTop)
            lineTo(w - clothFar, clothTop)
            lineTo(w - w * 0.055f, clothBottom)
            lineTo(w * 0.055f, clothBottom)
            close()
        }
        drawPath(
            path = cloth,
            brush = Brush.verticalGradient(
                0f to felt.darken(0.30f),       // far end falls away from the light
                0.42f to felt.lighten(0.12f),   // the lamp sits over the middle
                1f to felt.darken(0.12f),
                startY = clothTop,
                endY = clothBottom
            )
        )

        // Six pockets. Ellipses rather than circles: a round hole seen at an angle is one.
        fun pocket(cx: Float, cy: Float, r: Float) {
            drawOval(                                   // leather lip, catching the light
                color = trim.lighten(0.25f),
                topLeft = Offset(cx - r, cy - r * 0.62f),
                size = Size(r * 2f, r * 1.24f)
            )
            drawOval(                                   // the drop itself
                color = Color(0xFF05070A),
                topLeft = Offset(cx - r * 0.74f, cy - r * 0.44f),
                size = Size(r * 1.48f, r * 0.88f)
            )
        }
        val rFar = w * 0.042f
        val rNear = w * 0.055f
        pocket(clothFar, clothTop, rFar)
        pocket(w - clothFar, clothTop, rFar)
        pocket(w * 0.055f, clothBottom, rNear)
        pocket(w - w * 0.055f, clothBottom, rNear)
        pocket(w * 0.5f, clothTop + h * 0.005f, rFar * 0.92f)
        pocket(w * 0.5f, clothBottom, rNear * 0.92f)

        // Three balls, small, just enough to say what the table is for. Each gets a
        // highlight up and left, which is the whole trick for making a disc look round.
        fun ball(cx: Float, cy: Float, r: Float, c: Color) {
            drawOval(
                color = Color(0x44000000),
                topLeft = Offset(cx - r, cy - r * 0.2f),
                size = Size(r * 2.1f, r * 0.8f)
            )
            drawCircle(c, radius = r, center = Offset(cx, cy))
            drawCircle(
                Color(0x66FFFFFF),
                radius = r * 0.34f,
                center = Offset(cx - r * 0.33f, cy - r * 0.36f)
            )
        }
        val by = clothTop + (clothBottom - clothTop) * 0.52f
        ball(w * 0.40f, by, w * 0.030f, Color(0xFFE8E4DC))
        ball(w * 0.55f, by + h * 0.05f, w * 0.030f, Color(0xFFD4453A))
        ball(w * 0.63f, by - h * 0.03f, w * 0.030f, Color(0xFF14171B))
    }
}

