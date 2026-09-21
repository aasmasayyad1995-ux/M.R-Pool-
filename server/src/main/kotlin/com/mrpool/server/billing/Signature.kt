package com.mrpool.server.billing

/**
 * Comparing a secret without saying how nearly a guess was right.
 *
 * The admin password is the only thing standing between the internet and a page that
 * hands out subscriptions, so it is worth guessing. Returning on the first wrong
 * character leaks, in the time taken, how much of a guess was correct, and a password can
 * be worked out a character at a time by anyone patient enough to measure.
 */
object Signature {

    /** True when [provided] equals [secret], in time that does not depend on how much matched. */
    fun constantTimeEquals(secret: String, provided: String?): Boolean {
        if (secret.isEmpty() || provided.isNullOrEmpty()) return false
        // The lengths themselves differ visibly in any comparison, so this only promises
        // not to leak which characters matched.
        if (secret.length != provided.length) return false
        var difference = 0
        for (i in secret.indices) {
            difference = difference or (secret[i].code xor provided[i].code)
        }
        return difference == 0
    }
}
