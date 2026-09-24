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
import com.mrpool.eightball.ads.AdPolicy
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.data.PlayerProfile

/**
 * The money screen: balance, the daily bonus, and exactly what every match pays.
 *
 * Coins are in-game currency only — there is no real money anywhere in this app.
 */
@Composable
fun WalletScreen(
    profile: PlayerProfile,
    bonusAvailable: Boolean,
    onClaimBonus: () -> Unit,
    /** How many rewarded ads the player may still watch today. */
    adRewardsLeft: Int,
    /** False while no rewarded ad has finished loading, or while one is on screen. */
    adRewardReady: Boolean,
    onWatchAd: () -> Unit,
    onBack: () -> Unit
) {
    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Coins & Rewards",
                subtitle = "In-game currency — nothing here costs real money",
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
                        if (bonusAvailable) "Claim daily bonus +${PlayerProfile.DAILY_BONUS}"
                        else "Daily bonus claimed — come back tomorrow",
                        color = if (bonusAvailable) Ink else Chalk.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                // Opt in, never in the way: a button the player presses, not an advert
                // that arrives on its own. The cap is shown on it rather than discovered
                // by pressing it and being told no.
                Button(
                    onClick = onWatchAd,
                    enabled = adRewardsLeft > 0 && adRewardReady,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (adRewardsLeft > 0 && adRewardReady) Cyan else Color(0xFF32383B)
                    )
                ) {
                    Text(
                        when {
                            adRewardsLeft <= 0 -> "No more ad rewards today"
                            !adRewardReady -> "Loading an ad…"
                            else -> "Watch an ad +${AdPolicy.REWARD_COINS} · $adRewardsLeft left today"
                        },
                        color = if (adRewardsLeft > 0 && adRewardReady) Ink
                        else Chalk.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                SectionTitle("How you earn")
                InfoRow("Beat the beginner robot", "+${RobotDifficulty.BEGINNER.reward}")
                InfoRow("Beat the medium robot", "+${RobotDifficulty.MEDIUM.reward}")
                InfoRow("Beat the hard robot", "+${RobotDifficulty.HARD.reward}")
                InfoRow("Daily bonus", "+${PlayerProfile.DAILY_BONUS} once a day")
                InfoRow(
                    "Watching an ad",
                    "+${AdPolicy.REWARD_COINS}, up to ${AdPolicy.REWARDS_PER_DAY} a day"
                )

                SectionTitle("How you spend")
                InfoRow("Cue sticks", "From 200 coins")
                InfoRow("Tables", "From 1,000 coins")

                SectionTitle("What is free")
                InfoRow("Playing the robot", "No entry fee")
                InfoRow("Playing a friend", "No entry fee")
                InfoRow("Every coin in the game", "Won or watched, never bought")

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
