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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrpool.eightball.billing.BillingConfig
import com.mrpool.eightball.billing.ClaimState
import com.mrpool.eightball.billing.PaymentInstructions
import com.mrpool.eightball.billing.SubscriptionPlan
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.PoolTableSkin
import java.text.DateFormat
import java.util.Date

private val ProViolet = Color(0xFF8C6BFF)

/** What a subscription is worth, said once so the screen and the tests agree. */
val PRO_BENEFITS: List<Pair<String, String>> = listOf(
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
 * Paying is a UPI transfer straight to the owner, with a reference in the note. Nothing
 * automatic can see that money arrive, so this screen is honest about the shape of it: pay,
 * say you have paid, and wait for the owner to check their bank. It never claims a
 * subscription the server has not confirmed.
 */
@Composable
fun SubscriptionScreen(
    profile: PlayerProfile,
    plan: SubscriptionPlan,
    payment: PaymentInstructions?,
    claimState: ClaimState?,
    busy: Boolean,
    notice: String?,
    onStartPayment: () -> Unit,
    onOpenUpiApp: (String) -> Unit,
    onSubmitClaim: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var utr by remember { mutableStateOf("") }

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
                            Brush.linearGradient(listOf(Color(0xFF1B1235), Color(0xFF241A44)))
                        )
                        .border(
                            BorderStroke(1.dp, ProViolet.copy(alpha = 0.5f)),
                            RoundedCornerShape(18.dp)
                        )
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
                            proStatusLine(profile, plan, claimState),
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

                Text(
                    "The shop is untouched: every cue and table in it is still earned with " +
                        "coins, by subscribers and everyone else alike. Pro buys no " +
                        "advantage at the table.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Chalk.copy(alpha = 0.5f)
                )

                if (notice != null) {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = Gold
                    )
                }

                when {
                    !BillingConfig.isConfigured || !plan.configured -> Text(
                        if (!BillingConfig.isConfigured) BillingConfig.SETUP_HINT
                        else "This server is not set up to take payments yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Chalk.copy(alpha = 0.6f)
                    )

                    busy -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = ProViolet) }

                    profile.pro -> {
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                            Text("Check my subscription")
                        }
                        Text(
                            "Nothing renews by itself. When the month runs out, pay again " +
                                "the same way and it carries on.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Chalk.copy(alpha = 0.5f)
                        )
                    }

                    claimState == ClaimState.SUBMITTED -> {
                        Text(
                            "Waiting for your payment to be checked. This is done by hand, " +
                                "so give it a little while.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Chalk.copy(alpha = 0.75f)
                        )
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                            Text("Check again")
                        }
                    }

                    payment != null -> PaymentSteps(
                        payment = payment,
                        rejected = claimState == ClaimState.REJECTED,
                        utr = utr,
                        onUtrChange = { utr = it.take(24) },
                        onOpenUpiApp = { onOpenUpiApp(payment.payLink) },
                        onSubmitClaim = { onSubmitClaim(utr) }
                    )

                    else -> {
                        Button(
                            onClick = onStartPayment,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ProViolet)
                        ) {
                            Text(
                                if (plan.price.isBlank()) "Subscribe"
                                else "Subscribe · ${plan.price}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                            Text("I have already paid")
                        }
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

/**
 * Pay by UPI, then say so.
 *
 * The reference is the load bearing part: it is the only thing tying a line in somebody's
 * bank statement back to this player, so it is shown large, repeated in the note of the
 * UPI link, and asked for again when the payment is reported.
 */
@Composable
private fun PaymentSteps(
    payment: PaymentInstructions,
    rejected: Boolean,
    utr: String,
    onUtrChange: (String) -> Unit,
    onOpenUpiApp: () -> Unit,
    onSubmitClaim: () -> Unit
) {
    if (rejected) {
        Text(
            "Your last payment could not be found. Check the amount and the reference, " +
                "then try again.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFE08A6A)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkSoft)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "YOUR REFERENCE",
            style = MaterialTheme.typography.labelSmall,
            color = Chalk.copy(alpha = 0.5f)
        )
        Text(
            payment.reference,
            style = MaterialTheme.typography.titleLarge,
            color = Gold,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Put this in the payment note. Without it your payment cannot be matched to " +
                "your game.",
            style = MaterialTheme.typography.labelSmall,
            color = Chalk.copy(alpha = 0.6f)
        )
        if (payment.upiId.isNotBlank()) {
            Text(
                "Paying ${payment.price} to ${payment.upiId}",
                style = MaterialTheme.typography.labelSmall,
                color = Chalk.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    Button(
        onClick = onOpenUpiApp,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = ProViolet)
    ) { Text("Pay with UPI", fontWeight = FontWeight.Bold) }

    OutlinedTextField(
        value = utr,
        onValueChange = onUtrChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("UPI transaction id (optional)") }
    )

    OutlinedButton(onClick = onSubmitClaim, modifier = Modifier.fillMaxWidth()) {
        Text("I have paid")
    }

    Text(
        "Payments are checked by hand against a real bank account, so this is not instant. " +
            "Nothing is unlocked until the payment has been found.",
        style = MaterialTheme.typography.labelSmall,
        color = Chalk.copy(alpha = 0.5f)
    )
}

/** One line saying exactly where the subscription stands. */
private fun proStatusLine(
    profile: PlayerProfile,
    plan: SubscriptionPlan,
    claimState: ClaimState?
): String = when {
    profile.pro -> "Runs until " +
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.proUntilMillis))

    claimState == ClaimState.SUBMITTED -> "Waiting for your payment to be checked"
    claimState == ClaimState.REJECTED -> "Your last payment could not be found"
    profile.proUntilMillis > 0L -> "Your subscription has ended"
    plan.price.isNotBlank() -> "${plan.price}, paid by UPI"
    else -> "A little extra, every day"
}
