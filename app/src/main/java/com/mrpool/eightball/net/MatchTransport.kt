package com.mrpool.eightball.net

import com.mrpool.eightball.game.GameSnapshot

/**
 * The pipe between two players.
 *
 * [OnlineMatch] talks to this and nothing else, so the lockstep logic can be driven by a
 * pair of in-memory fakes in a unit test exactly as it is driven by a realtime database in
 * the real app.
 *
 * Every send is fire and forget: the transport is expected to retry or fail loudly on its
 * own, and moves carry their own index so a duplicate delivery is harmless.
 */
interface MatchTransport {

    /** Publishes a move made locally. [index] is its position in the match's move list. */
    fun sendMove(index: Int, move: MatchMove)

    /** Publishes this device's fingerprint of the table after move [index]. */
    fun sendChecksum(index: Int, checksum: String)

    /** Host only: publishes the authoritative table after a disagreement. */
    fun sendSnapshot(index: Int, snapshot: GameSnapshot)

    /** Stops listening and releases whatever the implementation holds open. */
    fun close()

    /** Set by [OnlineMatch]; called when the opponent's move arrives. */
    var onRemoteMove: ((index: Int, move: MatchMove) -> Unit)?

    /** Set by [OnlineMatch]; called when the opponent's fingerprint arrives. */
    var onRemoteChecksum: ((index: Int, checksum: String) -> Unit)?

    /** Set by [OnlineMatch]; called when the host publishes a repair snapshot. */
    var onSnapshot: ((index: Int, snapshot: GameSnapshot) -> Unit)?

    /** Set by [OnlineMatch]; called when the opponent disconnects or forfeits. */
    var onOpponentGone: (() -> Unit)?
}
