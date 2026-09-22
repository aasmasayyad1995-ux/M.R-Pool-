package com.mrpool.eightball

import com.mrpool.eightball.net.ChatFilter
import com.mrpool.eightball.net.ChatRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What one player is allowed to send the other.
 *
 * The filter is a speed bump rather than a wall, so what is worth testing is that it
 * catches the lazy dodges people actually use and leaves ordinary words alone. A filter
 * that mangles "class" is worse than none, because it teaches players to ignore it.
 */
class ChatFilterTest {

    @Test
    fun `ordinary pool talk goes through untouched`() {
        val fine = listOf(
            "good shot",
            "nice one mate",
            "your turn",
            "that was lucky!",
            "I need the 8 in the corner",
            "one more game?",
            "well played, gg"
        )
        for (message in fine) {
            assertEquals("\"$message\" should have been left alone", message, ChatFilter.clean(message))
        }
    }

    @Test
    fun `words that merely contain a rude run are not touched`() {
        // The classic false positive. A filter that breaks these gets ignored.
        val innocent = listOf(
            "class", "classic", "assist", "assume", "grass", "pass", "bass", "glass",
            "analysis", "shuttle", "cockpit", "Scunthorpe", "Dickens", "kutta"
        )
        for (word in innocent) {
            if (word == "kutta") continue // that one really is an insult here
            assertEquals("\"$word\" is an ordinary word", word, ChatFilter.clean(word))
        }
    }

    @Test
    fun `plain abuse is masked`() {
        assertTrue(ChatFilter.hasBlockedWords("you fuck"))
        assertTrue(ChatFilter.hasBlockedWords("chutiya"))
        assertTrue(ChatFilter.hasBlockedWords("bhenchod"))
        assertEquals("you ${ChatFilter.MASK}", ChatFilter.clean("you fuck"))
    }

    @Test
    fun `the usual dodges do not get past it`() {
        val dodges = listOf("f4ck", "fuuuuck", "sh1t", "$" + "hit", "b1tch", "ch00tiya", "g4ndu")
        for (dodge in dodges) {
            assertTrue("\"$dodge\" got through", ChatFilter.hasBlockedWords(dodge))
        }
    }

    @Test
    fun `only the offending word is masked, not the sentence`() {
        assertEquals(
            "nice shot ${ChatFilter.MASK} play again",
            ChatFilter.clean("nice shot bastard play again")
        )
    }

    @Test
    fun `a message is tidied before anybody sees it`() {
        assertEquals("good shot", ChatFilter.tidy("   good    shot   "))
        assertEquals("", ChatFilter.tidy("      "))
        assertEquals("", ChatFilter.tidy(""))
        assertEquals(
            ChatFilter.MAX_LENGTH,
            ChatFilter.tidy("a".repeat(500)).length
        )
    }

    @Test
    fun `newlines cannot be used to shout down the screen`() {
        assertEquals("a b c", ChatFilter.tidy("a\n\n\nb\t\tc"))
    }

    @Test
    fun `an ending on the end of it does not get it through`() {
        // Otherwise the list would have to carry every ending of every word on it.
        for (word in listOf("fucking", "fucked", "shits", "dickhead", "chutiye", "f4cking")) {
            assertEquals("\"$word\" should not get through", ChatFilter.MASK, ChatFilter.clean(word))
        }
    }

    @Test
    fun `taking an ending off does not turn an ordinary word rude`() {
        // The risk of the rule above: chop the end off an innocent word and it can land on
        // the list. These are the ones that actually come close.
        for (word in listOf("chute", "parachute", "chutney", "Gandhi", "grandest", "randy", "grapes", "classy")) {
            assertEquals("\"$word\" is an ordinary word", word, ChatFilter.clean(word))
        }
    }
}

/** How often a player may say something. */
class ChatRateTest {

    @Test
    fun `talking normally is never held back`() {
        val rate = ChatRate()
        var now = 0L
        repeat(20) {
            assertTrue("a message every three seconds is not spam", rate.allow(now))
            now += 3_000L
        }
    }

    @Test
    fun `a burst is cut off`() {
        val rate = ChatRate()
        var allowed = 0
        repeat(50) { if (rate.allow(1_000L)) allowed++ }
        assertEquals(ChatRate.MESSAGES, allowed)
    }

    @Test
    fun `the allowance comes back after the window`() {
        val rate = ChatRate()
        repeat(ChatRate.MESSAGES) { rate.allow(0L) }
        assertFalse(rate.allow(0L))
        assertTrue("after the window it should let one through", rate.allow(ChatRate.WINDOW_MILLIS + 1))
    }

    @Test
    fun `it says how long the wait is`() {
        val rate = ChatRate()
        assertEquals(0L, rate.waitMillis(0L))
        repeat(ChatRate.MESSAGES) { rate.allow(0L) }
        assertEquals(ChatRate.WINDOW_MILLIS, rate.waitMillis(0L))
        assertEquals(1_000L, rate.waitMillis(ChatRate.WINDOW_MILLIS - 1_000L))
        assertEquals(0L, rate.waitMillis(ChatRate.WINDOW_MILLIS))
    }
}
