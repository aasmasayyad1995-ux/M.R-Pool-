package com.mrpool.server.billing

import java.util.concurrent.ConcurrentHashMap

/**
 * A crude cap on how often one caller may do something expensive.
 *
 * Creating a subscription costs a call to Razorpay and leaves a record there, so an open
 * endpoint that does it is worth hammering. This is not protection against a determined
 * attacker; it is the difference between a stray retry loop and ten thousand dead
 * subscriptions in somebody's Razorpay dashboard.
 */
class Throttle(
    private val limit: Int,
    private val windowMillis: Long
) {
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()

    @Synchronized
    fun allow(key: String, nowMillis: Long): Boolean {
        val recent = hits.getOrPut(key) { mutableListOf() }
        recent.removeAll { it < nowMillis - windowMillis }
        if (recent.size >= limit) return false
        recent.add(nowMillis)
        return true
    }
}

/** What the app is told about a player's subscription. */
data class SubscriptionStatus(
    val active: Boolean,
    val activeUntilMillis: Long,
    val status: String
)

/**
 * The subscription, as one object: what it costs, who has paid, and how to start paying.
 *
 * Kept apart from the match hub because they share nothing. A server with billing
 * configured and no players relays no matches; a server relaying matches with no keys
 * simply says subscriptions are unavailable, and the game still plays.
 */
class Billing(
    val config: BillingConfig = BillingConfig(),
    /** Null in production, where the real gateway is built from [config]. */
    private val gateway: SubscriptionGateway? = null,
    val entitlements: Entitlements = Entitlements(config.storeFile()),
    private val clock: () -> Long = System::currentTimeMillis,
    private val throttle: Throttle = Throttle(limit = 6, windowMillis = 60L * 60L * 1000L)
) {
    /** The live gateway, or the one handed in for tests. */
    private val paymentGateway: SubscriptionGateway? =
        gateway ?: if (config.isConfigured) RazorpayGateway(config) else null

    val isConfigured: Boolean get() = paymentGateway != null

    fun statusFor(playerId: String): SubscriptionStatus {
        val entitlement = entitlements[playerId]
            ?: return SubscriptionStatus(false, 0L, "none")
        return SubscriptionStatus(
            active = entitlement.isActive(clock()),
            activeUntilMillis = entitlement.activeUntilMillis(),
            status = entitlement.status
        )
    }

    /**
     * Starts a subscription and returns Razorpay's hosted payment page.
     *
     * The entitlement is not granted here. It is granted when Razorpay says the money
     * arrived, over a signed webhook — this only writes down whose subscription it is, so
     * that webhook can be matched to a player.
     */
    fun beginSubscription(playerId: String, throttleKey: String): NewSubscription? {
        val gateway = paymentGateway ?: return null
        if (playerId.isBlank() || playerId.length > 64) return null
        if (!throttle.allow(throttleKey, clock())) return null
        val created = gateway.createSubscription(playerId) ?: return null
        entitlements.claimSubscription(created.id, playerId)
        return created
    }

    /** Stops the renewal. The player keeps what they paid for until the cycle ends. */
    fun cancel(playerId: String): Boolean {
        val gateway = paymentGateway ?: return false
        val subscriptionId = entitlements[playerId]?.subscriptionId ?: return false
        return gateway.cancelSubscription(subscriptionId)
    }

    /** Verifies and applies a Razorpay webhook. Returns the player it moved. */
    fun handleWebhook(body: String, signature: String?): String? =
        Webhook.handle(body, signature, config.webhookSecret, entitlements)
}
