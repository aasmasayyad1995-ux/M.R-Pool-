package com.mrpool.server.billing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/** A subscription that has been created but not yet paid for. */
data class NewSubscription(val id: String, val payUrl: String)

/**
 * Whatever actually takes the money.
 *
 * An interface because the tests must be able to exercise the whole subscription flow
 * without a Razorpay account, a network, or anybody's card.
 */
interface SubscriptionGateway {
    fun createSubscription(playerId: String): NewSubscription?
    fun cancelSubscription(subscriptionId: String): Boolean
}

/**
 * Everything the billing half of the server needs, read from the environment.
 *
 * Nothing here has a default. Keys belong in the environment of the machine that runs
 * this, never in the repository, and a server started without them simply has no
 * subscriptions rather than half of one.
 */
data class BillingConfig(
    val keyId: String = System.getenv("RAZORPAY_KEY_ID").orEmpty(),
    val keySecret: String = System.getenv("RAZORPAY_KEY_SECRET").orEmpty(),
    val webhookSecret: String = System.getenv("RAZORPAY_WEBHOOK_SECRET").orEmpty(),
    val planId: String = System.getenv("RAZORPAY_PLAN_ID").orEmpty(),
    /** Shown in the app, e.g. "₹99 / month". Display only — Razorpay holds the real price. */
    val priceLabel: String = System.getenv("SUBSCRIPTION_PRICE").orEmpty(),
    /** Where paid players are remembered. Must outlive the container. */
    val storePath: String = System.getenv("SUBSCRIPTION_STORE").orEmpty()
) {
    val isConfigured: Boolean
        get() = keyId.isNotBlank() && keySecret.isNotBlank() &&
            webhookSecret.isNotBlank() && planId.isNotBlank()

    fun storeFile(): File? = storePath.takeIf { it.isNotBlank() }?.let(::File)
}

/**
 * Razorpay's subscriptions API, over the JDK's own HTTP client.
 *
 * The app never talks to Razorpay and never holds a key: it asks this server to start a
 * subscription, gets back Razorpay's own hosted payment page, and opens it in the phone's
 * browser. Card and UPI details are typed into Razorpay's page, not into the game, so
 * nothing the game ships can leak them.
 */
class RazorpayGateway(
    private val config: BillingConfig,
    private val baseUrl: String = "https://api.razorpay.com/v1",
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
) : SubscriptionGateway {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun createSubscription(playerId: String): NewSubscription? {
        // total_count is the number of billing cycles Razorpay will charge before the
        // subscription ends by itself. Ten years of months: long enough to be a
        // subscription, short enough that an abandoned install cannot be charged forever.
        val body = """
            {"plan_id":"${escape(config.planId)}",
             "total_count":120,
             "customer_notify":1,
             "notes":{"player_id":"${escape(playerId)}"}}
        """.trimIndent().replace("\n", "")

        val response = post("/subscriptions", body) ?: return null
        val entity = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
            ?: return null
        val id = entity["id"]?.jsonPrimitive?.content ?: return null
        val url = entity["short_url"]?.jsonPrimitive?.content ?: return null
        return NewSubscription(id, url)
    }

    override fun cancelSubscription(subscriptionId: String): Boolean {
        // At the end of the cycle, not immediately: the player paid for this month.
        val path = "/subscriptions/${escape(subscriptionId)}/cancel"
        return post(path, """{"cancel_at_cycle_end":1}""") != null
    }

    private fun post(path: String, body: String): String? {
        val credentials = Base64.getEncoder()
            .encodeToString("${config.keyId}:${config.keySecret}".toByteArray())
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Basic $credentials")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return runCatching {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) response.body() else null
        }.getOrNull()
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
