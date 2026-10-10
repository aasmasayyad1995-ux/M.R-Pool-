package com.mrpool.eightball.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var previewId by remember { mutableStateOf(profile.equippedCue.id) }
    val preview = CueStick.ALL.firstOrNull { it.id == previewId } ?: profile.equippedCue

    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Choose Cue Stick",
                subtitle = "${profile.ownedCueIds.size} of ${CueStick.ALL.size} unlocked",
                coins = profile.coins,
                onBack = onBack
            )
            CuePreviewPanel(
                cue = preview,
                owned = profile.owns(preview),
                equipped = profile.equippedCue.id == preview.id,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(CueStick.ALL, key = { it.id }) { cue ->
                    CueCard(
                        cue = cue,
                        owned = profile.owns(cue),
                        equipped = profile.equippedCue.id == cue.id,
                        selected = cue.id == preview.id,
                        affordable = profile.coins >= cue.price,
                        onSelect = { previewId = cue.id },
                        onBuy = { previewId = cue.id; onBuy(cue) },
                        onEquip = { previewId = cue.id; onEquip(cue) }
                    )
                }
            }
        }
    }
}

/** The rack light above the list: one cue, drawn the length of the screen. */
@Composable
private fun CuePreviewPanel(
    cue: CueStick,
    owned: Boolean,
    equipped: Boolean,
    modifier: Modifier = Modifier
) {
    val shaft = Color(cue.shaftColor.toInt())
    val butt = Color(cue.buttColor.toInt())
    val accent = Color(cue.accentColor.toInt())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Ink, accent.darken(0.74f), InkSoft)))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.45f)), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cue.name, style = MaterialTheme.typography.headlineMedium, color = Chalk)
                Text(
                    cue.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Chalk.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                StarRating("POWER", cue.powerStars)
                StarRating("AIM  ", cue.aimStars)
                StarRating("SPIN ", cue.spinStars)
            }
        }
        // A cue is fifty times longer than it is thick, so drawn honestly across a phone
        // held sideways it is a very thin thing. The lit shelf behind it is what stops it
        // looking like a scratch on the screen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF), Color(0x00FFFFFF))
                    )
                )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
            ) {
                drawCueStick(shaft = shaft, butt = butt, accent = accent, detailed = true)
            }
        }
        Text(
            when {
                equipped -> "In your hands right now"
                owned -> "Unlocked · tap Equip to use it"
                cue.isFree -> "Free · yours already"
                else -> "${formatCoins(cue.price)} coins to unlock · tap any cue below"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (equipped) Gold else Chalk.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CueCard(
    cue: CueStick,
    owned: Boolean,
    equipped: Boolean,
    selected: Boolean,
    affordable: Boolean,
    onSelect: () -> Unit,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    val accent = Color(cue.accentColor.toInt())
    val outline = when {
        equipped -> Gold
        selected -> Chalk.copy(alpha = 0.45f)
        else -> accent.copy(alpha = 0.35f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(InkSoft)
            .border(
                BorderStroke(if (equipped || selected) 2.dp else 1.dp, outline),
                RoundedCornerShape(18.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 128.dp, height = 28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ink)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                drawCueStick(
                    shaft = Color(cue.shaftColor.toInt()),
                    butt = Color(cue.buttColor.toInt()),
                    accent = accent,
                    detailed = false
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(cue.name, style = MaterialTheme.typography.titleMedium, color = Chalk)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StarRating("PWR", cue.powerStars)
                StarRating("AIM", cue.aimStars)
                StarRating("SPN", cue.spinStars)
            }
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
            ) { Text("Equip", color = Chalk) }
            else -> Button(
                onClick = onBuy,
                enabled = affordable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (affordable) Gold else Color(0xFF32383B)
                )
            ) {
                Text(
                    formatCoins(cue.price),
                    color = if (affordable) Ink else Chalk.copy(alpha = 0.5f)
                )
            }
        }
    }
}
