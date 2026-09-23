package com.mrpool.eightball.data

import java.util.TimeZone

/**
 * Which day the daily bonus belongs to.
 *
 * Counting whole days from the epoch gives UTC days, and a UTC day does not start at
 * midnight for most of the world. In India it starts at half past five in the morning, so
 * a player who claimed at ten at night could claim again over breakfast, and a player who
 * claimed at six in the morning had to wait nearly two days. The phone's own offset is
 * added first so the day turns over when the player's clock says it does.
 *
 * Kept away from Android so the arithmetic can be tested for the offsets that actually
 * bite: half hours, three quarter hours, and the negative side of the world.
 */
object BonusDay {

    const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** The day [nowMillis] falls in for a clock [offsetMillis] ahead of UTC. */
    fun of(nowMillis: Long, offsetMillis: Int): Long =
        Math.floorDiv(nowMillis + offsetMillis, DAY_MILLIS)

    /** The day it is on this phone right now. */
    fun local(nowMillis: Long = System.currentTimeMillis()): Long =
        of(nowMillis, TimeZone.getDefault().getOffset(nowMillis))
}
