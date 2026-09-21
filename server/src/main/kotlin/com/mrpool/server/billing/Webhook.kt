package com.mrpool.server.billing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The part of a Razorpay webhook this server cares about. */
data class SubscriptionEvent(
    /** Razorpay's event name, e.g. `subscription.charged`. */
    val event: String,
    val subscriptionId: String,
    /** Razorpay's status for the subscription: active, cancelled, halted, completed... */
    val status: String,
    /** End of the cycle that has been paid for, in epoch millis. */
    val paidUntilMillis: Long,
    /** The player id the app put in the subscription's notes, when it is there. */
    val playerId: String?
)

/**
 * Reads Razorpay's webhooks.
 *
 * Every event carries the subscription's current state, so there is one rule for all of
 * them: the player has paid up to `current_end`. A cancellation does not cut the month
 * short — Razorpay keeps `current_end` on a subscription cancelled at the end of its
 * cycle, and a player who paid for a month gets the month.
 */
object Webhook {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Events that say something about whether a subscription is paid for. */
    val HANDLED = setOf(
        "subscription.activated",
        "subscription.charged",
        "subscription.resumed",
        "subscription.pending",
        "subscription.halted",
        "subscription.paused",
        "subscription.cancelled",
        "subscription.completed",
        "subscription.expired"
    )

    /** Returns null for anything unreadable or uninteresting, rather than throwing. */
    fun parse(body: String): SubscriptionEvent? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null
        val event = root.string("event") ?: return null
        if (event !in HANDLED) return null

        val entity = root["payload"]?.jsonObjectOrNull()
            ?.get("subscription")?.jsonObjectOrNull()
            ?.get("entity")?.jsonObjectOrNull()
            ?: return null

        val id = entity.string("id") ?: return null
        // Razorpay counts in whole seconds; the rest of this server counts in millis.
        val currentEnd = entity["current_end"]?.jsonPrimitive?.longOrNull ?: 0L

        return SubscriptionEvent(
            event = event,
            subscriptionId = id,
            status = entity.string("status") ?: "unknown",
            paidUntilMillis = currentEnd * 1000L,
            playerId = entity["notes"]?.jsonObjectOrNull()?.string("player_id")
        )
    }

    /**
     * Verifies and applies a webhook. Returns the player it moved, or null when the body
     * was not signed by Razorpay, could not be read, or is about a subscription this
     * server never created.
     */
    fun handle(
        body: String,
        signature: String?,
        secret: String,
        entitlements: Entitlements
    ): String? {
        if (!Signature.matches(body, secret, signature)) return null
        val event = parse(body) ?: return null

        // The note travels with the subscription, the claim was written when this server
        // created it. Either identifies the player; neither means the subscription is not
        // ours, and an unknown subscription must not create an entitlement.
        val player = event.playerId ?: entitlements.playerFor(event.subscriptionId) ?: return null

        entitlements.apply(
            Entitlement(
                playerId = player,
                subscriptionId = event.subscriptionId,
                status = event.status,
                paidUntilMillis = event.paidUntilMillis
            )
        )
        return player
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        runCatching { jsonObject }.getOrNull()
}
