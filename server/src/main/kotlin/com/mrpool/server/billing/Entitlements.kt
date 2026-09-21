package com.mrpool.server.billing

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * What one player has paid for.
 *
 * @param playerId the app's install id, which is all the identity this game has
 * @param paidUntilMillis when the subscription runs out
 */
data class Entitlement(
    val playerId: String,
    val paidUntilMillis: Long
) {
    fun isActive(nowMillis: Long): Boolean = nowMillis < paidUntilMillis
}

/**
 * Who has paid, and who says they are about to, kept across restarts.
 *
 * A subscription a redeploy forgets is a subscription the player paid for twice, so every
 * change is written to disk before it returns. The file is a line of JSON per record,
 * rewritten whole and moved into place, so a crash half way through a write leaves the
 * previous file rather than half of a new one.
 *
 * [file] null keeps everything in memory, which is what the tests and a server with no
 * billing configured both want.
 */
class Entitlements(private val file: File? = null) {

    private val byPlayer = ConcurrentHashMap<String, Entitlement>()
    private val claims = ConcurrentHashMap<String, Claim>()

    init {
        load()
    }

    // ------------------------------------------------------------------- entitlements

    operator fun get(playerId: String): Entitlement? = byPlayer[playerId]

    fun isActive(playerId: String, nowMillis: Long): Boolean =
        byPlayer[playerId]?.isActive(nowMillis) ?: false

    /**
     * Adds [days] to a player's subscription.
     *
     * Paid time is added to whatever is left rather than replacing it, so somebody who
     * pays early keeps the days they have already bought instead of losing them.
     */
    fun grantDays(playerId: String, days: Int, nowMillis: Long): Entitlement {
        val from = maxOf(nowMillis, byPlayer[playerId]?.paidUntilMillis ?: 0L)
        val granted = Entitlement(playerId, from + days * DAY_MILLIS)
        byPlayer[playerId] = granted
        save()
        return granted
    }

    /** Takes a subscription away. For the owner, when a payment turns out to be a lie. */
    fun revoke(playerId: String) {
        byPlayer.remove(playerId)
        save()
    }

    fun size(): Int = byPlayer.size

    // ------------------------------------------------------------------------ claims

    fun putClaim(claim: Claim) {
        claims[claim.reference] = claim
        save()
    }

    fun claim(reference: String): Claim? = claims[reference]

    /** The most recent claim a player made, which is the one the app asks about. */
    fun latestClaimFor(playerId: String): Claim? =
        claims.values.filter { it.playerId == playerId }.maxByOrNull { it.createdAtMillis }

    /** Everything waiting on the owner, oldest first: the approvals queue. */
    fun submittedClaims(): List<Claim> =
        claims.values.filter { it.state == ClaimState.SUBMITTED }.sortedBy { it.createdAtMillis }

    fun recentClaims(limit: Int = 40): List<Claim> =
        claims.values.sortedByDescending { it.createdAtMillis }.take(limit)

    // ------------------------------------------------------------------- persistence

    @Synchronized
    private fun save() {
        val target = file ?: return
        target.parentFile?.mkdirs()
        val temporary = File(target.absolutePath + ".tmp")
        temporary.writeText(
            buildString {
                for (e in byPlayer.values) {
                    append("""{"player":${quote(e.playerId)},"paidUntil":${e.paidUntilMillis}}""")
                    append('\n')
                }
                for (c in claims.values) {
                    append(
                        """{"claim":${quote(c.reference)},"player":${quote(c.playerId)},""" +
                            """"state":${quote(c.state.name)},"at":${c.createdAtMillis},""" +
                            """"utr":${quote(c.utr)},"note":${quote(c.note)}}"""
                    )
                    append('\n')
                }
            }
        )
        temporary.renameTo(target)
    }

    private fun load() {
        val source = file ?: return
        if (!source.exists()) return
        for (line in source.readLines()) {
            if (line.isBlank()) continue
            val player = field(line, "player") ?: continue
            val reference = field(line, "claim")
            if (reference != null) {
                claims[reference] = Claim(
                    reference = reference,
                    playerId = player,
                    state = runCatching {
                        ClaimState.valueOf(field(line, "state").orEmpty())
                    }.getOrDefault(ClaimState.AWAITING),
                    createdAtMillis = number(line, "at") ?: 0L,
                    utr = field(line, "utr").orEmpty(),
                    note = field(line, "note").orEmpty()
                )
                continue
            }
            val paidUntil = number(line, "paidUntil") ?: continue
            byPlayer[player] = Entitlement(player, paidUntil)
        }
    }

    private fun quote(value: String): String =
        '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"'

    private fun field(line: String, name: String): String? =
        Regex(""""$name":"((?:[^"\\]|\\.)*)"""").find(line)
            ?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")

    private fun number(line: String, name: String): Long? =
        Regex(""""$name":(-?\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull()

    companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
