package com.mrpool.server

import com.mrpool.server.billing.Billing
import com.mrpool.server.billing.billingRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.time.Duration
import java.util.UUID

/**
 * The matchmaking and relay server for online play.
 *
 * It is deliberately tiny: it pairs players and forwards opaque messages between them. All
 * the pool lives in the app, where both devices replay the same shots through the same
 * deterministic simulation, so the server never simulates anything and never needs to know
 * the rules.
 *
 * Run it with `./gradlew run`; it listens on $PORT, or 8080.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    embeddedServer(Netty, port = port, host = "0.0.0.0") { matchServer() }.start(wait = true)
}

const val DEFAULT_PORT = 8080

/** A connected app, wrapped so [Hub] never sees a Ktor type. */
private class SocketPeer(
    override val id: String,
    private val session: DefaultWebSocketSession
) : Peer {
    override suspend fun send(text: String) {
        session.send(text)
    }
}

fun Application.matchServer(hub: Hub = Hub(), billing: Billing = Billing()) {
    install(ServerWebSockets) {
        // Keeps the connection alive through the idle timeouts that free hosting tiers and
        // mobile networks both impose. A player lining up a shot sends nothing for a while.
        pingPeriodMillis = Duration.ofSeconds(20).toMillis()
        timeoutMillis = Duration.ofSeconds(60).toMillis()
        maxFrameSize = MAX_FRAME_BYTES
        masking = false
    }

    routing {
        get("/") {
            call.respondText("Mr. Pool match server. ${hub.openRooms} rooms open.")
        }

        // Subscriptions. Inert unless the Razorpay keys are in the environment, so a
        // server deployed only to relay matches carries these routes and sells nothing.
        billingRoutes(billing)

        // Free hosting tiers ping this to decide whether the service is alive.
        get("/health") {
            call.respondText("ok")
        }

        webSocket("/ws") {
            val peer = SocketPeer(UUID.randomUUID().toString(), this)
            try {
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val envelope = Messages.parse(text)
                    if (envelope == null) {
                        send(Messages.error("unreadable message"))
                        continue
                    }
                    when (envelope.op) {
                        "hello" -> hub.setName(peer, envelope.name ?: "Player")
                        "create" -> hub.createRoom(peer)
                        "join" -> hub.joinRoom(peer, envelope.code.orEmpty())
                        "queue" -> hub.quickMatch(peer)
                        "leave" -> hub.leave(peer)
                        "relay" -> envelope.body?.let { hub.relay(peer, it) }
                        else -> send(Messages.error("unknown op ${envelope.op}"))
                    }
                }
            } finally {
                // However the connection ended, the opponent must be told rather than left
                // waiting on a table that will never move again.
                hub.leave(peer)
            }
        }
    }
}

/** Snapshots can run to a few kilobytes; nothing here is ever larger. */
private const val MAX_FRAME_BYTES = 256L * 1024L
