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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.mrpool.eightball.billing.BillingConfig
import com.mrpool.eightball.billing.SubscriptionPlan
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.PoolTableSkin
import java.text.DateFormat
import java.util.Date

private val ProViolet = Color(0xFF8C6BFF)

/** What a subscription is worth, said once so the screen and the tests agree. */
val PRO_BENEFITS: List<Pair<String, String>> = listOf(
    "Every cue and every table" to
        "All ${CueStick.ALL.size} cues and all ${PoolTableSkin.ALL.size} tables unlocked " +
            "for as long as the subscription runs.",
    "${PlayerProfile.PRO_DAILY_BONUS} coins a day" to
        "The daily bonus goes from ${PlayerProfile.DAILY_BONUS} to " +
            "${PlayerProfile.PRO_DAILY_BONUS}.",
    "Double prize money" to
        "Beating the robots pays 50, 100 and 200 instead of 25, 50 and 100.",
    "Subscriber cues and tables" to
        "${CueStick.PRO_ONLY.joinToString(" and ") { it.name }}, and the " +
            "${PoolTableSkin.PRO_ONLY.joinToString(" and ") { it.name }} tables. " +
            "Not for sale at any price.",
    "A star beside your name" to
        "Online opponents see it. That is all it does."
)

/**
 * Mr. Pool Pro.
 *
 * The paying happens on the payment provider's own page, in the phone's browser — this
 * screen only says what the subscription is, opens that page, and reports what the server
 * says came back. No card or UPI id is ever typed into this game.
 */
@Composable
fun SubscriptionScreen(
    profile: PlayerProfile,
    plan: SubscriptionPlan,
    busy: Boolean,
    notice: String?,
    onSubscribe: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    PoolBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Mr. Pool Pro",
                subtitle = if (profile.pro) "Subscription active" else "A monthly subscription",
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1B1235), Color(0xFF241A44))
                            )
                        )
                        .border(BorderStroke(1.dp, ProViolet.copy(alpha = 0.5f)), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(ProViolet.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("★", color = ProViolet, fontSize = 24.sp)
                    }
                    Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(
                            if (profile.pro) "You are a subscriber" else "Mr. Pool Pro",
                            style = MaterialTheme.typography.titleMedium,
                            color = Chalk,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            proStatusLine(profile, plan),
                            style = MaterialTheme.typography.labelSmall,
                            color = Chalk.copy(alpha = 0.65f)
                        )
                    }
                }

                for ((title, detail) in PRO_BENEFITS) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(InkSoft)
                            .padding(14.dp)
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (profile.pro) ProViolet else Chalk,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Chalk.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (notice != null) {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = Gold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                when {
                    !BillingConfig.isConfigured || !plan.configured -> {
                        Text(
                            if (!BillingConfig.isConfigured) BillingConfig.SETUP_HINT
                            else "This server is not set up to take payments yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Chalk.copy(alpha = 0.6f)
                        )
                    }

                    busy -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ProViolet)
                        }
                    }

                    profile.pro -> {
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Check my subscription") }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel renewal") }
                        Text(
                            "Cancelling stops the next payment. Everything above stays " +
                                "yours until the month you have paid for runs out.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Chalk.copy(alpha = 0.5f)
                        )
                    }

                    else -> {
                        Button(
                            onClick = onSubscribe,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ProViolet)
                        ) {
                            Text(
                                if (plan.price.isBlank()) "Subscribe"
                                else "Subscribe · ${plan.price}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("I have already paid") }
                        Text(
                            "Payment opens in your browser, on the payment provider's own " +
                                "page. This game never sees your card or UPI details. " +
                                "Cancel any time.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Chalk.copy(alpha = 0.5f)
                        )
                    }
                }

                Text(
                    "The subscription is tied to this installation. There are no accounts " +
                        "in this game yet, so clearing the app's data or moving to another " +
                        "phone will not carry it across.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Chalk.copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}

/** One line saying exactly where the subscription stands. */
private fun proStatusLine(profile: PlayerProfile, plan: SubscriptionPlan): String = when {
    profile.pro -> {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(Date(profile.proUntilMillis))
        "Runs until $date"
    }

    profile.proUntilMillis > 0L -> "Your subscription has ended"
    plan.price.isNotBlank() -> "${plan.price}, cancel any time"
    else -> "Unlock everything, every day"
}
