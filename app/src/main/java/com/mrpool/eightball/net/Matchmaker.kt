package com.mrpool.eightball.net

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.mrpool.eightball.game.Seat
import kotlin.random.Random

/** A room both players have joined, ready to play. */
data class MatchRoom(
    val code: String,
    val seat: Seat,
    val isHost: Boolean,
    val opponentName: String,
    /** Both devices rack from this, so they start from the identical table. */
    val seed: Int,
    val transport: MatchTransport
)

/** What the matchmaking screen is currently doing. */
sealed interface Matchmaking {
    data object Idle : Matchmaking
    data class Hosting(val code: String) : Matchmaking
    data object Searching : Matchmaking
    data class Ready(val room: MatchRoom) : Matchmaking
    data class Failed(val reason: String) : Matchmaking
}

/**
 * Puts two players in the same room.
 *
 * Two ways in: host a private room and read the code out to a friend, or take a seat in the
 * public queue and be paired with whoever is waiting. Joining is done inside a transaction,
 * so when two players reach for the same room only one of them gets it.
 */
class Matchmaker(
    private val database: FirebaseDatabase,
    private val deviceId: String,
    private val playerName: String
) {

    private val rooms: DatabaseReference get() = database.reference.child("rooms")
    private val queue: DatabaseReference get() = database.reference.child("queue")

    private var watching: DatabaseReference? = null
    private var watcher: ValueEventListener? = null

    /**
     * Opens a private room and waits for someone to join it.
     *
     * @param onUpdate called with [Matchmaking.Hosting] immediately, then [Matchmaking.Ready]
     */
    fun host(onUpdate: (Matchmaking) -> Unit) {
        val code = RoomCode.generate()
        val seed = Random.nextInt()
        val room = rooms.child(code)

        room.setValue(
            mapOf(
                "hostId" to deviceId,
                "hostName" to playerName,
                "seed" to seed,
                "createdAt" to ServerValue.TIMESTAMP,
                "version" to MatchProtocol.VERSION
            )
        ).addOnFailureListener { error ->
            onUpdate(Matchmaking.Failed(error.message ?: "could not open the room"))
        }

        onUpdate(Matchmaking.Hosting(code))
        awaitGuest(code, seed, onUpdate)
    }

    /** Waits for a guest to write their name into the room we opened. */
    private fun awaitGuest(code: String, seed: Int, onUpdate: (Matchmaking) -> Unit) {
        val room = rooms.child(code)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val guestId = snapshot.child("guestId").getValue(String::class.java) ?: return
                if (guestId == deviceId) return
                val guestName = snapshot.child("guestName").getValue(String::class.java) ?: "Player"
                stopWatching()
                onUpdate(
                    Matchmaking.Ready(
                        MatchRoom(
                            code = code,
                            seat = Seat.ONE,
                            isHost = true,
                            opponentName = guestName,
                            seed = seed,
                            transport = FirebaseTransport(room, deviceId)
                        )
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(Matchmaking.Failed(error.message))
            }
        }
        watching = room
        watcher = listener
        room.addValueEventListener(listener)
    }

    /**
     * Joins a room by its code.
     *
     * The seat is claimed in a transaction, so a room that someone else reached first is
     * reported as full rather than quietly overwriting them.
     */
    fun join(rawCode: String, onUpdate: (Matchmaking) -> Unit) {
        val code = RoomCode.normalise(rawCode)
        if (!RoomCode.isValid(code)) {
            onUpdate(Matchmaking.Failed("That code does not look right"))
            return
        }
        val room = rooms.child(code)

        room.runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                val hostId = current.child("hostId").getValue(String::class.java)
                    ?: return Transaction.abort()
                if (hostId == deviceId) return Transaction.abort()
                val existingGuest = current.child("guestId").getValue(String::class.java)
                if (existingGuest != null && existingGuest != deviceId) return Transaction.abort()
                current.child("guestId").value = deviceId
                current.child("guestName").value = playerName
                return Transaction.success(current)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
                if (error != null) {
                    onUpdate(Matchmaking.Failed(error.message))
                    return
                }
                if (!committed || data == null) {
                    onUpdate(Matchmaking.Failed("That room is full or does not exist"))
                    return
                }
                val seed = data.child("seed").getValue(Int::class.java) ?: 0
                val hostName = data.child("hostName").getValue(String::class.java) ?: "Player"
                onUpdate(
                    Matchmaking.Ready(
                        MatchRoom(
                            code = code,
                            seat = Seat.TWO,
                            isHost = false,
                            opponentName = hostName,
                            seed = seed,
                            transport = FirebaseTransport(room, deviceId)
                        )
                    )
                )
            }
        })
    }

    /**
     * Takes a seat in the public queue.
     *
     * Whoever arrives second finds the one already waiting, claims them in a transaction and
     * opens a room; the one who was waiting is told the code through their own queue entry.
     */
    fun quickMatch(onUpdate: (Matchmaking) -> Unit) {
        onUpdate(Matchmaking.Searching)
        queue.orderByChild("createdAt").limitToFirst(QUEUE_SCAN)
            .get()
            .addOnSuccessListener { snapshot ->
                val candidate = snapshot.children.firstOrNull {
                    it.key != deviceId && it.child("room").value == null
                }
                if (candidate == null) {
                    waitInQueue(onUpdate)
                } else {
                    claim(candidate.key!!, onUpdate)
                }
            }
            .addOnFailureListener { error ->
                onUpdate(Matchmaking.Failed(error.message ?: "could not reach the queue"))
            }
    }

    /** Adds this device to the queue and waits to be picked up. */
    private fun waitInQueue(onUpdate: (Matchmaking) -> Unit) {
        val entry = queue.child(deviceId)
        entry.setValue(mapOf("name" to playerName, "createdAt" to ServerValue.TIMESTAMP))
        entry.onDisconnect().removeValue()

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val code = snapshot.child("room").getValue(String::class.java) ?: return
                val seed = snapshot.child("seed").getValue(Int::class.java) ?: 0
                val opponent = snapshot.child("opponent").getValue(String::class.java) ?: "Player"
                stopWatching()
                entry.removeValue()
                onUpdate(
                    Matchmaking.Ready(
                        MatchRoom(
                            code = code,
                            seat = Seat.TWO,
                            isHost = false,
                            opponentName = opponent,
                            seed = seed,
                            transport = FirebaseTransport(rooms.child(code), deviceId)
                        )
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onUpdate(Matchmaking.Failed(error.message))
            }
        }
        watching = entry
        watcher = listener
        entry.addValueEventListener(listener)
    }

    /** Picks a waiting player out of the queue and opens a room for the two of us. */
    private fun claim(opponentId: String, onUpdate: (Matchmaking) -> Unit) {
        val code = RoomCode.generate()
        val seed = Random.nextInt()
        val entry = queue.child(opponentId)

        entry.runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                if (current.value == null) return Transaction.abort()
                if (current.child("room").value != null) return Transaction.abort()
                current.child("room").value = code
                current.child("seed").value = seed
                current.child("opponent").value = playerName
                return Transaction.success(current)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
                if (error != null || !committed || data == null) {
                    // Someone beat us to them. Go round again rather than giving up.
                    quickMatch(onUpdate)
                    return
                }
                val opponentName = data.child("name").getValue(String::class.java) ?: "Player"
                val room = rooms.child(code)
                room.setValue(
                    mapOf(
                        "hostId" to deviceId,
                        "hostName" to playerName,
                        "guestId" to opponentId,
                        "guestName" to opponentName,
                        "seed" to seed,
                        "createdAt" to ServerValue.TIMESTAMP,
                        "version" to MatchProtocol.VERSION
                    )
                )
                queue.child(deviceId).removeValue()
                onUpdate(
                    Matchmaking.Ready(
                        MatchRoom(
                            code = code,
                            seat = Seat.ONE,
                            isHost = true,
                            opponentName = opponentName,
                            seed = seed,
                            transport = FirebaseTransport(room, deviceId)
                        )
                    )
                )
            }
        })
    }

    /** Leaves the queue or stops waiting for a guest. */
    fun cancel() {
        stopWatching()
        queue.child(deviceId).removeValue()
    }

    private fun stopWatching() {
        val reference = watching
        val listener = watcher
        if (reference != null && listener != null) reference.removeEventListener(listener)
        watching = null
        watcher = null
    }

    private companion object {
        /** How far down the queue to look for someone to play. */
        const val QUEUE_SCAN = 20
    }
}
