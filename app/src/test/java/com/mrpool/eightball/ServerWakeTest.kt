package com.mrpool.eightball

import com.mrpool.eightball.net.ServerWake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerWakeTest {

    @Test
    fun `retries after the first failure`() {
        assertTrue(ServerWake.shouldRetry(attemptsMade = 1, elapsedMillis = 0))
    }

    @Test
    fun `stops once the attempts are used up`() {
        assertTrue(ServerWake.shouldRetry(ServerWake.MAX_ATTEMPTS - 1, 0))
        assertFalse(ServerWake.shouldRetry(ServerWake.MAX_ATTEMPTS, 0))
    }

    @Test
    fun `stops once the player has waited long enough`() {
        assertTrue(ServerWake.shouldRetry(2, ServerWake.GIVE_UP_MILLIS - 1))
        assertFalse(ServerWake.shouldRetry(2, ServerWake.GIVE_UP_MILLIS))
    }

    @Test
    fun `the gap grows and then holds`() {
        val delays = (1..6).map { ServerWake.retryDelayMillis(it) }
        assertEquals(delays.sorted(), delays)
        assertEquals(
            "should settle rather than grow forever",
            delays[delays.size - 2],
            delays.last()
        )
    }

    @Test
    fun `no gap is ever zero`() {
        // A zero would turn the retry into a spin against a server that refuses instantly.
        for (attempt in 0..ServerWake.MAX_ATTEMPTS + 2) {
            assertTrue("attempt $attempt", ServerWake.retryDelayMillis(attempt) > 0)
        }
    }

    /**
     * The point of the whole class: keep trying for about as long as a sleeping free
     * instance takes to wake, or a player -- and a Play reviewer, whose connection is by
     * definition the first after a quiet spell -- meets a dead game.
     */
    @Test
    fun `keeps trying for long enough to cover a cold start`() {
        var elapsed = 0L
        var attempts = 1
        while (ServerWake.shouldRetry(attempts, elapsed)) {
            elapsed += ServerWake.retryDelayMillis(attempts)
            attempts++
        }
        assertTrue(
            "gave up after only ${elapsed}ms over $attempts attempts",
            elapsed >= 45_000L
        )
        assertTrue("and should not go on forever, waited ${elapsed}ms", elapsed <= 120_000L)
    }
}
