package com.mrpool.eightball.net

import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.mrpool.eightball.game.GameSnapshot

/**
 * [MatchTransport] over a Firebase Realtime Database room.
 *
 * The room is the whole match: a list of moves, a fingerprint per move per device, and one
 * snapshot slot the host writes into when the two tables disagree. Nothing here understands
 * pool — it moves maps in and out of the database and hands them to [OnlineMatch].
 *
 * Callbacks arrive on the main thread, which is where the game loop applies them.
 */
class FirebaseTransport(
    private val room: DatabaseReference,
    private val deviceId: String
) : MatchTransport {

    override var onRemoteMove: ((Int, MatchMove) -> Unit)? = null
    override var onRemoteChecksum: ((Int, String) -> Unit)? = null
    override var onSnapshot: ((Int, GameSnapshot) -> Unit)? = null
    override var onOpponentGone: (() -> Unit)? = null

    private val moves = room.child("moves")
    private val hashes = room.child("hash")
    private val snapshotSlot = room.child("snap")
    private val presence = room.child("presence")

    private val moveListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previous: String?) {
            val index = snapshot.key?.toIntOrNull() ?: return
            val move = MatchProtocol.decodeMove(snapshot.asMap()) ?: return
            onRemoteMove?.invoke(index, move)
        }

        override fun onChildChanged(snapshot: DataSnapshot, previous: String?) = Unit
        override fun onChildRemoved(snapshot: DataSnapshot) = Unit
        override fun onChildMoved(snapshot: DataSnapshot, previous: String?) = Unit
        override fun onCancelled(error: DatabaseError) = warn("moves", error)
    }

    private val hashListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previous: String?) = readHash(snapshot)
        override fun onChildChanged(snapshot: DataSnapshot, previous: String?) = readHash(snapshot)
        override fun onChildRemoved(snapshot: DataSnapshot) = Unit
        override fun onChildMoved(snapshot: DataSnapshot, previous: String?) = Unit
        override fun onCancelled(error: DatabaseError) = warn("hashes", error)

        private fun readHash(snapshot: DataSnapshot) {
            val index = snapshot.key?.toIntOrNull() ?: return
            // Each move's node holds one fingerprint per device; ours is not news.
            for (child in snapshot.children) {
                if (child.key == deviceId) continue
                val value = child.getValue(String::class.java) ?: continue
                onRemoteChecksum?.invoke(index, value)
            }
        }
    }

    private val snapshotListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val map = snapshot.asMap() ?: return
            val index = (map["index"] as? Long)?.toInt() ?: return
            val table = MatchProtocol.decodeSnapshot(map) ?: return
            onSnapshot?.invoke(index, table)
        }

        override fun onCancelled(error: DatabaseError) = warn("snapshot", error)
    }

    private val presenceListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            // Two entries means both players are here. One means we are on our own.
            val others = snapshot.children.count { it.key != deviceId }
            if (others == 0 && seenOpponent) onOpponentGone?.invoke()
            if (others > 0) seenOpponent = true
        }

        override fun onCancelled(error: DatabaseError) = warn("presence", error)
    }

    private var seenOpponent = false

    init {
        // Drop out of the room by itself if this device loses the network or is killed.
        presence.child(deviceId).setValue(true)
        presence.child(deviceId).onDisconnect().removeValue()

        moves.addChildEventListener(moveListener)
        hashes.addChildEventListener(hashListener)
        snapshotSlot.addValueEventListener(snapshotListener)
        presence.addValueEventListener(presenceListener)
    }

    override fun sendMove(index: Int, move: MatchMove) {
        moves.child(index.toString()).setValue(MatchProtocol.encode(move))
    }

    override fun sendChecksum(index: Int, checksum: String) {
        hashes.child(index.toString()).child(deviceId).setValue(checksum)
    }

    override fun sendSnapshot(index: Int, snapshot: GameSnapshot) {
        snapshotSlot.setValue(MatchProtocol.encode(snapshot) + ("index" to index))
    }

    override fun close() {
        moves.removeEventListener(moveListener)
        hashes.removeEventListener(hashListener)
        snapshotSlot.removeEventListener(snapshotListener)
        presence.removeEventListener(presenceListener)
        presence.child(deviceId).removeValue()
    }

    private fun warn(what: String, error: DatabaseError) {
        Log.w(TAG, "lost the $what listener: ${error.message}")
    }

    private companion object {
        const val TAG = "FirebaseTransport"
    }
}

/** Realtime Database hands back nested maps; this is the cast, in one place. */
@Suppress("UNCHECKED_CAST")
internal fun DataSnapshot.asMap(): Map<String, Any?>? = value as? Map<String, Any?>
