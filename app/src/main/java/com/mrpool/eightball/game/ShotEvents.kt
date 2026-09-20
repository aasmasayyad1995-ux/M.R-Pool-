package com.mrpool.eightball.game

/** Everything the rules engine needs to know about a shot that has come to rest. */
class ShotEvents {
    /** Number of the first object ball the cue ball touched, or null for a complete miss. */
    var firstContact: Int? = null

    /** Balls pocketed, in the order they dropped. */
    val pocketed: MutableList<Int> = mutableListOf()

    /** True when any ball touched a cushion after the cue ball made contact. */
    var cushionAfterContact: Boolean = false

    /** True when the cue ball was pocketed (a scratch). */
    val cueBallPocketed: Boolean get() = pocketed.contains(0)

    /** True when at least one ball left the playing surface and had to be re-spotted. */
    var ballOffTable: Boolean = false

    /** Numbers of every ball that touched a cushion during the shot (used for break legality). */
    val railContactBalls: MutableSet<Int> = mutableSetOf()

    internal var contactHappened: Boolean = false

    fun pocketedExcludingCue(): List<Int> = pocketed.filter { it != 0 }
}
