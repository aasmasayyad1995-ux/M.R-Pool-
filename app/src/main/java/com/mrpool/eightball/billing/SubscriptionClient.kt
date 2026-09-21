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
 * server, where the payment provider's signature can be checked; this only carries the
 * question there and the answer back.
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

    /**
     * Starts a subscription and returns the provider's hosted payment page.
     *
     * The game never sees a card or a UPI id: that page opens in the phone's browser and
     * the money is typed into the provider's own site.
     */
    suspend fun beginSubscription(playerId: String): String? = withContext(Dispatchers.IO) {
        val body = post("/billing/subscribe", playerId) ?: return@withContext null
        Json.readObject(body)?.get("url") as? String
    }

    suspend fun cancel(playerId: String): Boolean = withContext(Dispatchers.IO) {
        val body = post("/billing/cancel", playerId) ?: return@withContext false
        Json.readObject(body)?.get("cancelled") as? Boolean ?: false
    }

    private fun get(path: String): String? = runCatching {
        http.newCall(Request.Builder().url(baseUrl + path).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun post(path: String, playerId: String): String? = runCatching {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(Json.write(mapOf("player" to playerId)).toRequestBody(jsonType))
            .build()
        http.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
