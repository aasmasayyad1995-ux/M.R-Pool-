package com.mrpool.eightball.billing

import com.mrpool.eightball.net.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to the subscription half of the match server.
 *
 * Deliberately dull. Every judgement about what a player is entitled to belongs on the
 * server, and in the end with the person reading their own bank statement; this only
 * carries the question there and the answer back.
 */
class SubscriptionClient(
    private val baseUrl: String = BillingConfig.serverUrl,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonType = "application/json".toMediaType()

    suspend fun plan(): SubscriptionPlan = withContext(Dispatchers.IO) {
        SubscriptionPlan.parse(get("/billing/plan"))
    }

    suspend fun status(playerId: String): Subscription = withContext(Dispatchers.IO) {
        Subscription.parse(get("/billing/status?player=${encode(playerId)}"))
    }

    /** Asks for a reference and the `upi://` link to pay it with. */
    suspend fun paymentInstructions(playerId: String): PaymentInstructions? =
        withContext(Dispatchers.IO) {
            PaymentInstructions.parse(post("/billing/payment", mapOf("player" to playerId)))
        }

    /**
     * Tells the server the player says they have paid.
     *
     * This grants nothing. It puts the payment in the owner's queue to be checked against
     * their bank.
     */
    suspend fun submitClaim(
        playerId: String,
        reference: String,
        utr: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = post(
            "/billing/claim",
            mapOf("player" to playerId, "reference" to reference, "utr" to utr)
        ) ?: return@withContext false
        Json.readObject(body)?.get("submitted") as? Boolean ?: false
    }

    private fun get(path: String): String? = runCatching {
        http.newCall(Request.Builder().url(baseUrl + path).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun post(path: String, fields: Map<String, String>): String? = runCatching {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(Json.write(fields).toRequestBody(jsonType))
            .build()
        http.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
