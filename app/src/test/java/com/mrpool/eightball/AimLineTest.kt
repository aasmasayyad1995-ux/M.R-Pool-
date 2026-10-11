package com.mrpool.eightball

import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PoolTableSkin
import com.mrpool.eightball.game.GameController
import com.mrpool.eightball.game.TableGeometry
import com.mrpool.eightball.game.Vec2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The aiming guide.
 *
 * It used to draw three lines from the same point: a dashed one for the cue ball, a
 * yellow one for the ball being hit, and a blue one for where the cue ball carried on to
 * afterwards. Three lines leaving one spot is not a picture anybody reads mid-shot, and
 * the blue one answered a question nobody asks while lining up a pot.
 *
 * What is left is one line, and it has to earn its place: it runs from the ball that is
 * about to be hit to wherever that ball actually gets to.
 */
class AimLineTest {

    private fun controller(): GameController = GameController(
        cue = CueStick.ALL.first(),
        table = PoolTableSkin.ALL.first(),
        difficulty = null,
        playerName = "You",
        opponentName = "Them",
        scope = CoroutineScope(Dispatchers.Unconfined),
        random = Random(3)
    )

    /** Clears the table down to the cue ball and whatever is asked for. */
    private fun layOut(controller: GameController, vararg balls: Pair<Int, Vec2>) {
        for (ball in controller.session.physics.balls) {
            if (ball.isCue) continue
            ball.pocketed = true
        }
        for ((number, position) in balls) {
            val ball = controller.session.physics.balls.first { it.number == number }
            ball.pocketed = false
            ball.position = position
            ball.stop()
        }
    }

    private fun placeCue(controller: GameController, at: Vec2) {
        val cue = controller.session.physics.cueBall!!
        cue.position = at
        cue.stop()
        cue.pocketed = false
    }

    @Test
    fun `the line runs from the ball being hit to the cushion it would reach`() {
        val controller = controller()
        layOut(controller, 3 to Vec2(0f, 0f))
        placeCue(controller, Vec2(-0.5f, 0f))
        controller.setAimAngle(0f)

        val preview = controller.aimPreview()!!

        assertNotNull("there is a ball to hit", preview.objectBall)
        assertEquals(0f, preview.objectBall!!.x, 1e-4f)
        assertEquals(1f, preview.objectDirection!!.x, 1e-3f)
        // Straight down the table: from the origin to the far cushion.
        val toCushion = TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS
        assertEquals(toCushion, preview.objectTravel, 2e-3f)
    }

    @Test
    fun `the line stops at the ball in the way, not at the cushion behind it`() {
        val controller = controller()
        layOut(controller, 3 to Vec2(0f, 0f), 5 to Vec2(0.4f, 0f))
        placeCue(controller, Vec2(-0.5f, 0f))
        controller.setAimAngle(0f)

        val preview = controller.aimPreview()!!

        // Two balls touch when their centres are a diameter apart.
        assertEquals(0.4f - TableGeometry.BALL_DIAMETER, preview.objectTravel, 2e-3f)
    }

    @Test
    fun `the ball that is about to be hit does not block its own line`() {
        val controller = controller()
        layOut(controller, 3 to Vec2(0f, 0f))
        placeCue(controller, Vec2(-0.5f, 0f))
        controller.setAimAngle(0f)

        // A ray cast from the ball's own centre must not find the ball it started in,
        // which would collapse the line to nothing.
        assertTrue("the line has length", controller.aimPreview()!!.objectTravel > 0.5f)
    }

    @Test
    fun `aiming at bare cushion leaves no ball line to draw`() {
        val controller = controller()
        layOut(controller)
        placeCue(controller, Vec2(-0.5f, 0f))
        controller.setAimAngle(0f)

        val preview = controller.aimPreview()!!

        assertNull(preview.objectBall)
        assertNull(preview.objectDirection)
        assertNull(preview.ghostBall)
    }

    @Test
    fun `a cut shot sends the line off the line of the cue, not down it`() {
        val controller = controller()
        // Hitting the right hand side of the ball sends it left of the cue's line.
        layOut(controller, 3 to Vec2(0f, 0.02f))
        placeCue(controller, Vec2(-0.5f, 0f))
        controller.setAimAngle(0f)

        val preview = controller.aimPreview()!!
        val objectDir = preview.objectDirection!!

        assertTrue("the ball is thrown across, not straight on", objectDir.y > 0.05f)
        assertTrue("but still forward", objectDir.x > 0.5f)
    }
}
