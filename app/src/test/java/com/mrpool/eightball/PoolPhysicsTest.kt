package com.mrpool.eightball

import com.mrpool.eightball.game.Ball
import com.mrpool.eightball.game.ClothProperties
import com.mrpool.eightball.game.CollisionListener
import com.mrpool.eightball.game.PoolPhysics
import com.mrpool.eightball.game.Rack
import com.mrpool.eightball.game.ShotEvents
import com.mrpool.eightball.game.TableGeometry
import com.mrpool.eightball.game.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class PoolPhysicsTest {

    private fun world(vararg balls: Ball) =
        PoolPhysics(balls.toMutableList(), ClothProperties.TOURNAMENT).also {
            it.trackOrientation = false
        }

    @Test
    fun `every ball comes to rest after a full power break`() {
        val physics = PoolPhysics(Rack.build(Random(7)), ClothProperties.TOURNAMENT)
        physics.strike(Vec2(1f, 0.01f).normalized(), 9f, 0f, 0f)
        physics.simulateUntilRest(30f)
        assertFalse("balls were still moving 30s after the break", physics.anyBallMoving())
    }

    @Test
    fun `a rolling ball stops instead of creeping forever`() {
        val cue = Ball(0, Vec2(-0.9f, 0f))
        val physics = world(cue)
        physics.strike(Vec2(1f, 0f), 1.2f, 0f, 0f)
        physics.simulateUntilRest(30f)
        assertFalse(physics.anyBallMoving())
        assertEquals(0f, cue.velocity.length(), 1e-4f)
    }

    @Test
    fun `a straight shot into the corner pocket drops the object ball`() {
        val pocket = TableGeometry.pockets.first { it.id.name == "TOP_RIGHT" }
        val target = Ball(1, Vec2(0.55f, 0.28f))
        val aim = TableGeometry.aimPoint(pocket)
        val potLine = (aim - target.position).normalized()
        val cue = Ball(0, target.position - potLine * 0.45f)
        val physics = world(cue, target)

        val events = ShotEvents()
        physics.strike(potLine, 2.6f, 0f, 0f)
        physics.simulateUntilRest(25f, events)

        assertTrue("object ball should have been potted, events=${events.pocketed}",
            events.pocketed.contains(1))
        assertEquals(1, events.firstContact)
    }

    @Test
    fun `a ball rebounds off a cushion with the angle it arrived at`() {
        val cue = Ball(0, Vec2(0f, 0f))
        val physics = world(cue)
        physics.strike(Vec2(1f, 1f).normalized(), 2.5f, 0f, 0f)
        // Run until it has bounced off the far long rail.
        var elapsed = 0f
        while (elapsed < 1.2f) {
            physics.step(PoolPhysics.FIXED_STEP)
            elapsed += PoolPhysics.FIXED_STEP
            if (cue.velocity.y < 0f) break
        }
        assertTrue("the cue ball never came off the cushion", cue.velocity.y < 0f)
        assertTrue("the rebound should keep travelling down the table", cue.velocity.x > 0f)
    }

    @Test
    fun `a full speed ball never tunnels through another ball`() {
        val target = Ball(1, Vec2(0.4f, 0f))
        val cue = Ball(0, Vec2(-1f, 0f))
        val physics = world(cue, target)
        val events = ShotEvents()
        physics.strike(Vec2(1f, 0f), 9f, 0f, 0f)
        physics.simulateUntilRest(25f, events)
        assertEquals("the cue ball passed straight through the object ball", 1, events.firstContact)
    }

    @Test
    fun `no ball ever finishes outside the table`() {
        val physics = PoolPhysics(Rack.build(Random(3)), ClothProperties.FAST)
        physics.strike(Vec2(1f, 0.05f).normalized(), 9f, 0.9f, 0.4f)
        physics.simulateUntilRest(30f)
        val limitX = TableGeometry.HALF_LENGTH + 0.09f
        val limitY = TableGeometry.HALF_WIDTH + 0.09f
        physics.balls.filter { !it.pocketed }.forEach {
            assertTrue("ball ${it.number} escaped at ${it.position}",
                abs(it.position.x) <= limitX && abs(it.position.y) <= limitY)
        }
    }

    @Test
    fun `draw brings the cue ball back towards the shooter`() {
        val target = Ball(1, Vec2(0f, 0f))
        val cue = Ball(0, Vec2(-0.35f, 0f))
        val physics = world(cue, target)
        physics.strike(Vec2(1f, 0f), 3.2f, 0f, -1f)
        physics.simulateUntilRest(25f)
        assertTrue(
            "with full draw the cue ball should end up behind the contact point, was ${cue.position}",
            cue.position.x < -0.05f
        )
    }

    @Test
    fun `the rack is legal and fits on the table`() {
        val balls = Rack.build(Random(11))
        assertEquals(16, balls.size)
        assertEquals((0..15).toSet(), balls.map { it.number }.toSet())
        balls.forEach {
            assertTrue("ball ${it.number} is off the table at ${it.position}",
                TableGeometry.isInsideCushions(it.position))
        }
        for (i in balls.indices) {
            for (j in i + 1 until balls.size) {
                val gap = balls[i].position.distanceTo(balls[j].position)
                assertTrue(
                    "balls ${balls[i].number} and ${balls[j].number} overlap in the rack",
                    gap >= TableGeometry.BALL_DIAMETER - 1e-4f
                )
            }
        }
        assertNotNull(balls.firstOrNull { it.number == 8 })
    }

    /** Records the impacts a shot produces, standing in for the audio layer. */
    private class RecordingListener : CollisionListener {
        val ballHits = mutableListOf<Float>()
        val cushionHits = mutableListOf<Float>()
        val pocketed = mutableListOf<Int>()
        var cueStrikes = 0

        override fun onBallCollision(speed: Float, position: Vec2, cueBallInvolved: Boolean) {
            ballHits.add(speed)
        }

        override fun onCushionCollision(speed: Float, position: Vec2) {
            cushionHits.add(speed)
        }

        override fun onPocketed(ballNumber: Int, speed: Float) {
            pocketed.add(ballNumber)
        }

        override fun onCueStrike(speed: Float) {
            cueStrikes++
        }
    }

    @Test
    fun `impacts are reported as they happen`() {
        val target = Ball(1, Vec2(0.3f, 0f))
        val cue = Ball(0, Vec2(-0.6f, 0f))
        val physics = world(cue, target)
        val listener = RecordingListener()
        physics.collisionListener = listener

        physics.strike(Vec2(1f, 0f), 4f, 0f, 0f)
        physics.simulateUntilRest(25f)

        assertEquals("the cue strike should be reported once", 1, listener.cueStrikes)
        assertTrue("the ball on ball hit was not reported", listener.ballHits.isNotEmpty())
        assertTrue(
            "the closing speed should be close to the shot speed, was ${listener.ballHits.first()}",
            listener.ballHits.first() > 2.5f
        )
        assertTrue("the object ball should have reached a cushion", listener.cushionHits.isNotEmpty())
    }

    @Test
    fun `a potted ball is reported to the listener`() {
        val pocket = TableGeometry.pockets.first { it.id.name == "TOP_RIGHT" }
        val target = Ball(1, Vec2(0.55f, 0.28f))
        val potLine = (TableGeometry.aimPoint(pocket) - target.position).normalized()
        val cue = Ball(0, target.position - potLine * 0.45f)
        val physics = world(cue, target)
        val listener = RecordingListener()
        physics.collisionListener = listener

        physics.strike(potLine, 2.6f, 0f, 0f)
        physics.simulateUntilRest(25f)

        assertTrue("the drop was not reported", listener.pocketed.contains(1))
    }

    @Test
    fun `a rehearsal on a copied table is silent`() {
        // The robot plays out dozens of shots per turn on copies of the table. If a copy
        // carried the listener every one of them would fire sounds for a shot the player
        // has not taken yet.
        val physics = PoolPhysics(Rack.build(Random(9)), ClothProperties.TOURNAMENT)
        val listener = RecordingListener()
        physics.collisionListener = listener

        val rehearsal = physics.copy()
        assertNull("the copy must not carry the listener", rehearsal.collisionListener)

        rehearsal.strike(Vec2(1f, 0.01f).normalized(), 9f, 0f, 0f)
        rehearsal.simulateUntilRest(30f)

        assertEquals("a rehearsal fired a cue strike", 0, listener.cueStrikes)
        assertTrue("a rehearsal fired ball impacts", listener.ballHits.isEmpty())
        assertTrue("a rehearsal fired cushion impacts", listener.cushionHits.isEmpty())
        assertTrue("a rehearsal fired pocket drops", listener.pocketed.isEmpty())
    }

    @Test
    fun `the simulation is independent of the frame rate`() {
        fun run(frameSeconds: Float): List<Vec2> {
            val physics = PoolPhysics(Rack.build(Random(5)), ClothProperties.TOURNAMENT)
            physics.trackOrientation = false
            physics.strike(Vec2(1f, 0.02f).normalized(), 7f, 0.2f, 0.1f)
            var guard = 0
            while (physics.anyBallMoving() && guard < 20000) {
                physics.advance(frameSeconds)
                guard++
            }
            return physics.balls.map { it.position }
        }

        val at60 = run(1f / 60f)
        val at120 = run(1f / 120f)
        at60.forEachIndexed { index, position ->
            assertEquals("ball $index drifted between frame rates on x",
                position.x.toDouble(), at120[index].x.toDouble(), 1e-3)
            assertEquals("ball $index drifted between frame rates on y",
                position.y.toDouble(), at120[index].y.toDouble(), 1e-3)
        }
    }
}
