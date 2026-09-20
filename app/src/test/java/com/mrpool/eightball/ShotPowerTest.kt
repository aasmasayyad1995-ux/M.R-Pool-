package com.mrpool.eightball

import com.mrpool.eightball.game.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The power bar, and the speed it asks the cue for. */
class ShotPowerTest {

    @Test
    fun `the ends of the bar are the ends of the range`() {
        assertEquals(GameSession.MIN_SHOT_SPEED, GameSession.speedForPower(0f), 1e-4f)
        assertEquals(GameSession.MAX_SHOT_SPEED, GameSession.speedForPower(1f), 1e-4f)
    }

    @Test
    fun `speed only ever rises with power`() {
        var previous = -1f
        var power = 0f
        while (power <= 1f) {
            val speed = GameSession.speedForPower(power)
            assertTrue("power $power went backwards", speed > previous)
            previous = speed
            power += 0.01f
        }
    }

    @Test
    fun `the bar spends most of itself on the soft shots`() {
        // Nearly every shot in a game of pool is a gentle one. Half the bar should be a
        // long way short of half the speed, or the soft shots are all crammed together at
        // the bottom and impossible to pick between.
        val half = GameSession.speedForPower(0.5f)
        val midpoint = (GameSession.MIN_SHOT_SPEED + GameSession.MAX_SHOT_SPEED) / 2f
        assertTrue(
            "half power gives ${half}m/s, which is not far enough below the ${midpoint}m/s " +
                "a straight line would give",
            half < midpoint * 0.75f
        )
        // And it must still be a usable shot, not a nudge.
        assertTrue("half power is too feeble at ${half}m/s", half > 1.5f)
    }

    @Test
    fun `asking for a speed gives back the setting that produces it`() {
        // The robot works in speeds and the cue works in bar positions. If the two ever
        // disagreed, every shot the robot planned would arrive at the wrong pace.
        var power = 0f
        while (power <= 1f) {
            val speed = GameSession.speedForPower(power)
            val recovered = GameSession.powerForSpeed(speed)
            assertEquals("the round trip lost power $power", power, recovered, 1e-3f)
            power += 0.02f
        }
    }

    @Test
    fun `speeds outside the range are clamped rather than producing nonsense`() {
        assertEquals(0f, GameSession.powerForSpeed(-5f), 1e-4f)
        assertEquals(1f, GameSession.powerForSpeed(500f), 1e-4f)
        assertEquals(GameSession.MIN_SHOT_SPEED, GameSession.speedForPower(-3f), 1e-4f)
        assertEquals(GameSession.MAX_SHOT_SPEED, GameSession.speedForPower(9f), 1e-4f)
    }

    @Test
    fun `a full power break crosses the table slowly enough to watch`() {
        // What matters is the time on screen, not in the simulation: the table is run
        // slower than real time precisely so that a hard shot can be followed.
        val crossing = 2.24f / GameSession.MAX_SHOT_SPEED / GameSession.TABLE_TIME_SCALE
        assertTrue(
            "a full power shot crosses the table in ${crossing}s on screen, too fast to follow",
            crossing > 0.3f
        )
    }

    @Test
    fun `a full power break is still hard enough to be legal`() {
        // A break has to send four balls to a cushion. Softening this to calm the game
        // down made the robot break illegally one game in four; the calming is done by the
        // time scale and the shape of the bar instead.
        assertTrue(
            "a full power break at ${GameSession.MAX_SHOT_SPEED}m/s is too weak to break with",
            GameSession.MAX_SHOT_SPEED >= 8.5f
        )
    }

    @Test
    fun `the table is slowed down, but not into slow motion`() {
        assertTrue(
            "the time scale of ${GameSession.TABLE_TIME_SCALE} is not a slowdown at all",
            GameSession.TABLE_TIME_SCALE < 0.9f
        )
        assertTrue(
            "the time scale of ${GameSession.TABLE_TIME_SCALE} would feel like treacle",
            GameSession.TABLE_TIME_SCALE > 0.5f
        )
    }
}
