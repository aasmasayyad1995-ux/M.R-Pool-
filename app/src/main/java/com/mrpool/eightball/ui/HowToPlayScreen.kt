package com.mrpool.eightball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private data class Lesson(val title: String, val body: String, val bullets: List<String> = emptyList())

private val LESSONS = listOf(
    Lesson(
        "The goal",
        "8 ball pool is played with a cue ball and fifteen numbered balls. One player takes " +
            "the solids (1 to 7), the other takes the stripes (9 to 15). Pot your whole group, " +
            "then pot the 8 ball to win the game."
    ),
    Lesson(
        "The break",
        "The game starts with a break from behind the head string. The table stays open after " +
            "the break: whatever drops does not decide your group yet.",
        listOf(
            "Four balls must reach a cushion, or a ball must be potted, or it is a foul.",
            "Pot the 8 on the break and it is simply re-spotted in this game — you carry on."
        )
    ),
    Lesson(
        "Choosing your group",
        "After the break the first player to legally pot a ball takes that group. From then on " +
            "you must hit one of your own balls first on every shot."
    ),
    Lesson(
        "Fouls and ball in hand",
        "A foul hands your opponent the cue ball to place anywhere on the table. You foul when:",
        listOf(
            "The cue ball is potted (a scratch).",
            "You hit none of your own balls first.",
            "Nothing is potted and no ball reaches a cushion after contact.",
            "You pot the 8 ball before clearing your group — that loses the game outright."
        )
    ),
    Lesson(
        "Winning on the 8",
        "Once your group is gone you are on the 8 ball. Pot it cleanly and the game is yours. " +
            "Scratch on the 8, or pot it along with your last group ball, and you lose."
    ),
    Lesson(
        "Aiming",
        "Drag anywhere on the table to swing the cue. The dashed white line shows where the cue " +
            "ball travels, the ghost ball shows the contact point, the yellow line shows where the " +
            "object ball will go and the blue line shows where your cue ball goes afterwards.",
        listOf(
            "Use the fine tune arrows for the last half degree on long pots.",
            "A better cue draws a longer guide line — that is what the aim rating buys you."
        )
    ),
    Lesson(
        "Power",
        "Drag the power bar up for a harder hit. Most pots need far less power than players use; " +
            "a soft shot keeps the cue ball under control and stops it running into trouble."
    ),
    Lesson(
        "Spin: follow, draw and english",
        "Tap the white ball icon to open the spin pad and drag the tip mark off centre.",
        listOf(
            "Top (follow): the cue ball rolls forward after contact.",
            "Bottom (draw): the cue ball comes back towards you.",
            "Left or right (english): the cue ball spins sideways and throws off the cushion.",
            "Centre ball is the safest hit, and stun is the easiest to predict."
        )
    ),
    Lesson(
        "Position play",
        "The shot you take decides the shot you get. Look at the blue tangent line before you " +
            "hit: if it sends the cue ball behind an opponent ball, pick a different speed or spin."
    ),
    Lesson(
        "Safety play",
        "When nothing is on, do not gamble. Roll up behind one of your own balls so your " +
            "opponent cannot see theirs. The hard robot does exactly this, and it wins games."
    ),
    Lesson(
        "Coins, stakes and prizes",
        "Every match against the robot has an entry fee set by the table you are playing on, and " +
            "the prize scales with the difficulty you choose.",
        listOf(
            "Beginner pays 1.5x the table prize, medium 2.5x and hard 4x.",
            "Cues start at 200 coins, tables at 1,000 coins.",
            "Claim the daily bonus from the Coins screen, and matches against a friend are free.",
            "Coins are in-game only — nothing here costs real money."
        )
    )
)

/** The rules and controls, written for someone who has never played pool. */
@Composable
fun HowToPlayScreen(coins: Int, onBack: () -> Unit) {
    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "How to Play Pool",
                subtitle = "Rules, controls and the tactics that win games",
                coins = coins,
                onBack = onBack
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(LESSONS) { lesson ->
                    LessonCard(lesson)
                }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InkSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(lesson.title, style = MaterialTheme.typography.titleLarge, color = Gold)
        Text(
            lesson.body,
            style = MaterialTheme.typography.bodyLarge,
            color = Chalk.copy(alpha = 0.82f)
        )
        lesson.bullets.forEach { bullet ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•  ", color = Cyan, style = MaterialTheme.typography.bodyLarge)
                Text(
                    bullet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Chalk.copy(alpha = 0.7f)
                )
            }
        }
    }
}
