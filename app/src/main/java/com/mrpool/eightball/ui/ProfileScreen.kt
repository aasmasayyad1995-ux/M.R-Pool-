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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.mrpool.eightball.data.PlayerProfile

/**
 * The player: their picture, their name and what they have done with them.
 *
 * The picture never leaves the phone. Online opponents see the name and nothing else —
 * sending a picture to a stranger is a different thing to build, with moderation behind
 * it, and this is not that.
 */
@Composable
fun ProfileScreen(
    profile: PlayerProfile,
    onPickPicture: () -> Unit,
    onRemovePicture: () -> Unit,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember(profile.playerName) { mutableStateOf(profile.playerName) }

    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Profile",
                subtitle = "Your picture, your name, your record",
                coins = profile.coins,
                onBack = onBack
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(
                        stamp = profile.avatarStamp,
                        size = 132.dp,
                        ring = Gold,
                        onClick = onPickPicture
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Gold)
                            .border(BorderStroke(3.dp, Ink), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✎", color = Ink, fontSize = 17.sp)
                    }
                }

                Text(
                    "Tap the picture to choose one from your phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = Chalk.copy(alpha = 0.55f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPickPicture,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold)
                    ) { Text("Choose picture", color = Ink, fontWeight = FontWeight.Bold) }
                    if (profile.avatarStamp > 0L) {
                        OutlinedButton(onClick = onRemovePicture) { Text("Remove") }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(16)
                        onNameChange(name)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    singleLine = true,
                    label = { Text("Your name") },
                    supportingText = {
                        Text(
                            "This is what an online opponent sees. Your picture stays on " +
                                "this phone.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(InkSoft)
                        .border(
                            BorderStroke(1.dp, Chalk.copy(alpha = 0.10f)),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "RECORD",
                        style = MaterialTheme.typography.labelLarge,
                        color = Chalk.copy(alpha = 0.45f)
                    )
                    ProfileRow("Matches played", profile.matchesPlayed.toString(), Chalk)
                    ProfileRow("Wins", profile.wins.toString(), Color(0xFF4CC38A))
                    ProfileRow("Losses", profile.losses.toString(), Crimson)
                    ProfileRow("Best run", profile.bestWinStreak.toString(), Gold)
                    ProfileRow("On a run of", profile.currentWinStreak.toString(), Gold)
                    ProfileRow(
                        "Cues unlocked",
                        "${profile.ownedCueIds.size} of 12",
                        Color(0xFFE0A050)
                    )
                    ProfileRow(
                        "Tables unlocked",
                        "${profile.ownedTableIds.size} of 20",
                        Color(0xFF4CC38A)
                    )
                }

                Text(
                    "Everything here is kept on this phone only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Chalk.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Chalk.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
