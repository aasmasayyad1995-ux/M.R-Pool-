package com.mrpool.eightball.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
                subtitle = if (profile.pro) "All ${PoolTableSkin.EVERY.size} unlocked with Pro"
                else "${profile.ownedTableIds.size} of ${PoolTableSkin.ALL.size} unlocked",
                coins = profile.coins,
                onBack = onBack
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(PoolTableSkin.EVERY, key = { it.id }) { table ->
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
            // Not for sale at any price, so it gets a label rather than a dead button.
            !owned && table.proOnly -> Text(
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
    Box(
        modifier = Modifier
            .size(width = 86.dp, height = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(table.railColor.toInt()))
            .padding(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(table.feltColor.toInt()))
        ) {
            val pocket = Color(0xFF05070A)
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(pocket)
                    .align(Alignment.TopStart)
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(pocket)
                    .align(Alignment.TopEnd)
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(pocket)
                    .align(Alignment.BottomStart)
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(pocket)
                    .align(Alignment.BottomEnd)
            )
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(pocket)
                    .align(Alignment.TopCenter)
            )
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(pocket)
                    .align(Alignment.BottomCenter)
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(table.trimColor.toInt()))
                    .align(Alignment.Center)
            )
        }
    }
}
