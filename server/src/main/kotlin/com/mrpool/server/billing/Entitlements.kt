package com.mrpool.server.billing

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * What one player has paid for.
 *
 * @param playerId the app's install id, which is all the identity this game has
 * @param subscriptionId the Razorpay subscription this came from
 * @param status Razorpay's own word for it, passed through so the app can explain itself
 * @param paidUntilMillis the end of the cycle the player has already paid for
 */
data class Entitlement(
    val playerId: String,
    val subscriptionId: String,
    val status: String,
    val paidUntilMillis: Long
) {
    /**
     * True while the paid cycle still has time on it, plus a day.
     *
     * The grace day is not generosity: Razorpay charges a renewal some hours after the
     * cycle turns over, and without it a paying player is locked out every month in the
     * gap between the old cycle ending and the new charge landing.
     */
    fun isActive(nowMillis: Long): Boolean = nowMillis < paidUntilMillis + GRACE_MILLIS

    fun activeUntilMillis(): Long = paidUntilMillis + GRACE_MILLIS

    companion object {
        const val GRACE_MILLIS = 24L * 60L * 60L * 1000L
    }
}

/**
 * Who has paid, kept across restarts.
 *
 * A subscription that a redeploy forgets is a subscription the player paid for twice, so
 * this writes every change to disk before it returns. The file is a line of JSON per
 * player, rewritten whole and moved into place, so a crash half way through a write leaves
 * the previous file rather than half of a new one.
 *
 * [file] null keeps everything in memory, which is what the tests and a server with no
 * billing configured both want.
 */
class Entitlements(private val file: File? = null) {

    private val byPlayer = ConcurrentHashMap<String, Entitlement>()

    /** Which player a subscription belongs to, recorded when the subscription is created. */
    private val playerOfSubscription = ConcurrentHashMap<String, String>()

    init {
        load()
    }

    /** Remembers that [subscriptionId] was created for [playerId], before any payment. */
    fun claimSubscription(subscriptionId: String, playerId: String) {
        playerOfSubscription[subscriptionId] = playerId
        save()
    }

    fun playerFor(subscriptionId: String): String? = playerOfSubscription[subscriptionId]

    operator fun get(playerId: String): Entitlement? = byPlayer[playerId]

    fun isActive(playerId: String, nowMillis: Long): Boolean =
        byPlayer[playerId]?.isActive(nowMillis) ?: false

    /**
     * Records what Razorpay says about a subscription.
     *
     * Webhooks arrive out of order and more than once, so a later event must never shorten
     * an entitlement: the longest paid-until wins. Without that, a `cancelled` event
     * overtaking the `charged` event that paid for the cycle would take away a month the
     * player had already bought.
     */
    fun apply(entitlement: Entitlement) {
        byPlayer.compute(entitlement.playerId) { _, existing ->
            if (existing == null || entitlement.paidUntilMillis >= existing.paidUntilMillis) {
                entitlement
            } else {
                existing.copy(status = entitlement.status)
            }
        }
        playerOfSubscription[entitlement.subscriptionId] = entitlement.playerId
        save()
    }

    fun size(): Int = byPlayer.size

    // ------------------------------------------------------------------- persistence

    @Synchronized
    private fun save() {
        val target = file ?: return
        target.parentFile?.mkdirs()
        val temporary = File(target.absolutePath + ".tmp")
        temporary.writeText(
            buildString {
                for (e in byPlayer.values) {
                    append(
                        """{"player":${quote(e.playerId)},"subscription":""" +
                            """${quote(e.subscriptionId)},"status":${quote(e.status)},""" +
                            """"paidUntil":${e.paidUntilMillis}}"""
                    )
                    append('\n')
                }
                for ((subscription, player) in playerOfSubscription) {
                    if (byPlayer.containsKey(player)) continue
                    append("""{"claim":${quote(subscription)},"player":${quote(player)}}""")
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
            val claim = field(line, "claim")
            val player = field(line, "player") ?: continue
            if (claim != null) {
                playerOfSubscription[claim] = player
                continue
            }
            val subscription = field(line, "subscription") ?: continue
            val paidUntil = number(line, "paidUntil") ?: continue
            byPlayer[player] = Entitlement(
                playerId = player,
                subscriptionId = subscription,
                status = field(line, "status") ?: "unknown",
                paidUntilMillis = paidUntil
            )
            playerOfSubscription[subscription] = player
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
}
