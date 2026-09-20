package com.mrpool.eightball.net

import com.mrpool.eightball.game.BallGroup
import com.mrpool.eightball.game.BallSnapshot
import com.mrpool.eightball.game.GamePhase
import com.mrpool.eightball.game.GameSnapshot
import com.mrpool.eightball.game.Seat

/**
 * What one device sends the other.
 *
 * Pool is turn based, so nothing streams: a whole shot is four numbers. Both devices then
 * run the identical deterministic simulation and arrive at the same table. That is the
 * entire netcode, and it is why a match costs a few hundred bytes rather than a few hundred
 * kilobytes.
 */
sealed interface MatchMove {

    /** The seat that made the move, so the receiver can reject a move out of turn. */
    val seat: Seat

    /** A shot: everything the cue did to the cue ball. */
    data class Shoot(
        override val seat: Seat,
        val angle: Float,
        val power: Float,
        val sideSpin: Float,
        val topSpin: Float
    ) : MatchMove

    /** Placing the cue ball after a foul. */
    data class PlaceCueBall(
        override val seat: Seat,
        val x: Float,
        val y: Float
    ) : MatchMove

    /** Leaving the match, so the other player is not left staring at a still table. */
    data class Forfeit(override val seat: Seat) : MatchMove
}

/**
 * Turns moves and table states into plain maps and back.
 *
 * Plain maps because that is what a realtime database stores natively; keeping the
 * conversion here, away from any database type, is what lets the whole protocol be unit
 * tested without a network or an emulator.
 */
object MatchProtocol {

    /** Bumped whenever the wire format changes in a way an older client cannot read. */
    const val VERSION = 1

    private const val KEY_VERSION = "v"
    private const val KEY_TYPE = "type"
    private const val KEY_SEAT = "seat"

    fun encode(move: MatchMove): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            KEY_VERSION to VERSION,
            KEY_SEAT to move.seat.name
        )
        when (move) {
            is MatchMove.Shoot -> {
                base[KEY_TYPE] = "shoot"
                base["angle"] = move.angle.toDouble()
                base["power"] = move.power.toDouble()
                base["sideSpin"] = move.sideSpin.toDouble()
                base["topSpin"] = move.topSpin.toDouble()
            }

            is MatchMove.PlaceCueBall -> {
                base[KEY_TYPE] = "place"
                base["x"] = move.x.toDouble()
                base["y"] = move.y.toDouble()
            }

            is MatchMove.Forfeit -> base[KEY_TYPE] = "forfeit"
        }
        return base
    }

    /** Returns null for anything malformed or from a future version, rather than throwing. */
    fun decodeMove(raw: Map<String, Any?>?): MatchMove? {
        if (raw == null) return null
        val version = number(raw[KEY_VERSION])?.toInt() ?: return null
        if (version != VERSION) return null
        val seat = seatOf(raw[KEY_SEAT]) ?: return null
        return when (raw[KEY_TYPE] as? String) {
            "shoot" -> MatchMove.Shoot(
                seat = seat,
                angle = number(raw["angle"])?.toFloat() ?: return null,
                power = number(raw["power"])?.toFloat() ?: return null,
                sideSpin = number(raw["sideSpin"])?.toFloat() ?: 0f,
                topSpin = number(raw["topSpin"])?.toFloat() ?: 0f
            )

            "place" -> MatchMove.PlaceCueBall(
                seat = seat,
                x = number(raw["x"])?.toFloat() ?: return null,
                y = number(raw["y"])?.toFloat() ?: return null
            )

            "forfeit" -> MatchMove.Forfeit(seat)
            else -> null
        }
    }

    fun encode(snapshot: GameSnapshot): Map<String, Any?> = mapOf(
        KEY_VERSION to VERSION,
        "seat" to snapshot.currentSeat.name,
        "phase" to snapshot.phase.name,
        "tableOpen" to snapshot.tableOpen,
        "groupOne" to snapshot.playerOneGroup?.name,
        "groupTwo" to snapshot.playerTwoGroup?.name,
        "winner" to snapshot.winner?.name,
        "breakShot" to snapshot.isBreakShot,
        "balls" to snapshot.balls.map {
            mapOf(
                "n" to it.number,
                "x" to it.x.toDouble(),
                "y" to it.y.toDouble(),
                "p" to it.pocketed
            )
        }
    )

    @Suppress("UNCHECKED_CAST")
    fun decodeSnapshot(raw: Map<String, Any?>?): GameSnapshot? {
        if (raw == null) return null
        if (number(raw[KEY_VERSION])?.toInt() != VERSION) return null
        val seat = seatOf(raw["seat"]) ?: return null
        val phase = (raw["phase"] as? String)
            ?.let { name -> GamePhase.entries.firstOrNull { it.name == name } } ?: return null
        val rawBalls = raw["balls"] as? List<Map<String, Any?>> ?: return null
        val balls = rawBalls.mapNotNull { ball ->
            val number = number(ball["n"])?.toInt() ?: return@mapNotNull null
            BallSnapshot(
                number = number,
                x = number(ball["x"])?.toFloat() ?: return@mapNotNull null,
                y = number(ball["y"])?.toFloat() ?: return@mapNotNull null,
                pocketed = ball["p"] as? Boolean ?: false
            )
        }
        if (balls.isEmpty()) return null
        return GameSnapshot(
            balls = balls,
            currentSeat = seat,
            phase = phase,
            tableOpen = raw["tableOpen"] as? Boolean ?: true,
            playerOneGroup = groupOf(raw["groupOne"]),
            playerTwoGroup = groupOf(raw["groupTwo"]),
            winner = seatOf(raw["winner"]),
            isBreakShot = raw["breakShot"] as? Boolean ?: false
        )
    }

    /**
     * A realtime database hands numbers back as Long or Double depending on how they were
     * written, so every read goes through here rather than casting and crashing.
     */
    private fun number(value: Any?): Double? = when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Long -> value.toDouble()
        is Int -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun seatOf(value: Any?): Seat? =
        (value as? String)?.let { name -> Seat.entries.firstOrNull { it.name == name } }

    private fun groupOf(value: Any?): BallGroup? =
        (value as? String)?.let { name -> BallGroup.entries.firstOrNull { it.name == name } }
}
