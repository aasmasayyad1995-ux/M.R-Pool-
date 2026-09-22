package com.mrpool.eightball

import com.mrpool.eightball.net.ChatLog
import com.mrpool.eightball.net.ChatSend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat between two players, and the four things that stand between a text box and a
 * stranger: the filter, the limits, the mute and the report.
 */
class ChatLogTest {

    /** A clock the test winds by hand, so the rate limiter can be driven without waiting. */
    private var now = 1_000L

    private val sent = mutableListOf<String>()
    private val reports = mutableListOf<List<String>>()

    private fun log() = ChatLog(
        localName = "You",
        remoteName = "Raj",
        publish = { sent.add(it) },
        publishReport = { reports.add(it) },
        clock = { now }
    )

    /** What the player would read on screen, players' words only. */
    private fun said(log: ChatLog): List<String> =
        log.lines().filterNot { it.note }.map { "${it.author}: ${it.text}" }

    @Test
    fun `what is typed goes out and is shown back`() {
        val chat = log()
        assertEquals(ChatSend.Sent, chat.say("  good shot  "))

        assertEquals(listOf("good shot"), sent)
        assertEquals(listOf("You: good shot"), said(chat))
        assertTrue("the player's own line is theirs", chat.lines().first().fromLocal)
    }

    @Test
    fun `an empty box sends nothing`() {
        val chat = log()
        assertEquals(ChatSend.Empty, chat.say("    "))
        assertEquals(ChatSend.Empty, chat.say(""))
        assertTrue(sent.isEmpty())
        assertTrue(chat.lines().isEmpty())
    }

    @Test
    fun `the opponent's line arrives under their name`() {
        val chat = log()
        chat.receive("nice one")
        assertEquals(listOf("Raj: nice one"), said(chat))
        assertFalse(chat.lines().first().fromLocal)
    }

    @Test
    fun `abuse is masked in both directions`() {
        val chat = log()
        chat.say("you fucking idiot")
        chat.receive("shut up bhosdike")

        // Masked on the way out too, so the player is not saying one thing and reading
        // another, and so the other phone never receives the word at all.
        assertEquals(listOf("you ●●● idiot"), sent)
        assertEquals(
            listOf("You: you ●●● idiot", "Raj: shut up ●●●"),
            said(chat)
        )
    }

    @Test
    fun `holding the send button down is cut off`() {
        val chat = log()
        repeat(5) { assertEquals(ChatSend.Sent, chat.say("hi $it")) }

        val blocked = chat.say("hi again")
        assertTrue("the sixth in ten seconds should be held", blocked is ChatSend.TooFast)
        assertEquals("only five went", 5, sent.size)

        // And it comes back once the window has rolled past.
        now += 10_001L
        assertEquals(ChatSend.Sent, chat.say("hi again"))
        assertEquals(6, sent.size)
    }

    @Test
    fun `a flood from the other phone is dropped, not drawn`() {
        val chat = log()
        repeat(20) { chat.receive("spam $it") }

        assertEquals("only what the limit allows is shown", 5, said(chat).size)
        assertTrue(
            "and the player is told why the rest are missing",
            chat.lines().any { it.note && it.text.contains("too fast") }
        )
    }

    @Test
    fun `muting stops the opponent being heard at all`() {
        val chat = log()
        chat.receive("before")
        assertTrue(chat.toggleMute())
        chat.receive("during")
        assertFalse(chat.toggleMute())
        chat.receive("after")

        assertEquals(listOf("Raj: before", "Raj: after"), said(chat))
    }

    @Test
    fun `muting does not stop the player talking`() {
        val chat = log()
        chat.toggleMute()
        assertEquals(ChatSend.Sent, chat.say("leaving, this is not fun"))
        assertEquals(listOf("leaving, this is not fun"), sent)
    }

    @Test
    fun `reporting sends their lines, mutes them, and says what it can and cannot do`() {
        val chat = log()
        chat.receive("you are rubbish")
        chat.say("ok")
        chat.receive("rubbish again")

        assertTrue(chat.report())

        // Only their half goes. The player's own words are nobody else's business.
        assertEquals(listOf(listOf("you are rubbish", "rubbish again")), reports)
        assertTrue("reporting mutes them", chat.muted)
        chat.receive("and again")
        assertFalse("nothing of theirs lands after a report", said(chat).contains("Raj: and again"))

        assertTrue(
            "the player must not be left thinking somebody was banned",
            chat.lines().any { it.note && it.text.contains("cannot ban") }
        )
    }

    @Test
    fun `a report only goes once`() {
        val chat = log()
        chat.receive("rude")
        assertTrue(chat.report())
        assertFalse(chat.report())
        assertEquals(1, reports.size)
    }

    @Test
    fun `the badge counts what arrived while the panel was shut`() {
        val chat = log()
        chat.receive("one")
        chat.receive("two")
        assertEquals(2, chat.unread)

        chat.markRead()
        assertEquals(0, chat.unread)

        chat.say("my own words are not unread")
        assertEquals(0, chat.unread)
    }

    @Test
    fun `a long match does not grow the list forever`() {
        val chat = log()
        repeat(200) {
            now += 3_000L                      // slowly enough that nothing is rate limited
            chat.say("line $it")
        }
        assertEquals(ChatLog.MAX_LINES, chat.lines().size)
        assertEquals("the newest line is kept", "line 199", chat.lines().last().text)
    }

    @Test
    fun `a message longer than the box allows is cut, not sent whole`() {
        val chat = log()
        chat.say("x".repeat(500))
        assertEquals(120, sent.single().length)
    }
}
