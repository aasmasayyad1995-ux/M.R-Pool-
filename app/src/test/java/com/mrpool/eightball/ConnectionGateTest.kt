package com.mrpool.eightball

import com.mrpool.eightball.net.ConnectionGate
import com.mrpool.eightball.net.ConnectionNotice
import com.mrpool.eightball.net.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mr. Pool needs a connection to be played at all, so what a lost one means is a rule of
 * the game and not a detail of the screen.
 */
class ConnectionGateTest {

    @Test
    fun `with a connection there is nothing to say`() {
        assertEquals(
            ConnectionNotice.None,
            ConnectionGate.noticeFor(online = true, inMatch = false)
        )
        assertEquals(
            "a match in progress is not interrupted while the connection is there",
            ConnectionNotice.None,
            ConnectionGate.noticeFor(online = true, inMatch = true)
        )
    }

    @Test
    fun `off the table, losing the connection covers the game`() {
        assertEquals(
            ConnectionNotice.Blocked,
            ConnectionGate.noticeFor(online = false, inMatch = false)
        )
    }

    @Test
    fun `in a match, losing the connection ends the match rather than covering it`() {
        // The player should see the table they were playing on behind the message, not a
        // screen that has replaced it without explanation.
        assertEquals(
            ConnectionNotice.MatchEnded,
            ConnectionGate.noticeFor(online = false, inMatch = true)
        )
    }
}

/**
 * The grace period, which is the part that decides whether this feature is usable.
 *
 * Android reports a moment with no network at every handover between wifi and mobile data.
 * Acting on the first of those would throw a player out of a match for walking out of
 * their front door.
 */
class ConnectionStateTest {

    private val grace = ConnectionGate.GRACE_MILLIS

    @Test
    fun `a connection that is there is not offline`() {
        val state = ConnectionState()
        state.report(connected = true, nowMillis = 1_000L)
        assertFalse(state.isOffline(1_000L))
        assertFalse(state.isOffline(1_000L + grace * 10))
        assertNull("nothing is counting down", state.millisUntilOffline(1_000L))
    }

    @Test
    fun `a blink between wifi and mobile data is not a lost connection`() {
        val state = ConnectionState()
        state.report(connected = true, nowMillis = 0L)

        state.report(connected = false, nowMillis = 1_000L)
        assertFalse("still inside the grace period", state.isOffline(1_500L))

        state.report(connected = true, nowMillis = 2_000L)
        assertFalse(
            "the connection came back, so the countdown is over",
            state.isOffline(2_000L + grace * 10)
        )
    }

    @Test
    fun `a connection that stays gone is eventually believed`() {
        val state = ConnectionState()
        state.report(connected = true, nowMillis = 0L)
        state.report(connected = false, nowMillis = 1_000L)

        assertFalse(state.isOffline(1_000L + grace - 1))
        assertTrue(state.isOffline(1_000L + grace))
        assertTrue(state.isOffline(1_000L + grace * 5))
    }

    @Test
    fun `repeated reports of the same loss do not push the deadline back`() {
        // A phone that reports the loss once a second would otherwise never time out:
        // every report would restart the wait and the player would sit there forever.
        val state = ConnectionState()
        state.report(connected = true, nowMillis = 0L)
        state.report(connected = false, nowMillis = 1_000L)

        var now = 1_000L
        while (now < 1_000L + grace) {
            state.report(connected = false, nowMillis = now)
            now += 500L
        }
        assertTrue(
            "the wait must run from when the connection first went",
            state.isOffline(1_000L + grace)
        )
    }

    @Test
    fun `the countdown reports how long is left`() {
        val state = ConnectionState()
        state.report(connected = true, nowMillis = 0L)
        state.report(connected = false, nowMillis = 1_000L)

        assertEquals(grace, state.millisUntilOffline(1_000L))
        assertEquals(grace / 2, state.millisUntilOffline(1_000L + grace / 2))
        assertEquals("never negative", 0L, state.millisUntilOffline(1_000L + grace * 3))
    }

    @Test
    fun `losing it again after it came back starts a fresh wait`() {
        val state = ConnectionState()
        state.report(connected = true, nowMillis = 0L)
        state.report(connected = false, nowMillis = 1_000L)
        state.report(connected = true, nowMillis = 1_500L)
        state.report(connected = false, nowMillis = 2_000L)

        assertFalse(
            "the second loss gets its own full grace period, not what was left of the first",
            state.isOffline(2_000L + grace - 1)
        )
        assertTrue(state.isOffline(2_000L + grace))
    }

    @Test
    fun `the grace period is long enough to be worth having`() {
        // A handover between networks takes a second or two. Anything shorter than that
        // and this may as well not be here.
        assertTrue(
            "too short to ride out a network handover",
            ConnectionGate.GRACE_MILLIS >= 2_000L
        )
        assertTrue(
            "so long that a player with no signal is left aiming at a dead table",
            ConnectionGate.GRACE_MILLIS <= 10_000L
        )
    }
}
