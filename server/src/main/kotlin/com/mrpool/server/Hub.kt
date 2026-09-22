package com.mrpool.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** One connected player, as far as the hub is concerned. */
interface Peer {
    val id: String
    suspend fun send(text: String)
}

/** Two players and the table they share. */
private class Room(
    val code: String,
    val host: Peer,
    val seed: Int
) {
    var guest: Peer? = null

    fun other(peer: Peer): Peer? = when (peer.id) {
        host.id -> guest
        guest?.id -> host
        else -> null
    }

    fun holds(peer: Peer): Boolean = host.id == peer.id || guest?.id == peer.id
}

/**
 * Everything the server actually does.
 *
 * It pairs players and forwards messages between them. It knows nothing whatsoever about
 * pool: a move's contents arrive as an opaque blob and leave untouched, so the rules of the
 * game live in one place — the app — and the server never needs redeploying when they change.
 *
 * Kept free of Ktor types so the pairing logic can be tested directly, with the WebSocket
 * layer tested separately on top of it.
 */
class Hub(private val random: Random = Random.Default) {

    private val mutex = Mutex()
    private val rooms = HashMap<String, Room>()
    private val roomOf = HashMap<String, String>()

    /** The player waiting for a quick match, if any. */
    private var waiting: Peer? = null

    /** Names, so each player can be told who they are up against. */
    private val names = HashMap<String, String>()

    /** Who has already reported, so the log cannot be flooded by holding the button down. */
    private val reporters = HashSet<String>()

    val openRooms: Int get() = rooms.size

    suspend fun setName(peer: Peer, name: String) = mutex.withLock {
        names[peer.id] = name.trim().take(16).ifBlank { "Player" }
    }

    private fun nameOf(peer: Peer): String = names[peer.id] ?: "Player"

    /** Opens a private room and tells its host the code. */
    suspend fun createRoom(peer: Peer) {
        val code = mutex.withLock {
            leaveLocked(peer)
            var candidate = RoomCode.generate(random)
            var guard = 0
            // A collision would put two pairs of players in one room, so keep looking.
            while (rooms.containsKey(candidate) && guard < 50) {
                candidate = RoomCode.generate(random)
                guard++
            }
            val room = Room(candidate, peer, random.nextInt())
            rooms[candidate] = room
            roomOf[peer.id] = candidate
            candidate
        }
        peer.send(Messages.created(code))
    }

    /**
     * Joins a room by code.
     *
     * Both players are told to start, each with their own seat, and both are given the same
     * seed so they rack the identical table.
     */
    suspend fun joinRoom(peer: Peer, rawCode: String) {
        val code = RoomCode.normalise(rawCode)
        data class Pairing(val host: Peer, val guest: Peer, val seed: Int, val code: String)

        val pairing: Pairing? = mutex.withLock {
            if (!RoomCode.isValid(code)) return@withLock null
            val room = rooms[code] ?: return@withLock null
            if (room.host.id == peer.id) return@withLock null
            if (room.guest != null) return@withLock null
            leaveLocked(peer)
            room.guest = peer
            roomOf[peer.id] = code
            Pairing(room.host, peer, room.seed, code)
        }

        if (pairing == null) {
            peer.send(Messages.error("That room is full or does not exist"))
            return
        }
        announce(pairing.host, pairing.guest, pairing.seed, pairing.code)
    }

    /**
     * Takes a seat in the quick match queue, or pairs with whoever was already waiting.
     */
    suspend fun quickMatch(peer: Peer) {
        data class Pairing(val host: Peer, val guest: Peer, val seed: Int, val code: String)

        val pairing: Pairing? = mutex.withLock {
            leaveLocked(peer)
            val partner = waiting
            if (partner == null || partner.id == peer.id) {
                waiting = peer
                return@withLock null
            }
            waiting = null
            val code = RoomCode.generate(random)
            val room = Room(code, partner, random.nextInt())
            room.guest = peer
            rooms[code] = room
            roomOf[partner.id] = code
            roomOf[peer.id] = code
            Pairing(partner, peer, room.seed, code)
        }

        if (pairing == null) {
            peer.send(Messages.searching())
            return
        }
        announce(pairing.host, pairing.guest, pairing.seed, pairing.code)
    }

    private suspend fun announce(host: Peer, guest: Peer, seed: Int, code: String) {
        host.send(Messages.start(code, seat = "ONE", isHost = true, seed = seed, opponent = nameOf(guest)))
        guest.send(Messages.start(code, seat = "TWO", isHost = false, seed = seed, opponent = nameOf(host)))
    }

    /**
     * Forwards one message to the other player in the room.
     *
     * [body] is whatever the client sent and is not inspected: the server relays pool moves
     * without knowing what a pool move is.
     */
    suspend fun relay(peer: Peer, body: String) {
        val other = mutex.withLock {
            val code = roomOf[peer.id] ?: return@withLock null
            rooms[code]?.other(peer)
        }
        other?.send(Messages.peer(body))
    }

    /**
     * Writes a report down.
     *
     * That is the whole of it, and it is worth being plain about why. There are no accounts
     * here, so there is no one to ban: a player is a name typed into a box and a socket that
     * closes when they leave. What this gives is a record in the log that somebody can read.
     * The part that actually protects the player who reported is the mute in their own app,
     * which needs no server at all.
     *
     * Returns the line to write, or null when the reporter is not in a room.
     */
    suspend fun report(peer: Peer, lines: List<String>): String? {
        val (code, about) = mutex.withLock {
            val code = roomOf[peer.id] ?: return@withLock null
            // One report per player per room. A second one says nothing the first did not.
            if (!reporters.add(peer.id)) return@withLock null
            code to rooms[code]?.other(peer)?.let { nameOf(it) }
        } ?: return null

        val quoted = lines.take(Messages.MAX_REPORT_LINES)
            .joinToString(" | ") { it.replace("\n", " ").take(MAX_REPORTED_LENGTH) }
        return "REPORT room=$code by=${nameOf(peer)} about=${about ?: "(gone)"} said=[$quoted]"
    }

    /** Leaves the queue or the room, telling the opponent they are on their own. */
    suspend fun leave(peer: Peer) {
        val orphan = mutex.withLock { leaveLocked(peer) }
        orphan?.send(Messages.gone())
    }

    /**
     * Removes [peer] from whatever it is in and returns the opponent left behind, if any.
     * The caller holds the lock; sending happens outside it.
     */
    private fun leaveLocked(peer: Peer): Peer? {
        if (waiting?.id == peer.id) waiting = null
        reporters.remove(peer.id)

        val code = roomOf.remove(peer.id) ?: return null
        val room = rooms[code] ?: return null
        if (!room.holds(peer)) return null

        val other = room.other(peer)
        // A room only ever holds two people, so losing one closes it.
        rooms.remove(code)
        other?.let { roomOf.remove(it.id) }
        return other
    }

    private companion object {
        /** Long enough to see what was said, short enough that the log cannot be flooded. */
        const val MAX_REPORTED_LENGTH = 200
    }

    /** True when [peer] is in a room with someone. Used by the tests and the health page. */
    suspend fun isPaired(peer: Peer): Boolean = mutex.withLock {
        val code = roomOf[peer.id] ?: return@withLock false
        rooms[code]?.other(peer) != null
    }
}
