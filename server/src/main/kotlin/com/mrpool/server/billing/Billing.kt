package com.mrpool.server.billing

import com.mrpool.server.RoomCode
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * A crude cap on how often one caller may do something that costs the owner attention.
 *
 * Every claim lands in a queue a person has to read, so an open endpoint that creates them
 * is worth flooding. This is not protection against a determined attacker; it is the
 * difference between a stray retry loop and a thousand rows to scroll past.
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
    /** Where the player's last attempt to pay has got to. */
    val claimState: ClaimState?,
    val reference: String
)

/** What the app needs to show a player the payment screen. */
data class PaymentInstructions(
    val reference: String,
    val payLink: String,
    val upiId: String,
    val priceLabel: String
)

/**
 * The subscription: a UPI link, a reference, and a person who checks the bank.
 *
 * There is no gateway, so nothing here can know that money moved. The flow is honest
 * about that: the player pays into the owner's UPI with a reference in the note, tells the
 * app they have paid, and the owner approves it from the admin page once they can see it
 * in their account. Until the owner says so, nobody is subscribed.
 */
class Billing(
    val config: BillingConfig = BillingConfig(),
    val entitlements: Entitlements = Entitlements(config.storeFile()),
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val throttle: Throttle = Throttle(limit = 8, windowMillis = 60L * 60L * 1000L)
) {
    val isConfigured: Boolean get() = config.isConfigured

    fun statusFor(playerId: String): SubscriptionStatus {
        val entitlement = entitlements[playerId]
        val claim = entitlements.latestClaimFor(playerId)
        return SubscriptionStatus(
            active = entitlement?.isActive(clock()) ?: false,
            activeUntilMillis = entitlement?.paidUntilMillis ?: 0L,
            claimState = claim?.state,
            reference = claim?.reference.orEmpty()
        )
    }

    /**
     * Gives the player a reference and the UPI link to pay with.
     *
     * A player who already has an unpaid reference gets the same one back, so tapping the
     * button twice does not leave two rows in the owner's queue for one payment.
     */
    fun paymentInstructions(playerId: String, throttleKey: String): PaymentInstructions? {
        if (!isConfigured) return null
        if (!isSanePlayerId(playerId)) return null

        val existing = entitlements.latestClaimFor(playerId)
        if (existing != null && existing.state == ClaimState.AWAITING) {
            return instructionsFor(existing.reference)
        }
        if (!throttle.allow(throttleKey, clock())) return null

        val reference = newReference()
        entitlements.putClaim(
            Claim(
                reference = reference,
                playerId = playerId,
                state = ClaimState.AWAITING,
                createdAtMillis = clock()
            )
        )
        return instructionsFor(reference)
    }

    /**
     * The player says they have paid.
     *
     * This grants nothing. It moves the claim into the owner's queue, and everything it
     * carries — the reference, the transaction number — is the player's word, to be read
     * by a person with the bank statement open.
     */
    fun submitClaim(playerId: String, reference: String, utr: String): Boolean {
        if (!isConfigured) return false
        val claim = entitlements.claim(reference.trim().uppercase()) ?: return false
        // The reference alone must not be enough: it travels through a UPI note and could
        // be seen. The claim only moves for the player it was minted for.
        if (claim.playerId != playerId) return false
        if (claim.state == ClaimState.APPROVED) return false
        entitlements.putClaim(
            claim.copy(state = ClaimState.SUBMITTED, utr = utr.take(MAX_UTR).trim())
        )
        return true
    }

    // -------------------------------------------------------------------- the owner

    /** True when [token] is the admin password, compared without leaking its length. */
    fun isOwner(token: String?): Boolean =
        config.adminToken.isNotEmpty() && Signature.constantTimeEquals(config.adminToken, token)

    /** The owner found the money. Adds a month. */
    fun approve(reference: String, note: String = ""): Entitlement? {
        val claim = entitlements.claim(reference) ?: return null
        entitlements.putClaim(
            claim.copy(state = ClaimState.APPROVED, note = note.take(MAX_NOTE))
        )
        return entitlements.grantDays(claim.playerId, config.days, clock())
    }

    /** The owner did not find the money. */
    fun reject(reference: String, note: String = ""): Boolean {
        val claim = entitlements.claim(reference) ?: return false
        entitlements.putClaim(
            claim.copy(state = ClaimState.REJECTED, note = note.take(MAX_NOTE))
        )
        return true
    }

    /** Takes a subscription back, for a payment that turned out not to be one. */
    fun revoke(playerId: String) = entitlements.revoke(playerId)

    fun queue(): List<Claim> = entitlements.submittedClaims()

    fun recent(): List<Claim> = entitlements.recentClaims()

    // -------------------------------------------------------------------- internals

    private fun instructionsFor(reference: String) = PaymentInstructions(
        reference = reference,
        payLink = config.payLink(reference),
        upiId = config.upiId,
        priceLabel = config.priceLabel
    )

    /**
     * A short code the owner can read in a bank statement.
     *
     * Reuses the room code alphabet, which already leaves out the characters that look
     * alike — a reference misread as a different one is a payment credited to the wrong
     * player.
     */
    private fun newReference(): String {
        repeat(20) {
            val candidate = "MRP-" + RoomCode.generate(random)
            if (entitlements.claim(candidate) == null) return candidate
        }
        return "MRP-" + RoomCode.generate(random) + clock().toString().takeLast(3)
    }

    private fun isSanePlayerId(playerId: String): Boolean =
        playerId.isNotBlank() && playerId.length <= 64

    companion object {
        const val MAX_UTR = 40
        const val MAX_NOTE = 120
    }
}
