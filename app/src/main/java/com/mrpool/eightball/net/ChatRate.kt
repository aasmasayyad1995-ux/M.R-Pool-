package com.mrpool.eightball.net

/**
 * How often one player may say something.
 *
 * On both ends on purpose. Holding it back on the way out keeps an ordinary player from
 * spamming by accident; checking it again on the way in is what actually protects a
 * player, because the other phone's app is not something this one gets to trust — a
 * modified one could send a thousand messages a second, and without this they would all
 * arrive.
 */
class ChatRate(
    private val allowance: Int = MESSAGES,
    private val windowMillis: Long = WINDOW_MILLIS
) {
    private val recent = ArrayDeque<Long>()

    /** True when a message may be sent or shown now, and records it if so. */
    fun allow(nowMillis: Long): Boolean {
        while (recent.isNotEmpty() && recent.first() <= nowMillis - windowMillis) {
            recent.removeFirst()
        }
        if (recent.size >= allowance) return false
        recent.addLast(nowMillis)
        return true
    }

    /** How long until the next one would be let through, in millis. Zero when it would. */
    fun waitMillis(nowMillis: Long): Long {
        if (recent.size < allowance) return 0L
        val oldest = recent.firstOrNull() ?: return 0L
        return (oldest + windowMillis - nowMillis).coerceAtLeast(0L)
    }

    companion object {
        /** Enough to talk with, not enough to shout over a game. */
        const val MESSAGES = 5
        const val WINDOW_MILLIS = 10_000L
    }
}
