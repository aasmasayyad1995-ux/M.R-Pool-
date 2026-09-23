package com.mrpool.eightball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown instead of the game whenever the phone has no connection.
 *
 * Mr. Pool needs a connection to be played, so this is not a warning laid over the game —
 * it *is* the screen. There is nothing behind it to go back to, which is why it offers a
 * retry and no way past.
 */
@Composable
fun OfflineScreen(onRetry: () -> Unit) {
    PoolBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Crimson.copy(alpha = 0.14f))
                    .border(2.dp, Crimson.copy(alpha = 0.6f), CircleShape)
                    .padding(26.dp)
            ) {
                Text("📶", fontSize = 40.sp)
            }

            Text(
                "No internet",
                color = Chalk,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                "Mr. Pool needs a connection to play.\nTurn on mobile data or wifi and try again.",
                color = Chalk.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(top = 24.dp).width(200.dp)
            ) {
                Text("Try again", color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Shown over a match that has lost its connection.
 *
 * One button, and it says OK. A match whose connection has gone cannot be picked up where
 * it stopped — online the other player is already gone, and offline the game is not meant
 * to be played at all — so a second button would have to promise something that does not
 * work.
 */
@Composable
fun MatchEndedOfflineDialog(onOk: () -> Unit) {
    DimScrim {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(InkSoft)
                .border(1.dp, Crimson, RoundedCornerShape(22.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Connection lost",
                color = Crimson,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "The match cannot continue without a connection.",
                color = Chalk.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onOk,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text("OK", color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}
