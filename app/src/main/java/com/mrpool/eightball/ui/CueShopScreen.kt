package com.mrpool.eightball.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

/**
 * The cue rack: twelve cues, the first one free and the rest priced from 200 coins up,
 * followed by the two that come with a subscription and are not for sale.
 */
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
                subtitle = if (profile.pro) "All ${CueStick.EVERY.size} unlocked with Pro"
                else "${profile.ownedCueIds.size} of ${CueStick.ALL.size} unlocked",
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
                items(CueStick.EVERY, key = { it.id }) { cue ->
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
                // Not for sale at any price, so it gets a label rather than a dead button.
                !owned && cue.proOnly -> Text(
                    "PRO ONLY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8C6BFF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF8C6BFF).copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF6FA8DC))
        )
        Box(
            modifier = Modifier
                .weight(1.4f)
                .height(7.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(cue.shaftColor.toInt()), Color(cue.shaftColor.toInt()))
                    )
                )
        )
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(9.dp)
                .background(Color(cue.accentColor.toInt()))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(11.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(cue.buttColor.toInt()), Color(cue.accentColor.toInt()))
                    )
                )
        )
        Box(modifier = Modifier.size(4.dp))
    }
}
