package com.mrpool.eightball.game

import kotlin.random.Random

enum class GamePhase { BREAK, AIMING, SHOOTING, BALL_IN_HAND, GAME_OVER }

enum class Seat { ONE, TWO }

data class PlayerState(
    val name: String,
    val isRobot: Boolean,
    var group: BallGroup? = null
)

/** What happened on the shot that just finished, in a form the UI can narrate. */
data class ShotResult(
    val shooter: Seat,
    val foul: Boolean,
    val foulReason: String?,
    val pocketed: List<Int>,
    val keepsTurn: Boolean,
    val winner: Seat?,
    val message: String
)

/**
 * One game of 8-ball: the table, whose turn it is, the group each player owns and every
 * rule that decides those things. Rendering and the robot both read from here; nothing
 * else mutates the ball list.
 */
class GameSession(
    playerOne: PlayerState,
    playerTwo: PlayerState,
    cloth: ClothProperties = ClothProperties.TOURNAMENT,
    private val random: Random = Random.Default
) {

    val players: Array<PlayerState> = arrayOf(playerOne, playerTwo)

    val physics: PoolPhysics = PoolPhysics(Rack.build(random), cloth)

    var currentSeat: Seat = Seat.ONE
        private set

    var phase: GamePhase = GamePhase.BREAK
        private set

    var tableOpen: Boolean = true
        private set

    var winner: Seat? = null
        private set

    /** Set while the incoming player may place the cue ball behind the head string. */
    var behindHeadString: Boolean = false
        private set

    var statusMessage: String = "Break them!"
        private set

    var lastResult: ShotResult? = null
        private set

    /** First ball the cue ball touched on the last shot, for diagnostics and replays. */
    var lastFirstContact: Int? = null
        private set

    private var isBreakShot: Boolean = true
    private var events: ShotEvents = ShotEvents()

    val currentPlayer: PlayerState get() = players[currentSeat.ordinal]

    val isRobotTurn: Boolean get() = currentPlayer.isRobot && phase != GamePhase.GAME_OVER

    fun playerAt(seat: Seat): PlayerState = players[seat.ordinal]

    fun groupOf(seat: Seat): BallGroup? = players[seat.ordinal].group

    fun opponentOf(seat: Seat): Seat = if (seat == Seat.ONE) Seat.TWO else Seat.ONE

    fun ballsRemaining(group: BallGroup): Int =
        physics.balls.count { !it.pocketed && it.group == group }

    /** Numbers the current player is allowed to hit first. */
    fun legalTargets(seat: Seat = currentSeat): List<Int> {
        val group = groupOf(seat)
        val live = physics.balls.filter { !it.pocketed && !it.isCue }
        return when {
            tableOpen -> live.filter { it.number != 8 }.map { it.number }
                .ifEmpty { live.map { it.number } }
            group != null && ballsRemaining(group) > 0 -> live.filter { it.group == group }.map { it.number }
            else -> live.filter { it.number == 8 }.map { it.number }
        }
    }

    val isShooting: Boolean get() = phase == GamePhase.SHOOTING

    val canAim: Boolean get() = phase == GamePhase.AIMING || phase == GamePhase.BREAK

    // -------------------------------------------------------------- cue ball placement

    fun isValidCueBallPosition(p: Vec2): Boolean {
        if (!TableGeometry.isInsideCushions(p)) return false
        if (behindHeadString && p.x > TableGeometry.HEAD_STRING_X) return false
        if (TableGeometry.pockets.any { p.distanceTo(it.center) < it.radius + TableGeometry.BALL_RADIUS }) {
            return false
        }
        return physics.balls.none {
            !it.pocketed && !it.isCue &&
                it.position.distanceTo(p) < TableGeometry.BALL_DIAMETER * 1.02f
        }
    }

    /** Drops the cue ball at [p] if that spot is legal. Returns true when placed. */
    fun placeCueBall(p: Vec2): Boolean {
        if (phase != GamePhase.BALL_IN_HAND) return false
        if (!isValidCueBallPosition(p)) return false
        val cue = physics.cueBall ?: return false
        cue.position = p
        cue.pocketed = false
        cue.stop()
        phase = if (isBreakShot) GamePhase.BREAK else GamePhase.AIMING
        behindHeadString = false
        statusMessage = "${currentPlayer.name} to shoot"
        return true
    }

    /** Finds a legal spot near [p] so a sloppy drag still places the ball somewhere sane. */
    fun nearestValidCueBallPosition(p: Vec2): Vec2 {
        if (isValidCueBallPosition(p)) return p
        var best: Vec2? = null
        var bestDist = Float.MAX_VALUE
        var radius = TableGeometry.BALL_RADIUS
        while (radius < 0.6f) {
            var a = 0
            while (a < 24) {
                val candidate = TableGeometry.clampInsideCushions(
                    p + Vec2.fromAngle((a / 24f) * (Math.PI * 2).toFloat(), radius)
                )
                if (isValidCueBallPosition(candidate)) {
                    val d = candidate.distanceTo(p)
                    if (d < bestDist) {
                        bestDist = d
                        best = candidate
                    }
                }
                a++
            }
            if (best != null) return best
            radius += TableGeometry.BALL_RADIUS
        }
        return TableGeometry.clampInsideCushions(p)
    }

    // ------------------------------------------------------------------------ shooting

    /**
     * Takes the shot.
     *
     * @param power 0..1, mapped onto a gentle tap through to a full blooded break.
     */
    fun shoot(direction: Vec2, power: Float, sideSpin: Float = 0f, topSpin: Float = 0f) {
        if (!canAim) return
        val speed = MIN_SHOT_SPEED + power.coerceIn(0f, 1f) * (MAX_SHOT_SPEED - MIN_SHOT_SPEED)
        events = ShotEvents()
        physics.resetClock()
        physics.strike(direction, speed, sideSpin.coerceIn(-1f, 1f), topSpin.coerceIn(-1f, 1f))
        phase = GamePhase.SHOOTING
        statusMessage = ""
    }

    /** Drives the simulation. Call once per frame with the frame time. */
    fun update(dt: Float) {
        if (phase != GamePhase.SHOOTING) return
        physics.advance(dt, events)
        if (!physics.anyBallMoving()) {
            physics.balls.forEach { if (!it.isMoving) it.stop() }
            resolveShot()
        }
    }

    // --------------------------------------------------------------------- rules engine

    private fun resolveShot() {
        val shooter = currentSeat
        val shooterState = players[shooter.ordinal]
        var potted = events.pocketedExcludingCue()
        var foul = false
        var reason: String? = null

        val firstContact = events.firstContact
        lastFirstContact = firstContact
        val firstBall = firstContact?.let { num -> physics.balls.firstOrNull { it.number == num } }

        if (events.cueBallPocketed) {
            foul = true
            reason = "Scratch — cue ball pocketed"
        } else if (firstBall == null) {
            foul = true
            reason = "No ball was hit"
        } else if (!isLegalFirstContact(shooter, firstBall, potted)) {
            foul = true
            reason = "Hit the ${describe(firstBall)} first"
        } else if (potted.isEmpty() && !events.cushionAfterContact) {
            foul = true
            reason = "No ball reached a cushion"
        } else if (isBreakShot && potted.isEmpty() && events.railContactBalls.size < 4) {
            foul = true
            reason = "Illegal break — four balls must reach a cushion"
        }

        // The 8 potted on the break is simply re-spotted; the break is not lost on it.
        if (isBreakShot && potted.contains(8)) {
            respotEightBall()
            potted = potted.filter { it != 8 }
        }

        // The 8 ball settles the game, one way or the other.
        if (potted.contains(8)) {
            val clearedBeforeShot = wasOnTheEightBall(shooter, potted)
            val wonIt = clearedBeforeShot && !foul
            finishGame(if (wonIt) shooter else opponentOf(shooter), shooter, potted, foul, reason)
            return
        }

        if (tableOpen && !foul && potted.isNotEmpty() && !isBreakShot) {
            assignGroups(shooter, potted)
        } else if (tableOpen && !foul && potted.isNotEmpty() && isBreakShot) {
            // Balls potted on the break do not claim a group: the table stays open.
            statusMessage = "Table is still open"
        }

        val ownPotted = potted.count { num ->
            val g = shooterState.group
            if (g == null) num != 8 else physics.balls.firstOrNull { it.number == num }?.group == g
        }

        isBreakShot = false
        val keepsTurn = !foul && ownPotted > 0

        val message = buildString {
            when {
                foul -> append("Foul — ${reason ?: "illegal shot"}. Ball in hand.")
                ownPotted > 0 -> append("${shooterState.name} potted ${potted.joinToString(", ")}")
                potted.isNotEmpty() -> append("Potted ${potted.joinToString(", ")} — turn passes")
                else -> append("No pot — turn passes")
            }
        }

        lastResult = ShotResult(
            shooter = shooter,
            foul = foul,
            foulReason = reason,
            pocketed = potted,
            keepsTurn = keepsTurn,
            winner = null,
            message = message
        )

        if (foul) {
            respotCueBall()
            currentSeat = opponentOf(shooter)
            phase = GamePhase.BALL_IN_HAND
            behindHeadString = false
        } else if (keepsTurn) {
            phase = GamePhase.AIMING
        } else {
            currentSeat = opponentOf(shooter)
            phase = GamePhase.AIMING
        }
        statusMessage = message
    }

    /**
     * Legality is decided by the table as it stood *before* the shot.
     *
     * Reading the live counts here would punish a player for potting the last ball of their
     * group: by the time this runs that ball is already down, and the check would demand
     * that they had hit the 8 instead.
     */
    private fun isLegalFirstContact(seat: Seat, firstBall: Ball, potted: List<Int>): Boolean {
        val group = groupOf(seat)
        return when {
            isBreakShot -> true
            tableOpen -> firstBall.number != 8
            group == null -> firstBall.number != 8
            remainingBefore(group, potted) > 0 -> firstBall.group == group
            else -> firstBall.number == 8
        }
    }

    /** How many balls of [group] were on the table before this shot dropped [potted]. */
    private fun remainingBefore(group: BallGroup, potted: List<Int>): Int {
        val pottedFromGroup = potted.count { number ->
            physics.balls.firstOrNull { it.number == number }?.group == group
        }
        return ballsRemaining(group) + pottedFromGroup
    }

    /** True when the shooter had already cleared their group before this shot. */
    private fun wasOnTheEightBall(seat: Seat, pottedThisShot: List<Int>): Boolean {
        val group = groupOf(seat) ?: return false
        val remainingNow = physics.balls.count { !it.pocketed && it.group == group }
        val pottedOwnThisShot = pottedThisShot.count { num ->
            physics.balls.firstOrNull { it.number == num }?.group == group
        }
        // The group must have been empty before the 8 dropped.
        return remainingNow == 0 && pottedOwnThisShot == 0
    }

    private fun assignGroups(seat: Seat, potted: List<Int>) {
        val decider = potted.firstOrNull { it != 8 } ?: return
        val group = physics.balls.firstOrNull { it.number == decider }?.group ?: return
        if (group != BallGroup.SOLIDS && group != BallGroup.STRIPES) return
        val other = if (group == BallGroup.SOLIDS) BallGroup.STRIPES else BallGroup.SOLIDS
        players[seat.ordinal].group = group
        players[opponentOf(seat).ordinal].group = other
        tableOpen = false
    }

    private fun finishGame(
        won: Seat,
        shooter: Seat,
        potted: List<Int>,
        foul: Boolean,
        reason: String?
    ) {
        winner = won
        phase = GamePhase.GAME_OVER
        val msg = if (won == shooter) {
            "${players[shooter.ordinal].name} sinks the 8 ball and wins!"
        } else {
            "${players[shooter.ordinal].name} loses — ${reason ?: "the 8 ball went down too early"}"
        }
        statusMessage = msg
        lastResult = ShotResult(shooter, foul, reason, potted, false, won, msg)
    }

    /** Puts the 8 back on the foot spot after it drops on the break. */
    private fun respotEightBall() {
        val eight = physics.ball(8) ?: return
        eight.pocketed = false
        eight.stop()
        eight.position = Rack.spotPosition(
            physics.balls.filter { it.number != 8 },
            Vec2(TableGeometry.FOOT_SPOT_X, 0f)
        )
    }

    private fun respotCueBall() {
        val cue = physics.cueBall ?: return
        cue.pocketed = false
        cue.stop()
        cue.position = Rack.spotPosition(
            physics.balls.filter { it.number != 0 },
            Vec2(TableGeometry.HEAD_STRING_X, 0f)
        )
    }

    /** Captures everything another device would need to reproduce this table exactly. */
    fun snapshot(): GameSnapshot = GameSnapshot(
        balls = physics.balls.map {
            BallSnapshot(it.number, it.position.x, it.position.y, it.pocketed)
        },
        currentSeat = currentSeat,
        phase = phase,
        tableOpen = tableOpen,
        playerOneGroup = players[0].group,
        playerTwoGroup = players[1].group,
        winner = winner,
        isBreakShot = isBreakShot
    )

    /**
     * Adopts [snapshot] wholesale, discarding whatever this table currently believes.
     *
     * Used to repair a desync in an online match: the host's table is the authority, and a
     * guest that has drifted takes this rather than carrying on with a different game.
     */
    fun restore(snapshot: GameSnapshot) {
        for (ball in snapshot.balls) {
            val target = physics.ball(ball.number) ?: continue
            target.position = Vec2(ball.x, ball.y)
            target.pocketed = ball.pocketed
            target.stop()
        }
        physics.resetClock()
        currentSeat = snapshot.currentSeat
        phase = snapshot.phase
        tableOpen = snapshot.tableOpen
        players[0].group = snapshot.playerOneGroup
        players[1].group = snapshot.playerTwoGroup
        winner = snapshot.winner
        isBreakShot = snapshot.isBreakShot
        events = ShotEvents()
        statusMessage = ""
    }

    /**
     * Test seam: puts the match straight into a chosen mid game state instead of playing
     * the shots it would take to get there.
     */
    internal fun assignGroupsForTest(seat: Seat, group: BallGroup) {
        val other = if (group == BallGroup.SOLIDS) BallGroup.STRIPES else BallGroup.SOLIDS
        players[seat.ordinal].group = group
        players[opponentOf(seat).ordinal].group = other
        tableOpen = false
        isBreakShot = false
    }

    companion object {
        const val MIN_SHOT_SPEED = 0.55f
        const val MAX_SHOT_SPEED = 9.0f
    }
}

private fun describe(ball: Ball): String = when (ball.group) {
    BallGroup.EIGHT -> "8 ball"
    BallGroup.SOLIDS -> "solids"
    BallGroup.STRIPES -> "stripes"
    BallGroup.CUE -> "cue ball"
}
