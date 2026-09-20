package com.mrpool.server

import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A peer that just records what it was sent. */
private class FakePeer(override val id: String) : Peer {
    val received = mutableListOf<String>()
    override suspend fun send(text: String) {
        received.add(text)
    }

    fun lastOp(): String? = received.lastOrNull()?.let { Messages.parse(it)?.op }
    fun opsSeen(): List<String> = received.mapNotNull { Messages.parse(it)?.op }
    fun lastCode(): String? = received.lastOrNull()?.let { Messages.parse(it)?.code }
}

class HubTest {

    private fun hub() = Hub(Random(5))

    @Test
    fun `creating a room hands back a usable code`() = runBlocking {
        val hub = hub()
        val host = FakePeer("host")
        hub.createRoom(host)

        assertEquals("created", host.lastOp())
        val code = host.lastCode()
        assertNotNull(code)
        assertTrue(RoomCode.isValid(code), "$code is not a valid room code")
        assertEquals(1, hub.openRooms)
    }

    @Test
    fun `joining a room starts both players with the same seed and opposite seats`() =
        runBlocking {
            val hub = hub()
            val host = FakePeer("host")
            val guest = FakePeer("guest")
            hub.setName(host, "Asad")
            hub.setName(guest, "Friend")

            hub.createRoom(host)
            val code = host.lastCode()!!
            hub.joinRoom(guest, code)

            val hostStart = Messages.json.parseToJsonElement(host.received.last())
            val guestStart = Messages.json.parseToJsonElement(guest.received.last())

            fun field(element: kotlinx.serialization.json.JsonElement, key: String): String =
                (element as kotlinx.serialization.json.JsonObject)[key].toString().trim('"')

            assertEquals("start", field(hostStart, "op"))
            assertEquals("start", field(guestStart, "op"))
            assertEquals("ONE", field(hostStart, "seat"))
            assertEquals("TWO", field(guestStart, "seat"))
            assertEquals("true", field(hostStart, "host"))
            assertEquals("false", field(guestStart, "host"))
            assertEquals(
                field(hostStart, "seed"),
                field(guestStart, "seed"),
                "both players must rack from the same seed"
            )
            assertEquals("Friend", field(hostStart, "opponent"))
            assertEquals("Asad", field(guestStart, "opponent"))
            assertTrue(hub.isPaired(host))
        }

    @Test
    fun `a code that does not exist is refused`() = runBlocking {
        val hub = hub()
        val guest = FakePeer("guest")
        hub.joinRoom(guest, "ZZZZZ")
        assertEquals("error", guest.lastOp())
        assertTrue(!hub.isPaired(guest))
    }

    @Test
    fun `a third player cannot join a full room`() = runBlocking {
        val hub = hub()
        val host = FakePeer("host")
        val guest = FakePeer("guest")
        val gatecrasher = FakePeer("third")

        hub.createRoom(host)
        val code = host.lastCode()!!
        hub.joinRoom(guest, code)
        hub.joinRoom(gatecrasher, code)

        assertEquals("error", gatecrasher.lastOp())
        assertTrue(!hub.isPaired(gatecrasher))
    }

    @Test
    fun `a host cannot join their own room`() = runBlocking {
        val hub = hub()
        val host = FakePeer("host")
        hub.createRoom(host)
        hub.joinRoom(host, host.lastCode()!!)
        assertEquals("error", host.lastOp())
    }

    @Test
    fun `the quick match queue pairs the first two players`() = runBlocking {
        val hub = hub()
        val first = FakePeer("first")
        val second = FakePeer("second")

        hub.quickMatch(first)
        assertEquals("searching", first.lastOp())

        hub.quickMatch(second)
        assertEquals("start", first.lastOp())
        assertEquals("start", second.lastOp())
        assertTrue(hub.isPaired(first))
        assertTrue(hub.isPaired(second))
    }

    @Test
    fun `queueing twice does not pair a player with themselves`() = runBlocking {
        val hub = hub()
        val lonely = FakePeer("lonely")
        hub.quickMatch(lonely)
        hub.quickMatch(lonely)
        assertEquals("searching", lonely.lastOp())
        assertTrue(!hub.isPaired(lonely))
    }

    @Test
    fun `a relayed body reaches the opponent untouched`() = runBlocking {
        val hub = hub()
        val host = FakePeer("host")
        val guest = FakePeer("guest")
        hub.createRoom(host)
        hub.joinRoom(guest, host.lastCode()!!)

        val move = """{"v":1,"type":"shoot","seat":"ONE","angle":1.25,"power":0.8}"""
        hub.relay(host, move)

        val delivered = guest.received.last()
        assertTrue(delivered.contains("\"op\":\"peer\""), delivered)
        // Every field must survive: the server does not understand them and must not touch them.
        assertTrue(delivered.contains("\"angle\":1.25"), delivered)
        assertTrue(delivered.contains("\"type\":\"shoot\""), delivered)
        assertTrue(delivered.contains("\"power\":0.8"), delivered)
        assertEquals(
            listOf("created"),
            host.opsSeen().take(1),
            "nothing should come back to the sender"
        )
    }

    @Test
    fun `a relay from someone in no room goes nowhere`() = runBlocking {
        val hub = hub()
        val stranger = FakePeer("stranger")
        hub.relay(stranger, """{"hello":"world"}""")
        assertTrue(stranger.received.isEmpty())
    }

    @Test
    fun `leaving tells the opponent and frees the room`() = runBlocking {
        val hub = hub()
        val host = FakePeer("host")
        val guest = FakePeer("guest")
        hub.createRoom(host)
        hub.joinRoom(guest, host.lastCode()!!)
        assertEquals(1, hub.openRooms)

        hub.leave(host)

        assertEquals("gone", guest.lastOp())
        assertEquals(0, hub.openRooms, "the room should be closed once a player leaves")
        assertTrue(!hub.isPaired(guest))
    }

    @Test
    fun `leaving the queue is quiet and leaves nobody waiting`() = runBlocking {
        val hub = hub()
        val first = FakePeer("first")
        val second = FakePeer("second")

        hub.quickMatch(first)
        hub.leave(first)
        hub.quickMatch(second)

        // The queue was emptied, so the second player waits rather than being paired with a
        // player who has already gone.
        assertEquals("searching", second.lastOp())
    }

    @Test
    fun `an unreadable envelope is rejected rather than guessed at`() {
        assertNull(Messages.parse("not json at all"))
        assertNull(Messages.parse("{}"))
        assertNull(Messages.parse("[1,2,3]"))
        assertNotNull(Messages.parse("""{"op":"queue"}"""))
    }
}
