package com.mrpool.server

import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real thing: two clients over real WebSockets, talking to the real routing.
 *
 * [HubTest] covers the pairing rules; this covers the layer underneath them — that a
 * message put into a socket comes out of the other one.
 */
class WebSocketFlowTest {

    private suspend fun io.ktor.websocket.WebSocketSession.expect(op: String): String =
        withTimeout(5000) {
            while (true) {
                val frame = incoming.receive()
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val envelope = Messages.parse(text)
                if (envelope?.op == op) return@withTimeout text
            }
            @Suppress("UNREACHABLE_CODE")
            ""
        }

    @Test
    fun `two clients meet in a room and pass a move between them`() = testApplication {
        application { matchServer() }
        val client = createClient { install(ClientWebSockets) }

        val host = client.webSocketSession("/ws")
        val guest = client.webSocketSession("/ws")

        host.send("""{"op":"hello","name":"Asad"}""")
        guest.send("""{"op":"hello","name":"Friend"}""")

        host.send("""{"op":"create"}""")
        val created = host.expect("created")
        val code = Messages.parse(created)?.code
        assertTrue(code != null && RoomCode.isValid(code), "bad code in $created")

        guest.send("""{"op":"join","code":"$code"}""")

        val hostStart = host.expect("start")
        val guestStart = guest.expect("start")
        assertTrue(hostStart.contains("\"seat\":\"ONE\""), hostStart)
        assertTrue(guestStart.contains("\"seat\":\"TWO\""), guestStart)
        assertTrue(hostStart.contains("\"opponent\":\"Friend\""), hostStart)
        assertTrue(guestStart.contains("\"opponent\":\"Asad\""), guestStart)

        // A shot, exactly as the app would send it.
        host.send(
            """{"op":"relay","body":{"v":1,"type":"shoot","seat":"ONE","angle":0.5,"power":0.9}}"""
        )
        val relayed = guest.expect("peer")
        assertTrue(relayed.contains("\"angle\":0.5"), relayed)
        assertTrue(relayed.contains("\"type\":\"shoot\""), relayed)

        // And a reply the other way.
        guest.send("""{"op":"relay","body":{"v":1,"type":"place","seat":"TWO","x":-0.4,"y":0.1}}""")
        val back = host.expect("peer")
        assertTrue(back.contains("\"x\":-0.4"), back)

        host.close()
        guest.close()
    }

    @Test
    fun `a dropped connection tells the other player`() = testApplication {
        application { matchServer() }
        val client = createClient { install(ClientWebSockets) }

        val host = client.webSocketSession("/ws")
        val guest = client.webSocketSession("/ws")

        host.send("""{"op":"create"}""")
        val code = Messages.parse(host.expect("created"))?.code
        guest.send("""{"op":"join","code":"$code"}""")
        guest.expect("start")
        host.expect("start")

        // The host's phone loses signal.
        host.close()

        val gone = guest.expect("gone")
        assertTrue(gone.contains("\"op\":\"gone\""), gone)
        guest.close()
    }

    @Test
    fun `the quick match queue pairs two strangers over the wire`() = testApplication {
        application { matchServer() }
        val client = createClient { install(ClientWebSockets) }

        val first = client.webSocketSession("/ws")
        first.send("""{"op":"hello","name":"First"}""")
        first.send("""{"op":"queue"}""")
        first.expect("searching")

        val second = client.webSocketSession("/ws")
        second.send("""{"op":"hello","name":"Second"}""")
        second.send("""{"op":"queue"}""")

        val firstStart = first.expect("start")
        val secondStart = second.expect("start")

        // Whoever was waiting hosts, and both rack from the same seed.
        assertTrue(firstStart.contains("\"host\":true"), firstStart)
        assertTrue(secondStart.contains("\"host\":false"), secondStart)
        val seed = { text: String -> text.substringAfter("\"seed\":").substringBefore(",") }
        assertEquals(seed(firstStart), seed(secondStart), "the two tables would not match")

        first.close()
        second.close()
    }

    @Test
    fun `rubbish sent down the socket is answered, not fatal`() = testApplication {
        application { matchServer() }
        val client = createClient { install(ClientWebSockets) }

        val socket = client.webSocketSession("/ws")
        socket.send("this is not json")
        val error = socket.expect("error")
        assertTrue(error.contains("unreadable"), error)

        // The connection is still usable afterwards.
        socket.send("""{"op":"create"}""")
        val created = socket.expect("created")
        assertTrue(Messages.parse(created)?.code != null)
        socket.close()
    }
}
