package com.mrpool.eightball.billing

import com.mrpool.eightball.net.Json

/**
 * What the server last said about this player's subscription.
 *
 * The app does not decide this and cannot: an entitlement is whatever the server worked
 * out from what the payment provider signed. All the app does is ask, cache the answer so
 * a subscriber is not cut off the moment they lose signal, and stop trusting the cache
 * when it runs out.
 */
data class Subscription(
    /** Epoch millis the subscription runs to, including the server's grace period. */
    val activeUntilMillis: Long,
    /** The provider's own word: active, cancelled, halted, none. */
    val status: String
) {
    fun isActive(nowMillis: Long): Boolean = nowMillis < activeUntilMillis

    /** True once it has been paid for but is no longer renewing. */
    val isEnding: Boolean get() = status == "cancelled" || status == "completed"

    companion object {
        val NONE = Subscription(0L, "none")

        /**
         * Reads the server's `/billing/status` reply.
         *
         * Anything unreadable is [NONE] rather than an exception: a server that has been
         * redeployed, a captive portal serving a login page, a truncated response — none
         * of those should crash the game, and none of them are proof of payment.
         */
        fun parse(body: String?): Subscription {
            val fields = body?.let { Json.readObject(it) } ?: return NONE
            val active = fields["active"] as? Boolean ?: false
            val until = (fields["until"] as? Double)?.toLong() ?: 0L
            val status = fields["status"] as? String ?: "none"
            // An "active" with no expiry would be a subscription that never ends; a server
            // that says that is a server sending something this app does not understand.
            if (active && until <= 0L) return NONE
            return Subscription(if (active) until else 0L, status)
        }
    }
}

/** What the server charges, and whether it can take money at all. */
data class SubscriptionPlan(
    val configured: Boolean,
    /** Display only — the provider holds the real price. */
    val price: String
) {
    companion object {
        val UNAVAILABLE = SubscriptionPlan(configured = false, price = "")

        fun parse(body: String?): SubscriptionPlan {
            val fields = body?.let { Json.readObject(it) } ?: return UNAVAILABLE
            return SubscriptionPlan(
                configured = fields["configured"] as? Boolean ?: false,
                price = fields["price"] as? String ?: ""
            )
        }
    }
}
