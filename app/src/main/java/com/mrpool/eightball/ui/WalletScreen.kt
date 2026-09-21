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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.data.PlayerProfile

/**
 * The money screen: balance, the daily bonus, and exactly what every match pays.
 *
 * Coins are in-game currency. They are won, never bought: the subscription unlocks cues,
 * tables and bigger payouts, and there is no way to turn coins back into money.
 */
@Composable
fun WalletScreen(
    profile: PlayerProfile,
    bonusAvailable: Boolean,
    onClaimBonus: () -> Unit,
    onBack: () -> Unit
) {
    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Coins & Rewards",
                subtitle = "Won by playing — coins are never bought or cashed out",
                coins = profile.coins,
                onBack = onBack
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(InkSoft)
                        .border(BorderStroke(1.dp, GoldDim), RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "BALANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Chalk.copy(alpha = 0.5f)
                    )
                    Text(
                        profile.coins.toString(),
                        color = Gold,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Win streak ${profile.currentWinStreak} · best ${profile.bestWinStreak}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Chalk.copy(alpha = 0.6f)
                    )
                }

                Button(
                    onClick = onClaimBonus,
                    enabled = bonusAvailable,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (bonusAvailable) Gold else Color(0xFF32383B)
                    )
                ) {
                    Text(
                        if (bonusAvailable) "Claim daily bonus +${profile.dailyBonus}"
                        else "Daily bonus claimed — come back tomorrow",
                        color = if (bonusAvailable) Ink else Chalk.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                SectionTitle("How you earn")
                InfoRow("Beat the beginner robot", "+${profile.prizeFor(RobotDifficulty.BEGINNER)}")
                InfoRow("Beat the medium robot", "+${profile.prizeFor(RobotDifficulty.MEDIUM)}")
                InfoRow("Beat the hard robot", "+${profile.prizeFor(RobotDifficulty.HARD)}")
                InfoRow("Daily bonus", "+${profile.dailyBonus} once a day")
                if (profile.pro) {
                    InfoRow("Mr. Pool Pro", "doubling every prize above")
                }

                SectionTitle("How you spend")
                InfoRow("Cue sticks", "From 200 coins")
                InfoRow("Tables", "From 1,000 coins")

                SectionTitle("What is free")
                InfoRow("Playing the robot", "No entry fee")
                InfoRow("Playing a friend", "No entry fee")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF14221F))
                        .padding(14.dp)
                ) {
                    Text(
                        "Every match is free to play. What you win depends only on which " +
                            "robot you beat, so pick the toughest one you can handle — and " +
                            "playing on ${profile.equippedTable.name} costs you nothing extra.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Chalk.copy(alpha = 0.75f)
                    )
                }

                Box(modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Chalk.copy(alpha = 0.45f),
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InkSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Chalk)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Gold)
    }
}
