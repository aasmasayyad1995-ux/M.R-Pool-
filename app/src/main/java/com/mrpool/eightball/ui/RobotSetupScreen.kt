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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.data.PlayerProfile

/** Difficulty picker, showing what beating each robot pays. */
@Composable
fun RobotSetupScreen(
    profile: PlayerProfile,
    onBack: () -> Unit,
    onStart: (RobotDifficulty) -> Unit
) {
    var selected by remember { mutableStateOf(RobotDifficulty.MEDIUM) }
    val table = profile.equippedTable
    val prize = profile.prizeFor(selected)

    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Play with Robot",
                subtitle = "${table.name} · ${table.clothSpeed.label}",
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
                RobotDifficulty.entries.forEach { difficulty ->
                    DifficultyCard(
                        difficulty = difficulty,
                        selected = difficulty == selected,
                        prize = profile.prizeFor(difficulty),
                        onSelect = { selected = difficulty }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(InkSoft)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "ENTRY FEE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Chalk.copy(alpha = 0.5f)
                        )
                        Text(
                            "FREE",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF4CC38A)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "WIN AND TAKE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Chalk.copy(alpha = 0.5f)
                        )
                        Text(
                            formatCoins(prize),
                            style = MaterialTheme.typography.titleLarge,
                            color = Gold
                        )
                    }
                }

                Button(
                    onClick = { onStart(selected) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "Break them — win ${formatCoins(prize)}",
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyCard(
    difficulty: RobotDifficulty,
    selected: Boolean,
    prize: Int,
    onSelect: () -> Unit
) {
    val accent = when (difficulty) {
        RobotDifficulty.BEGINNER -> Color(0xFF4CC38A)
        RobotDifficulty.MEDIUM -> Color(0xFFE0A050)
        RobotDifficulty.HARD -> Crimson
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else InkSoft)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, accent.copy(alpha = if (selected) 1f else 0.3f)),
                RoundedCornerShape(18.dp)
            )
            .clickable { onSelect() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (difficulty) {
                    RobotDifficulty.BEGINNER -> "★"
                    RobotDifficulty.MEDIUM -> "★★"
                    RobotDifficulty.HARD -> "★★★"
                },
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(difficulty.label, style = MaterialTheme.typography.titleLarge, color = Chalk)
            Text(
                difficulty.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = Chalk.copy(alpha = 0.6f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatCoins(prize),
                style = MaterialTheme.typography.titleLarge,
                color = Gold
            )
            Text(
                "PER WIN",
                style = MaterialTheme.typography.labelSmall,
                color = Chalk.copy(alpha = 0.45f)
            )
        }
    }
}
