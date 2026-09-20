package com.mrpool.eightball.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The dark felt gradient every screen sits on. */
@Composable
fun PoolBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Ink, FeltDeep, Ink))
            )
    ) {
        content()
    }
}

/** The coin balance, shown in the corner of every screen. */
@Composable
fun CoinPill(coins: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF1B2426))
            .border(BorderStroke(1.dp, GoldDim), RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Gold, Color(0xFFB8860B)))),
            contentAlignment = Alignment.Center
        ) {
            Text("₹", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Text(
            text = formatCoins(coins),
            color = Gold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

fun formatCoins(coins: Int): String = when {
    coins >= 1_000_000 -> String.format("%.1fM", coins / 1_000_000f)
    coins >= 10_000 -> String.format("%.1fK", coins / 1000f)
    else -> coins.toString()
}

/** A large tappable tile: the lobby is built out of these. */
@Composable
fun MenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = InkSoft),
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.55f else 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Chalk)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Chalk.copy(alpha = 0.6f)
                )
            }
            if (badge != null) {
                Text(
                    text = badge,
                    color = Ink,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Five dots, filled to show a cue's rating. */
@Composable
fun StarRating(label: String, filled: Int, modifier: Modifier = Modifier, max: Int = 5) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Chalk.copy(alpha = 0.55f),
            modifier = Modifier.padding(end = 2.dp)
        )
        repeat(max) { index ->
            Box(
                modifier = Modifier
                    .size(if (index < filled) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (index < filled) Gold else Chalk.copy(alpha = 0.18f))
            )
        }
    }
}

/** Section header used at the top of the shops and the rules screen. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    coins: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InkSoft)
                .border(BorderStroke(1.dp, Chalk.copy(alpha = 0.18f)), RoundedCornerShape(12.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("‹", color = Chalk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Chalk)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Chalk.copy(alpha = 0.55f)
            )
        }
        CoinPill(coins)
    }
}

/** Ball shaped preview used in the shop lists and the HUD. */
@Composable
fun BallChip(number: Int, size: androidx.compose.ui.unit.Dp = 26.dp) {
    val color = Color(com.mrpool.eightball.render.Textures.ballColor(number))
    val striped = number in 9..15
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (striped) Color.White else color),
        contentAlignment = Alignment.Center
    ) {
        if (striped) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(size * 0.52f)
                    .background(color)
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.52f)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                color = Ink,
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
