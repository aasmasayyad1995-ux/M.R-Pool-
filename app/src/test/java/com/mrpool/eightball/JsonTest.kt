package com.mrpool.eightball

import com.mrpool.eightball.game.Seat
import com.mrpool.eightball.net.Json
import com.mrpool.eightball.net.MatchMove
import com.mrpool.eightball.net.MatchProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun `objects and arrays survive a round trip`() {
        val original = mapOf(
            "type" to "shoot",
            "index" to 7,
            "power" to 0.625,
            "spin" to listOf(-0.5, 0.25),
            "flags" to mapOf("host" to true, "bot" to false),
            "nothing" to null
        )
        val decoded = Json.readObject(Json.write(original))
        assertNotNull(decoded)
        assertEquals("shoot", decoded!!["type"])
        assertEquals(7.0, decoded["index"])
        assertEquals(0.625, decoded["power"])
        assertEquals(listOf(-0.5, 0.25), decoded["spin"])
        assertEquals(true, (decoded["flags"] as Map<*, *>)["host"])
        assertEquals(false, (decoded["flags"] as Map<*, *>)["bot"])
        assertTrue(decoded.containsKey("nothing"))
        assertNull(decoded["nothing"])
    }

    @Test
    fun `strings with quotes, backslashes and newlines come back intact`() {
        val awkward = "a \"quoted\" \\ back\\slash\nnew line\ttab"
        val decoded = Json.readObject(Json.write(mapOf("text" to awkward)))
        assertEquals(awkward, decoded!!["text"])
    }

    @Test
    fun `unicode escapes are read back`() {
        assertEquals(mapOf("s" to "A"), Json.readObject("""{"s":"A"}"""))
    }

    @Test
    fun `negative and exponent numbers are read`() {
        val decoded = Json.readObject("""{"a":-0.5,"b":1e3,"c":-2E-2}""")!!
        assertEquals(-0.5, decoded["a"])
        assertEquals(1000.0, decoded["b"])
        assertEquals(-0.02, decoded["c"])
    }

    @Test
    fun `empty containers are handled`() {
        assertEquals(emptyMap<String, Any?>(), Json.readObject("{}"))
        assertEquals(emptyList<Any?>(), Json.read("[]"))
        assertEquals(emptyList<Any?>(), Json.readObject("""{"a":[]}""")!!["a"])
    }

    @Test
    fun `values that JSON cannot express are written as null rather than broken output`() {
        val written = Json.write(mapOf("bad" to Double.NaN, "worse" to Float.POSITIVE_INFINITY))
        assertEquals("""{"bad":null,"worse":null}""", written)
        assertNotNull("the output must still parse", Json.readObject(written))
    }

    @Test
    fun `malformed input returns null instead of throwing`() {
        assertNull(Json.read("not json"))
        assertNull(Json.read("""{"a":}"""))
        assertNull(Json.read("""{"a":1"""))
        assertNull(Json.read("""{"a" 1}"""))
        assertNull(Json.read("[1,2"))
        assertNull(Json.read(""))
        assertNull("trailing rubbish must not be ignored", Json.read("""{"a":1} extra"""))
        assertNull(Json.readObject("[1,2,3]"))
    }

    @Test
    fun `a move survives the whole path out and back`() {
        // This is the real journey: move to map, map to JSON text, back to map, back to move.
        for (move in listOf<MatchMove>(
            MatchMove.Shoot(Seat.ONE, 1.5f, 0.8f, -0.3f, 0.6f),
            MatchMove.Shoot(Seat.TWO, -2.75f, 0.05f, 0f, 0f),
            MatchMove.PlaceCueBall(Seat.TWO, -0.88f, 0.42f),
            MatchMove.Forfeit(Seat.ONE)
        )) {
            val text = Json.write(MatchProtocol.encode(move))
            val decoded = MatchProtocol.decodeMove(Json.readObject(text))
            assertEquals("$move did not survive the wire", move, decoded)
        }
    }
}
