package com.mrpool.server.billing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * The subscription endpoints.
 *
 * There are no accounts in this game, so a player is an install id the app generated for
 * itself. That is enough to tie a payment to a phone and no more: this server never learns
 * a name, a number or a card, because the paying happens on Razorpay's own page.
 *
 * Everything the app is told comes from what Razorpay signed. The app can ask, and it can
 * start a subscription; it can never assert that it has one.
 */
fun Route.billingRoutes(billing: Billing) {

    /** What the subscription costs and whether this server can sell it at all. */
    get("/billing/plan") {
        call.respondJson(
            """{"configured":${billing.isConfigured},""" +
                """"price":${json(billing.config.priceLabel)}}"""
        )
    }

    /** Whether this player has paid, and until when. */
    get("/billing/status") {
        val player = call.request.queryParameters["player"].orEmpty()
        if (player.isBlank()) {
            call.respondJson("""{"error":"player required"}""", HttpStatusCode.BadRequest)
            return@get
        }
        val status = billing.statusFor(player)
        call.respondJson(
            """{"active":${status.active},"until":${status.activeUntilMillis},""" +
                """"status":${json(status.status)}}"""
        )
    }

    /** Starts a subscription and hands back Razorpay's hosted payment page. */
    post("/billing/subscribe") {
        val player = playerFrom(call.receiveText())
        if (player.isNullOrBlank()) {
            call.respondJson("""{"error":"player required"}""", HttpStatusCode.BadRequest)
            return@post
        }
        if (!billing.isConfigured) {
            call.respondJson(
                """{"error":"subscriptions are not set up on this server"}""",
                HttpStatusCode.ServiceUnavailable
            )
            return@post
        }
        // The address is the throttle key: a player id is chosen by the caller, so
        // throttling on it alone would be throttling on something they can change.
        val created = billing.beginSubscription(
            playerId = player,
            throttleKey = call.request.local.remoteHost
        )
        if (created == null) {
            call.respondJson(
                """{"error":"could not start the subscription"}""",
                HttpStatusCode.ServiceUnavailable
            )
            return@post
        }
        call.respondJson(
            """{"subscriptionId":${json(created.id)},"url":${json(created.payUrl)}}"""
        )
    }

    /** Stops the renewal; the player keeps the cycle they paid for. */
    post("/billing/cancel") {
        val player = playerFrom(call.receiveText())
        if (player.isNullOrBlank()) {
            call.respondJson("""{"error":"player required"}""", HttpStatusCode.BadRequest)
            return@post
        }
        val cancelled = billing.cancel(player)
        call.respondJson("""{"cancelled":$cancelled}""")
    }

    /**
     * Razorpay tells us the money moved.
     *
     * The body must be read exactly as it arrived, because the signature is over those
     * bytes. An unsigned or wrongly signed body is answered with the same flat 400 as an
     * unreadable one: a caller probing for the secret learns nothing from the difference.
     */
    post("/billing/webhook") {
        val body = call.receiveText()
        val signature = call.request.header("X-Razorpay-Signature")
        val player = billing.handleWebhook(body, signature)
        if (player == null) {
            call.respondJson("""{"ok":false}""", HttpStatusCode.BadRequest)
        } else {
            call.respondJson("""{"ok":true}""")
        }
    }
}

/** Pulls the player id out of a `{"player":"..."}` body without a JSON dependency. */
private fun playerFrom(body: String): String? =
    Regex(""""player"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(body)?.groupValues?.get(1)

private fun json(value: String): String =
    '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"'

private suspend fun io.ktor.server.application.ApplicationCall.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(body, io.ktor.http.ContentType.Application.Json, status)
}
