package com.mrpool.eightball.net

import com.mrpool.eightball.game.GamePhase
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.Seat
import com.mrpool.eightball.game.Vec2

/** What an online match is currently doing, for the HUD to report. */
enum class OnlineStatus {
    /** Normal play. */
    PLAYING,

    /** The tables disagreed and the guest is waiting for the host's repair snapshot. */
    REPAIRING,

    /** The opponent left, forfeited or dropped off the network. */
    OPPONENT_GONE
}

/**
 * Keeps two devices playing the same game of pool.
 *
 * Nothing about the table is streamed. Each move — a shot or a cue ball placement — is a
 * handful of numbers, and both devices replay it through the same deterministic physics.
 * That works because the simulation runs on a fixed internal timestep that does not depend
 * on either device's frame rate.
 *
 * Because floating point on two different CPUs is not guaranteed to agree to the last bit,
 * every completed move is followed by a fingerprint exchange. If the fingerprints differ,
 * the host's table wins: it publishes a snapshot and the guest adopts it. That repair is
 * what stops a hairline difference on the break from becoming two completely different
 * games by the endgame.
 *
 * Holds no Android or database types, so a pair of these can be wired to each other in a
 * unit test and made to play a whole match.
 */
class OnlineMatch(
    val session: GameSession,
    val localSeat: Seat,
    val isHost: Boolean,
    private val transport: MatchTransport
) {

    /** How many moves have been applied to this table. Also the index of the next one. */
    var appliedMoves: Int = 0
        private set

    var status: OnlineStatus = OnlineStatus.PLAYING
        private set

    /** Counts repairs, so a match that keeps drifting can be reported rather than hidden. */
    var repairs: Int = 0
        private set

    val isLocalTurn: Boolean
        get() = status == OnlineStatus.PLAYING &&
            session.currentSeat == localSeat &&
            session.phase != GamePhase.GAME_OVER

    val remoteSeat: Seat get() = if (localSeat == Seat.ONE) Seat.TWO else Seat.ONE

    /** Moves that arrived before this device was ready for them. */
    private val buffered = HashMap<Int, MatchMove>()
    private val localChecksums = HashMap<Int, String>()
    private val remoteChecksums = HashMap<Int, String>()

    /** Index of the shot currently rolling, or null when the table is at rest. */
    private var shotInFlight: Int? = null

    /** Frames the move at the head of the buffer has been turned away for. */
    private var refusals = 0

    init {
        transport.onRemoteMove = { index, move -> receiveMove(index, move) }
        transport.onRemoteChecksum = { index, checksum ->
            remoteChecksums[index] = checksum
            compare(index)
        }
        transport.onSnapshot = { index, snapshot ->
            // Only the guest repairs; the host's table is the authority by definition.
            if (!isHost) {
                session.restore(snapshot)
                appliedMoves = index + 1
                shotInFlight = null
                refusals = 0
                buffered.keys.retainAll { it >= appliedMoves }
                localChecksums.clear()
                remoteChecksums.clear()
                repairs++
                status = OnlineStatus.PLAYING
            }
        }
        transport.onOpponentGone = { status = OnlineStatus.OPPONENT_GONE }
    }

    // ------------------------------------------------------------------- local moves

    /**
     * Takes the local player's shot.
     *
     * [power] must be the final value the physics will use, with any cue bonus already
     * applied: the two players may have different cues equipped, and a multiplier applied
     * after this point would be applied on one device and not the other.
     *
     * @return false when it is not this device's turn
     */
    fun submitShot(angle: Float, power: Float, sideSpin: Float, topSpin: Float): Boolean {
        if (!isLocalTurn || !session.canAim) return false
        return submit(MatchMove.Shoot(localSeat, angle, power, sideSpin, topSpin))
    }

    /** Places the cue ball after a foul. Returns false when it is not this device's turn. */
    fun submitPlacement(position: Vec2): Boolean {
        if (!isLocalTurn || session.phase != GamePhase.BALL_IN_HAND) return false
        if (!session.isValidCueBallPosition(position)) return false
        return submit(MatchMove.PlaceCueBall(localSeat, position.x, position.y))
    }

    /** Gives the game up, so the opponent is not left waiting on a still table. */
    fun forfeit() {
        transport.sendMove(appliedMoves, MatchMove.Forfeit(localSeat))
        status = OnlineStatus.OPPONENT_GONE
    }

    private fun submit(move: MatchMove): Boolean {
        val index = appliedMoves
        // Sent first so the opponent starts watching the same shot as early as possible.
        transport.sendMove(index, move)
        return apply(index, move)
    }

    // ------------------------------------------------------------------ remote moves

    private fun receiveMove(index: Int, move: MatchMove) {
        // Before the index test, not after it. A forfeit is not a move in the game, it is
        // the end of one, and the leaver stamps it with their own move count -- which is
        // behind ours whenever something of ours is still in flight the other way. Treated
        // as a stale duplicate it was dropped, and the player left behind sat at a still
        // table waiting for a game nobody was playing.
        if (move is MatchMove.Forfeit) {
            status = OnlineStatus.OPPONENT_GONE
            return
        }
        if (index < appliedMoves) return          // already applied, or a duplicate delivery
        // Our own moves come back to us from the database; we already applied them.
        if (move.seat == localSeat) return
        buffered[index] = move
        drainBuffer()
    }

    /**
     * Applies whatever buffered moves are now due, in order.
     *
     * A move is only taken out of the buffer once it has actually been applied. It used to
     * be removed first: a move this table could not take yet -- or could not take at all --
     * was gone, nothing retried it, `appliedMoves` never advanced past it, and the match
     * stopped dead with both players looking at a still table and no error anywhere.
     */
    private fun drainBuffer() {
        while (shotInFlight == null) {
            val index = appliedMoves
            val next = buffered[index] ?: return
            if (!apply(index, next)) {
                refused(index)
                return
            }
            buffered.remove(index)
            refusals = 0
        }
    }

    /**
     * Called each time the move at the head of the buffer is turned away.
     *
     * Retrying covers the case where this table simply has not caught up yet, which clears
     * in a frame or two. A move that is still being refused after that means the two tables
     * disagree about the state of the game -- the sender could make this move and we cannot
     * -- and retrying forever would hang the match.
     *
     * So say so, with a fingerprint for that move: ours comes from a table that has not
     * applied it and theirs from one that has, so the two are certain to differ and the
     * host sends the repair that unsticks us. Only the guest does this. The host's table is
     * the authority by definition and has nothing to be repaired from.
     */
    private fun refused(index: Int) {
        refusals++
        if (isHost || refusals != REFUSALS_BEFORE_REPAIR) return
        status = OnlineStatus.REPAIRING
        publishChecksum(index)
    }

    // ----------------------------------------------------------------------- applying

    private fun apply(index: Int, move: MatchMove): Boolean {
        // A move from the wrong seat is a bug or a tampered client, never a legal move.
        if (move.seat != session.currentSeat) return false

        when (move) {
            is MatchMove.Shoot -> {
                if (!session.canAim) return false
                session.shoot(Vec2.fromAngle(move.angle), move.power, move.sideSpin, move.topSpin)
                appliedMoves = index + 1
                shotInFlight = index
            }

            is MatchMove.PlaceCueBall -> {
                val asked = Vec2(move.x, move.y)
                // The sender's table said this spot was clear. Ours can disagree by a hair:
                // the test is a threshold against every other ball's position, and two
                // devices are not promised the same last bit of a float -- which is the
                // whole reason the fingerprints exist. Refusing would stop the match over a
                // fraction of a millimetre, so the ball goes to the nearest spot this table
                // does allow, and the fingerprint that follows catches whatever is left.
                if (!session.placeCueBall(asked) &&
                    !session.placeCueBall(session.nearestValidCueBallPosition(asked))
                ) {
                    return false
                }
                appliedMoves = index + 1
                publishChecksum(index)
            }

            is MatchMove.Forfeit -> {
                status = OnlineStatus.OPPONENT_GONE
                return true
            }
        }
        return true
    }

    // -------------------------------------------------------------------- per frame

    /** Drives the table. Call once per frame with the frame time, like a local match. */
    fun update(dt: Float) {
        session.update(dt)

        val rolling = shotInFlight
        if (rolling != null && !session.isShooting) {
            shotInFlight = null
            publishChecksum(rolling)
        }
        drainBuffer()
    }

    private fun publishChecksum(index: Int) {
        val checksum = StateChecksum.of(session)
        localChecksums[index] = checksum
        transport.sendChecksum(index, checksum)
        compare(index)
    }

    /**
     * Compares the two fingerprints for one move. A disagreement means the tables have
     * drifted, and the host repairs it.
     */
    private fun compare(index: Int) {
        val mine = localChecksums[index] ?: return
        val theirs = remoteChecksums[index] ?: return
        if (mine == theirs) {
            // Nothing before this can still be in dispute once a later move agrees.
            localChecksums.keys.retainAll { it > index }
            remoteChecksums.keys.retainAll { it > index }
            return
        }
        if (isHost) {
            transport.sendSnapshot(index, session.snapshot())
            repairs++
        } else {
            status = OnlineStatus.REPAIRING
        }
    }

    private companion object {
        /**
         * Frames of being turned away before a move is treated as a disagreement rather
         * than as this table running a step behind. Half a second at sixty frames: long
         * enough that catching up is never mistaken for drifting apart.
         */
        const val REFUSALS_BEFORE_REPAIR = 30
    }

    fun close() {
        transport.onRemoteMove = null
        transport.onRemoteChecksum = null
        transport.onSnapshot = null
        transport.onOpponentGone = null
        transport.close()
    }
}
