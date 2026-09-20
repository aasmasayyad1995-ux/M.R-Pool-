package com.mrpool.eightball.net

import com.mrpool.eightball.game.GameSession
import kotlin.math.roundToLong

/**
 * A short fingerprint of a table, used to catch two devices drifting apart.
 *
 * Both devices replay the same shots through the same physics, but floating point on two
 * different CPUs is not guaranteed to agree to the last bit, and a billiard break amplifies
 * a tiny difference quickly. After every shot each device fingerprints its own table; if the
 * two disagree, the host sends a snapshot and the guest adopts it.
 *
 * Positions are quantised to a tenth of a millimetre before hashing. Any finer and honest
 * rounding noise would be reported as a desync; any coarser and a real divergence could
 * hide inside the tolerance — a tenth of a millimetre is a four-hundredth of a ball.
 */
object StateChecksum {

    /** Quantisation step, in metres. */
    const val PRECISION = 0.0001f

    fun of(session: GameSession): String {
        val builder = StringBuilder()
        // Sorted by number so the ball list's own order can never affect the result.
        for (ball in session.physics.balls.sortedBy { it.number }) {
            builder.append(ball.number)
            builder.append(':')
            if (ball.pocketed) {
                builder.append('X')
            } else {
                builder.append(quantise(ball.position.x))
                builder.append(',')
                builder.append(quantise(ball.position.y))
            }
            builder.append(';')
        }
        builder.append(session.currentSeat.name)
        builder.append('|')
        builder.append(session.tableOpen)
        builder.append('|')
        builder.append(session.groupOf(com.mrpool.eightball.game.Seat.ONE)?.name ?: "-")
        builder.append('|')
        builder.append(session.groupOf(com.mrpool.eightball.game.Seat.TWO)?.name ?: "-")
        return hash(builder.toString())
    }

    private fun quantise(value: Float): Long = (value / PRECISION).roundToLong()

    /** FNV-1a: short, stable across platforms, and good enough to spot a drifting table. */
    private fun hash(text: String): String {
        var result = -0x340d631b7bdddcdbL
        for (char in text) {
            result = result xor char.code.toLong()
            result *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(result)
    }
}
