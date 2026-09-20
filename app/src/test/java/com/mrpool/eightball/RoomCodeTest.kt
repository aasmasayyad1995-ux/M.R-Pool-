package com.mrpool.eightball

import com.mrpool.eightball.net.RoomCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The code one player reads out to another. */
class RoomCodeTest {

    @Test
    fun `generated codes are the right length and always valid`() {
        val random = Random(9)
        repeat(500) {
            val code = RoomCode.generate(random)
            assertEquals(RoomCode.LENGTH, code.length)
            assertTrue("$code is not accepted by its own validator", RoomCode.isValid(code))
        }
    }

    @Test
    fun `codes never contain characters that sound alike over a phone`() {
        val confusing = setOf('O', '0', 'I', 'L', '1', 'S', '5')
        val random = Random(4)
        repeat(500) {
            val code = RoomCode.generate(random)
            for (char in code) {
                assertFalse("$code contains the ambiguous '$char'", char in confusing)
            }
        }
    }

    @Test
    fun `what a player types is tidied into what they meant`() {
        // Lower case, stray spaces, and the substitutions people make by habit.
        assertEquals("QJZ23", RoomCode.normalise(" o i s 2 3 "))
        assertEquals("QJZ23", RoomCode.normalise("0L523"))
        assertTrue(RoomCode.isValid(RoomCode.normalise("0l523")))
    }

    @Test
    fun `nonsense is rejected rather than half accepted`() {
        assertFalse(RoomCode.isValid(""))
        assertFalse(RoomCode.isValid("ABC"))
        assertFalse(RoomCode.isValid("ABCDEF"))
        assertFalse("the ambiguous characters must not pass validation", RoomCode.isValid("ABCD0"))
    }

    @Test
    fun `the alphabet is wide enough that codes rarely collide`() {
        val random = Random(77)
        val seen = HashSet<String>()
        var collisions = 0
        repeat(5000) {
            if (!seen.add(RoomCode.generate(random))) collisions++
        }
        // 5,000 codes out of a ~20 million space: a handful of collisions at most.
        assertTrue("$collisions collisions in 5,000 codes is too many", collisions < 10)
    }
}
