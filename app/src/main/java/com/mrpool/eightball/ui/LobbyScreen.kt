package com.mrpool.eightball.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrpool.eightball.BuildConfig
import com.mrpool.eightball.data.PlayerProfile

/** The lobby: every mode and every shop hangs off this screen. */
@Composable
fun LobbyScreen(
    profile: PlayerProfile,
    onPlayRobot: () -> Unit,
    onPlayFriend: () -> Unit,
    onPlayOnline: () -> Unit,
    onChooseCue: () -> Unit,
    onChooseTable: () -> Unit,
    onHowToPlay: () -> Unit,
    onWallet: () -> Unit,
    onToggleSound: () -> Unit
) {
    PoolBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF14181C), Color(0xFF2B3238)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("8", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        "MR. POOL",
                        style = MaterialTheme.typography.displayLarge,
                        color = Gold,
                        fontSize = 30.sp
                    )
                    Text(
                        "8 Ball Pool · 3D Edition",
                        style = MaterialTheme.typography.labelSmall,
                        color = Chalk.copy(alpha = 0.6f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(InkSoft)
                        .clickable { onToggleSound() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (profile.soundEnabled) Icons.Filled.VolumeUp
                        else Icons.Filled.VolumeOff,
                        contentDescription = if (profile.soundEnabled) "Turn sound off"
                        else "Turn sound on",
                        tint = if (profile.soundEnabled) Gold else Chalk.copy(alpha = 0.4f)
                    )
                }
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    CoinPill(profile.coins, onClick = onWallet)
                }
            }

            StatsStrip(profile)

            Text(
                "PLAY",
                style = MaterialTheme.typography.labelLarge,
                color = Chalk.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 6.dp)
            )
            MenuTile(
                title = "Play with Robot",
                subtitle = "Beginner · Medium · Hard",
                icon = Icons.Filled.SmartToy,
                accent = Gold,
                modifier = Modifier.fillMaxWidth(),
                badge = "WIN COINS",
                onClick = onPlayRobot
            )
            MenuTile(
                title = "Play Online",
                subtitle = "Real opponents, quick match or a room code",
                icon = Icons.Filled.Public,
                accent = Color(0xFF6FA8FF),
                modifier = Modifier.fillMaxWidth(),
                badge = "NEW",
                onClick = onPlayOnline
            )
            MenuTile(
                title = "Play with Friend",
                subtitle = "Two players, one device, no entry fee",
                icon = Icons.Filled.Groups,
                accent = Cyan,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPlayFriend
            )

            Text(
                "COLLECTION",
                style = MaterialTheme.typography.labelLarge,
                color = Chalk.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 10.dp)
            )
            MenuTile(
                title = "Choose Cue Stick",
                subtitle = "${profile.ownedCueIds.size} of 12 unlocked · ${profile.equippedCue.name}",
                icon = Icons.Filled.Straighten,
                accent = Color(0xFFE0A050),
                modifier = Modifier.fillMaxWidth(),
                onClick = onChooseCue
            )
            MenuTile(
                title = "Table Selection",
                subtitle = "${profile.ownedTableIds.size} of 20 unlocked · ${profile.equippedTable.name}",
                icon = Icons.Filled.GridView,
                accent = Color(0xFF4CC38A),
                modifier = Modifier.fillMaxWidth(),
                onClick = onChooseTable
            )

            Text(
                "MORE",
                style = MaterialTheme.typography.labelLarge,
                color = Chalk.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 10.dp)
            )
            MenuTile(
                title = "Coins & Rewards",
                subtitle = "Daily bonus and what each robot pays",
                icon = Icons.Filled.Paid,
                accent = Gold,
                modifier = Modifier.fillMaxWidth(),
                onClick = onWallet
            )
            MenuTile(
                title = "How to Play Pool",
                subtitle = "Rules, controls, spin and winning tactics",
                icon = Icons.Filled.School,
                accent = Color(0xFF9C8CFF),
                modifier = Modifier.fillMaxWidth(),
                onClick = onHowToPlay
            )

            // Which build this is. Small, but it turns "the fix did not work" into a
            // question that answers itself from a screenshot.
            Text(
                "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_ID}",
                style = MaterialTheme.typography.labelSmall,
                color = Chalk.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 14.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun StatsStrip(profile: PlayerProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InkSoft)
            .border(BorderStroke(1.dp, Chalk.copy(alpha = 0.10f)), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Stat("WINS", profile.wins.toString(), Color(0xFF4CC38A))
        Stat("LOSSES", profile.losses.toString(), Crimson)
        Stat("BEST RUN", profile.bestWinStreak.toString(), Gold)
        Stat("PLAYED", profile.matchesPlayed.toString(), Chalk)
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Chalk.copy(alpha = 0.45f))
    }
}
