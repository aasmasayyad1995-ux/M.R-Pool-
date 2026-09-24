package com.mrpool.eightball.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mrpool.eightball.game.GameSnapshot
import com.mrpool.eightball.game.Seat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

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
    data object Connecting : Matchmaking
    data class Hosting(val code: String) : Matchmaking
    data object Searching : Matchmaking
    data class Ready(val room: MatchRoom) : Matchmaking
    data class Failed(val reason: String) : Matchmaking
}

/**
 * One WebSocket to the match server, used first to find an opponent and then to play them.
 *
 * The same connection becomes the [MatchTransport] once a match starts, so there is a single
 * socket, a single thing to close, and no window in which the players are paired but not yet
 * able to talk.
 *
 * The server relays without understanding: the pool specific part of every message rides in
 * `body` and is read only here and in [MatchProtocol].
 */
class MatchConnection(
    private val serverUrl: String,
    private val playerName: String,
    private val onState: (Matchmaking) -> Unit
) : MatchTransport {

    override var onRemoteMove: ((Int, MatchMove) -> Unit)? = null
    override var onRemoteChecksum: ((Int, String) -> Unit)? = null
    override var onSnapshot: ((Int, GameSnapshot) -> Unit)? = null
    override var onOpponentGone: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(ServerWake.ATTEMPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Never time out the socket itself: a player lining up a shot sends nothing for a
        // while, and the server's own ping keeps the connection honest.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    /** Queued until the socket opens, so a tap on Quick Match is never lost to a race. */
    private var pendingRequest: String? = null
    private var open = false
    private var closed = false

    /** For [ServerWake]: how many times the socket has been tried, and since when. */
    private var attempts = 0
    private var firstAttemptAt = 0L

    /** True once a socket has opened, which turns a failure back into a real failure. */
    private var everOpened = false

    /** A retry is on the handler's queue; connect() must not race it with a second socket. */
    private var retryScheduled = false

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = post {
            open = true
            everOpened = true
            webSocket.send(Json.write(mapOf("op" to "hello", "name" to playerName)))
            pendingRequest?.let {
                webSocket.send(it)
                pendingRequest = null
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) = post { receive(text) }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = post {
            Log.w(TAG, "connection failed", t)
            open = false
            if (closed) return@post

            // A socket that never opened may just have arrived while the server was waking
            // up, which shows up as an instant refusal rather than a hang. Ask again before
            // calling it broken; see ServerWake for why that is the only thing that helps.
            val elapsed = System.currentTimeMillis() - firstAttemptAt
            if (!everOpened && ServerWake.shouldRetry(attempts, elapsed)) {
                scheduleRetry()
                return@post
            }

            onState(Matchmaking.Failed(t.message ?: "Could not reach the match server"))
            onOpponentGone?.invoke()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = post {
            open = false
            if (!closed) onOpponentGone?.invoke()
        }
    }

    /** Opens the socket. Safe to call more than once. */
    fun connect() {
        if (socket != null || closed || retryScheduled) return
        onState(Matchmaking.Connecting)
        openSocket()
    }

    private fun openSocket() {
        if (attempts == 0) firstAttemptAt = System.currentTimeMillis()
        attempts++
        val request = Request.Builder().url(serverUrl).build()
        socket = client.newWebSocket(request, listener)
    }

    private fun scheduleRetry() {
        socket = null
        retryScheduled = true
        main.postDelayed({
            retryScheduled = false
            // close() may have run while this was waiting its turn on the queue.
            if (!closed) openSocket()
        }, ServerWake.retryDelayMillis(attempts))
    }

    // ------------------------------------------------------------------ matchmaking

    fun createRoom() = request(mapOf("op" to "create"))

    fun joinRoom(code: String) {
        val tidied = RoomCode.normalise(code)
        if (!RoomCode.isValid(tidied)) {
            onState(Matchmaking.Failed("That code does not look right"))
            return
        }
        request(mapOf("op" to "join", "code" to tidied))
    }

    fun quickMatch() = request(mapOf("op" to "queue"))

    fun cancel() {
        request(mapOf("op" to "leave"))
        onState(Matchmaking.Idle)
    }

    private fun request(envelope: Map<String, Any?>) {
        connect()
        val text = Json.write(envelope)
        val live = socket
        if (open && live != null) live.send(text) else pendingRequest = text
    }

    // ------------------------------------------------------------------- incoming

    private fun receive(text: String) {
        val envelope = Json.readObject(text) ?: return
        when (envelope["op"] as? String) {
            "created" -> (envelope["code"] as? String)?.let { onState(Matchmaking.Hosting(it)) }

            "searching" -> onState(Matchmaking.Searching)

            "start" -> {
                val seat = if (envelope["host"] == true) Seat.ONE else Seat.TWO
                onState(
                    Matchmaking.Ready(
                        MatchRoom(
                            code = envelope["code"] as? String ?: "",
                            seat = seat,
                            isHost = envelope["host"] == true,
                            opponentName = envelope["opponent"] as? String ?: "Player",
                            seed = (envelope["seed"] as? Double)?.toInt() ?: 0,
                            transport = this
                        )
                    )
                )
            }

            "peer" -> readBody(envelope["body"])

            "gone" -> onOpponentGone?.invoke()

            "error" ->
                onState(Matchmaking.Failed(envelope["reason"] as? String ?: "Something went wrong"))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readBody(body: Any?) {
        val map = body as? Map<String, Any?> ?: return
        val index = (map["i"] as? Double)?.toInt() ?: return
        when (map["k"] as? String) {
            "move" -> MatchProtocol.decodeMove(map["m"] as? Map<String, Any?>)
                ?.let { onRemoteMove?.invoke(index, it) }

            "hash" -> (map["h"] as? String)?.let { onRemoteChecksum?.invoke(index, it) }

            "snap" -> MatchProtocol.decodeSnapshot(map["s"] as? Map<String, Any?>)
                ?.let { onSnapshot?.invoke(index, it) }
        }
    }

    // ------------------------------------------------------------- MatchTransport

    override fun sendMove(index: Int, move: MatchMove) =
        relay(mapOf("k" to "move", "i" to index, "m" to MatchProtocol.encode(move)))

    override fun sendChecksum(index: Int, checksum: String) =
        relay(mapOf("k" to "hash", "i" to index, "h" to checksum))

    override fun sendSnapshot(index: Int, snapshot: GameSnapshot) =
        relay(mapOf("k" to "snap", "i" to index, "s" to MatchProtocol.encode(snapshot)))

    private fun relay(body: Map<String, Any?>) {
        socket?.send(Json.write(mapOf("op" to "relay", "body" to body)))
    }

    override fun close() {
        if (closed) return
        closed = true
        retryScheduled = false
        socket?.send(Json.write(mapOf("op" to "leave")))
        socket?.close(NORMAL_CLOSE, "done")
        socket = null
    }

    /** Callbacks arrive on OkHttp's reader thread; the game loop lives on the main one. */
    private fun post(action: () -> Unit) {
        main.post(action)
    }

    private companion object {
        const val TAG = "MatchConnection"
        const val NORMAL_CLOSE = 1000
    }
}
