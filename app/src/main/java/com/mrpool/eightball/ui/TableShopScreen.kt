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
    // Which table the panel at the top is showing. It starts on the table being played
    // and follows whatever the player last touched, so the big picture is always of the
    // table they are thinking about rather than the one they already own.
    var previewId by remember { mutableStateOf(profile.equippedTable.id) }
    val preview = PoolTableSkin.ALL.firstOrNull { it.id == previewId } ?: profile.equippedTable

    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Table Selection",
                subtitle = "${profile.ownedTableIds.size} of ${PoolTableSkin.ALL.size} unlocked",
                coins = profile.coins,
                onBack = onBack
            )
            TablePreviewPanel(
                table = preview,
                owned = profile.owns(preview),
                equipped = profile.equippedTable.id == preview.id,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(PoolTableSkin.ALL, key = { it.id }) { table ->
                    TableCard(
                        table = table,
                        owned = profile.owns(table),
                        equipped = profile.equippedTable.id == table.id,
                        selected = table.id == preview.id,
                        affordable = profile.coins >= table.price,
                        onSelect = { previewId = table.id },
                        onBuy = { previewId = table.id; onBuy(table) },
                        onEquip = { previewId = table.id; onEquip(table) }
                    )
                }
            }
        }
    }
}

/**
 * The showcase above the list: one table, drawn big enough to be worth wanting.
 *
 * The thumbnail in a row has to survive being 84dp wide, so it can only ever be a hint.
 * This is where the table is actually sold.
 */
@Composable
private fun TablePreviewPanel(
    table: PoolTableSkin,
    owned: Boolean,
    equipped: Boolean,
    modifier: Modifier = Modifier
) {
    val felt = Color(table.feltColor.toInt())
    val rail = Color(table.railColor.toInt())
    val trim = Color(table.trimColor.toInt())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(listOf(felt.darken(0.76f), Ink, InkSoft))
            )
            .border(BorderStroke(1.dp, trim.copy(alpha = 0.45f)), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 240.dp, height = 104.dp)) {
            drawPoolTable(felt = felt, rail = rail, trim = trim, detailed = true)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(table.name, style = MaterialTheme.typography.headlineMedium, color = Chalk)
            Text(
                "${table.venue} · ${table.clothSpeed.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = Chalk.copy(alpha = 0.6f)
            )
            Text(
                when {
                    equipped -> "You are playing on this table"
                    owned -> "Unlocked · tap Play to use it"
                    table.isFree -> "Free · plays slow and forgiving"
                    else -> "${formatCoins(table.price)} coins to unlock"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (equipped) Gold else Chalk.copy(alpha = 0.85f)
            )
            Text(
                "Tap any table below to see it here",
                style = MaterialTheme.typography.labelSmall,
                color = Chalk.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TableCard(
    table: PoolTableSkin,
    owned: Boolean,
    equipped: Boolean,
    selected: Boolean,
    affordable: Boolean,
    onSelect: () -> Unit,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    val outline = when {
        equipped -> Gold
        selected -> Chalk.copy(alpha = 0.45f)
        else -> Chalk.copy(alpha = 0.12f)
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
                .size(width = 104.dp, height = 52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ink)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp)
            ) {
                drawPoolTable(
                    felt = Color(table.feltColor.toInt()),
                    rail = Color(table.railColor.toInt()),
                    trim = Color(table.trimColor.toInt()),
                    detailed = false
                )
            }
        }
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
