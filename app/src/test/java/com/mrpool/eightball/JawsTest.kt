package com.mrpool.eightball

import com.mrpool.eightball.game.Ball
import com.mrpool.eightball.game.ClothProperties
import com.mrpool.eightball.game.PoolPhysics
import com.mrpool.eightball.game.ShotEvents
import com.mrpool.eightball.game.TableGeometry
import com.mrpool.eightball.game.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A ball that stops in a pocket mouth.
 *
 * The jaws stop a ball flying off the table, but a ball can also come to rest in them:
 * outside the cushions, and further from the pocket centre than the pocket swallows. It
 * used to stay there — off the table, still in play, impossible to hit and impossible to
 * pot — for the rest of the game. Three hundred and sixty simulated games threw this up
 * 323 times, once with the cue ball.
 *
 * The positions below are real ones, copied out of that run.
 */
class JawsTest {

    private fun table(vararg balls: Ball) =
        PoolPhysics(balls.toMutableList(), ClothProperties.TOURNAMENT)

    private fun ballAt(number: Int, x: Float, y: Float) = Ball(number, Vec2(x, y))

    @Test
    fun `a ball stopped in the jaws drops instead of living off the table`() {
        // Seed 7000020: 1.2 mm too far from the pocket centre to be swallowed, and it sat
        // there for shots 16, 17, 18 and 19.
        val stray = ballAt(11, 1.066f, -0.593f)
        val physics = table(ballAt(0, 0f, 0f), stray)
        val events = ShotEvents()

        physics.settle(events)

        assertTrue("it is in the pocket it was sitting in", stray.pocketed)
        assertTrue("and the shot is told, so it counts as potted", events.pocketed.contains(11))
    }

    @Test
    fun `the cue ball stopped in the jaws is a scratch, not a ball off the table`() {
        // Seed 7000023, and the worst of the lot: the cue ball parked outside the cushions
        // with the game expecting the player to shoot from there.
        val cue = ballAt(0, -1.170f, 0.499f)
        val physics = table(cue, ballAt(9, 0f, 0f))
        val events = ShotEvents()

        physics.settle(events)

        assertTrue(cue.pocketed)
        assertTrue("the rules engine reads a scratch off this", events.cueBallPocketed)
    }

    @Test
    fun `a ball frozen against a cushion is left exactly where it is`() {
        // The other half of the rule, and the one that would quietly ruin the game if the
        // margin were wrong: a ball resting on the rail sits *exactly* on the line this
        // test is about, and must never be mistaken for one in a pocket mouth.
        val onTheRail = ballAt(
            4,
            0f,
            TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS
        )
        val physics = table(ballAt(0, 0f, 0f), onTheRail)
        val events = ShotEvents()

        physics.settle(events)

        assertFalse("still on the table", onTheRail.pocketed)
        assertTrue("and not moved", events.pocketed.isEmpty())
        assertEquals(0f, onTheRail.position.x, 1e-6f)
    }

    @Test
    fun `a ball in the middle of the table is not touched`() {
        val quiet = ballAt(7, 0.2f, -0.1f)
        val physics = table(ballAt(0, 0f, 0f), quiet)
        val events = ShotEvents()

        physics.settle(events)

        assertFalse(quiet.pocketed)
        assertTrue(events.pocketed.isEmpty())
        assertEquals(0.2f, quiet.position.x, 1e-6f)
        assertEquals(-0.1f, quiet.position.y, 1e-6f)
    }

    @Test
    fun `a ball already pocketed is not pocketed twice`() {
        val gone = ballAt(3, 1.12f, 0.56f).apply { pocketed = true }
        val physics = table(ballAt(0, 0f, 0f), gone)
        val events = ShotEvents()

        physics.settle(events)

        assertTrue(events.pocketed.isEmpty())
    }

    @Test
    fun `a ball deep in the jaws is not flung back onto the table by the rail`() {
        // Seed 7110010, shot 29. The ball sat 83mm past the long rail line, a hair outside
        // the mouth boundary. The step it drifted across, the cushion woke up and put it
        // back on its line -- 83mm, in one step, at 0.14 m/s, straight through the rail it
        // had already passed. A cushion cannot reach a ball that is behind it.
        val deep = Ball(12, Vec2(-1.0449f, -0.6144f), Vec2(0.01f, 0.14f))
        val physics = PoolPhysics(mutableListOf(Ball(0, Vec2(0f, 0f)), deep), ClothProperties.TOURNAMENT)

        physics.step(PoolPhysics.FIXED_STEP)

        val railLine = -(TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS)
        assertTrue(
            "it was put back on the cushion line at $railLine, from ${deep.position.y}",
            deep.position.y < railLine - 0.05f
        )
    }

    @Test
    fun `a ball arriving at a cushion still bounces off it`() {
        // The other half: the guard must not stop the rails working. A ball a sliver past
        // the line, which is all an arriving ball can ever be, has to come back.
        val limY = TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS
        val arriving = Ball(5, Vec2(0.4f, -limY - 0.001f), Vec2(0f, -1.5f))
        val physics = PoolPhysics(mutableListOf(Ball(0, Vec2(0f, 0f)), arriving), ClothProperties.TOURNAMENT)

        physics.step(PoolPhysics.FIXED_STEP)

        assertTrue("it should be heading back up the table", arriving.velocity.y > 0f)
        assertTrue("and not left outside the cushion", arriving.position.y >= -limY - 0.002f)
    }
}
