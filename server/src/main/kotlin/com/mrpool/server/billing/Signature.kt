package com.mrpool.server.billing

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256, as Razorpay signs its webhooks.
 *
 * This is the one piece of the subscription that decides whether somebody has paid. The
 * app never gets to say "I am subscribed": it only ever asks, and the answer comes from
 * what Razorpay signed. So everything here treats the request body as hostile.
 */
object Signature {

    /** Lower case hex HMAC-SHA256 of [body] under [secret]. */
    fun hmacSha256(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it)
        }
    }

    /**
     * True when [provided] is the signature for [body].
     *
     * The comparison runs over every byte whatever happens. Returning early on the first
     * wrong character leaks, in the time taken, how much of a guess was right, and a
     * signature can be guessed a character at a time by anyone patient enough to measure.
     */
    fun matches(body: String, secret: String, provided: String?): Boolean {
        if (secret.isEmpty() || provided.isNullOrEmpty()) return false
        val expected = hmacSha256(body, secret)
        if (expected.length != provided.length) return false
        var difference = 0
        for (i in expected.indices) {
            difference = difference or (expected[i].code xor provided[i].code)
        }
        return difference == 0
    }
}
