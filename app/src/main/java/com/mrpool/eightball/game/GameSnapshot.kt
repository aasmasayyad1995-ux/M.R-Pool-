package com.mrpool.eightball.game

/** One ball's state, as it travels between two players' devices. */
data class BallSnapshot(
    val number: Int,
    val x: Float,
    val y: Float,
    val pocketed: Boolean
)

/**
 * The complete logical state of a match.
 *
 * Online play keeps two devices in step by replaying the same shots through the same
 * deterministic physics, not by streaming positions. This snapshot is the repair kit for
 * when that goes wrong: if the two tables ever disagree, the host sends one of these and
 * the guest adopts it wholesale.
 *
 * It carries the rules state as well as the balls, because a table that drifted far enough
 * to pot a different ball has a different group assignment and a different player at the
 * table too — restoring the balls alone would leave the two devices disagreeing about whose
 * turn it is.
 */
data class GameSnapshot(
    val balls: List<BallSnapshot>,
    val currentSeat: Seat,
    val phase: GamePhase,
    val tableOpen: Boolean,
    val playerOneGroup: BallGroup?,
    val playerTwoGroup: BallGroup?,
    val winner: Seat?,
    val isBreakShot: Boolean
)
