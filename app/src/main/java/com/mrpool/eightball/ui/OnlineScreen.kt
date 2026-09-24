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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrpool.eightball.net.Matchmaking
import com.mrpool.eightball.net.RoomCode

/**
 * Getting into an online match: a quick game against whoever is waiting, or a private room
 * whose code you read out to a friend.
 */
@Composable
fun OnlineScreen(
    coins: Int,
    playerName: String,
    state: Matchmaking,
    configured: Boolean,
    setupHint: String,
    onQuickMatch: () -> Unit,
    onHost: () -> Unit,
    onJoin: (String) -> Unit,
    onCancel: () -> Unit,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    val busy = state is Matchmaking.Hosting ||
        state is Matchmaking.Searching ||
        state is Matchmaking.Connecting

    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Play Online",
                subtitle = "Take on a real player",
                coins = coins,
                onBack = {
                    onCancel()
                    onBack()
                }
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!configured) {
                    Notice(setupHint, Crimson)
                }

                OutlinedTextField(
                    value = playerName,
                    onValueChange = onNameChange,
                    label = { Text("Your name", color = Chalk.copy(alpha = 0.6f)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Chalk,
                        unfocusedTextColor = Chalk,
                        focusedContainerColor = InkSoft,
                        unfocusedContainerColor = InkSoft,
                        cursorColor = Gold,
                        focusedIndicatorColor = Gold,
                        unfocusedIndicatorColor = Chalk.copy(alpha = 0.2f)
                    )
                )

                when (state) {
                    is Matchmaking.Hosting -> HostingCard(state.code, onCancel)
                    is Matchmaking.Searching -> Notice("Looking for an opponent…", Gold)
                    // Says a minute out loud, because it can be. The server sleeps when
                    // nobody is playing, and the app keeps asking while it wakes rather
                    // than reporting a failure it would have recovered from -- so a wait
                    // here is normal and a silent spinner would look like a broken game.
                    is Matchmaking.Connecting -> Notice(
                        "Reaching the match server…\nThe first connect after a quiet spell " +
                            "can take up to a minute.",
                        Cyan
                    )
                    is Matchmaking.Failed -> Notice(state.reason, Crimson)
                    else -> Unit
                }

                if (!busy) {
                    MenuTile(
                        title = "Quick Match",
                        subtitle = "Play whoever is waiting",
                        icon = Icons.Filled.Bolt,
                        accent = Gold,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = configured,
                        onClick = onQuickMatch
                    )
                    MenuTile(
                        title = "Create a Room",
                        subtitle = "Get a code and read it to a friend",
                        icon = Icons.Filled.MeetingRoom,
                        accent = Cyan,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = configured,
                        onClick = onHost
                    )

                    Text(
                        "JOIN WITH A CODE",
                        style = MaterialTheme.typography.labelLarge,
                        color = Chalk.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = RoomCode.normalise(it) },
                            placeholder = {
                                Text("ABCDE", color = Chalk.copy(alpha = 0.35f), letterSpacing = 6.sp)
                            },
                            singleLine = true,
                            enabled = configured,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Gold,
                                unfocusedTextColor = Gold,
                                focusedContainerColor = InkSoft,
                                unfocusedContainerColor = InkSoft,
                                cursorColor = Gold,
                                focusedIndicatorColor = Gold,
                                unfocusedIndicatorColor = Chalk.copy(alpha = 0.2f)
                            )
                        )
                        Button(
                            onClick = { onJoin(codeInput) },
                            enabled = configured && RoomCode.isValid(codeInput),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Join", color = Ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Notice(
                    "Both players run the same simulation, so a match costs only a few " +
                        "hundred bytes. Play on mobile data without a second thought.",
                    Chalk.copy(alpha = 0.35f)
                )
                Box(modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

/** The room code, big enough to read out over a call. */
@Composable
private fun HostingCard(code: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(InkSoft)
            .border(BorderStroke(1.dp, Gold), RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "YOUR ROOM CODE",
            style = MaterialTheme.typography.labelSmall,
            color = Chalk.copy(alpha = 0.5f)
        )
        Text(
            code,
            color = Gold,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 12.sp,
            textAlign = TextAlign.Center
        )
        Text(
            "Waiting for your friend to join…",
            style = MaterialTheme.typography.bodyMedium,
            color = Chalk.copy(alpha = 0.6f)
        )
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3134)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Text("Cancel", color = Chalk)
        }
    }
}

@Composable
private fun Notice(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkSoft)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.4f)), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = accent)
    }
}
