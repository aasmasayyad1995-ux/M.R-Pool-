package com.mrpool.eightball.billing

import com.mrpool.eightball.net.Json

/** Where the player's last attempt to pay has got to. */
enum class ClaimState {
    /** They have a reference and a link, but have not said they paid. */
    AWAITING,

    /** They say they have paid. Waiting for the owner to check their bank. */
    SUBMITTED,

    /** The owner found the money. */
    APPROVED,

    /** The owner did not find the money. */
    REJECTED;

    companion object {
        fun parse(name: String?): ClaimState? =
            entries.firstOrNull { it.name == name?.uppercase() }
    }
}

/**
 * What the server last said about this player's subscription.
 *
 * The app does not decide this and cannot. Money arrives by UPI straight into the owner's
 * account, so nothing automatic knows it moved: a subscription exists only once the owner
 * has looked at their bank and approved it. All the app does is ask, cache the answer so a
 * subscriber is not cut off the moment they lose signal, and stop trusting the cache when
 * it runs out.
 */
data class Subscription(
    /** Epoch millis the subscription runs to. */
    val activeUntilMillis: Long,
    val claimState: ClaimState?,
    /** The reference that went in the UPI note, so the screen can show it again. */
    val reference: String
) {
    fun isActive(nowMillis: Long): Boolean = nowMillis < activeUntilMillis

    /** True while the owner has yet to look at a payment the player says they made. */
    val isWaitingOnOwner: Boolean get() = claimState == ClaimState.SUBMITTED

    companion object {
        val NONE = Subscription(0L, null, "")

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
            val claim = ClaimState.parse(fields["claim"] as? String)
            val reference = fields["reference"] as? String ?: ""
            // An "active" with no expiry would be a subscription that never ends; a server
            // that says that is a server sending something this app does not understand.
            if (active && until <= 0L) return NONE
            return Subscription(if (active) until else 0L, claim, reference)
        }
    }
}

/** What the server charges, and whether it can take money at all. */
data class SubscriptionPlan(
    val configured: Boolean,
    /** Display only. */
    val price: String,
    /** The owner's UPI id, shown so a player can pay by hand if the link will not open. */
    val upiId: String
) {
    companion object {
        val UNAVAILABLE = SubscriptionPlan(configured = false, price = "", upiId = "")

        fun parse(body: String?): SubscriptionPlan {
            val fields = body?.let { Json.readObject(it) } ?: return UNAVAILABLE
            return SubscriptionPlan(
                configured = fields["configured"] as? Boolean ?: false,
                price = fields["price"] as? String ?: "",
                upiId = fields["upiId"] as? String ?: ""
            )
        }
    }
}

/** The reference and the `upi://` link for one payment. */
data class PaymentInstructions(
    val reference: String,
    val payLink: String,
    val upiId: String,
    val price: String
) {
    companion object {
        fun parse(body: String?): PaymentInstructions? {
            val fields = body?.let { Json.readObject(it) } ?: return null
            val reference = fields["reference"] as? String ?: return null
            val link = fields["payLink"] as? String ?: return null
            return PaymentInstructions(
                reference = reference,
                payLink = link,
                upiId = fields["upiId"] as? String ?: "",
                price = fields["price"] as? String ?: ""
            )
        }
    }
}
