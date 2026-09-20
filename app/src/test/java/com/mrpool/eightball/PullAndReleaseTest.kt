package com.mrpool.eightball

import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PoolTableSkin
import com.mrpool.eightball.game.GameController
import com.mrpool.eightball.game.Vec2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/**
 * Drawing the cue back with a finger and letting go to play the shot.
 *
 * The finger holds the butt of the cue, so one movement sets both things a shot needs:
 * the line it goes down and how hard it is hit.
 */
class PullAndReleaseTest {

    private fun controller(): GameController = GameController(
        cue = CueStick.ALL.first(),
        table = PoolTableSkin.ALL.first(),
        difficulty = null,
        playerName = "You",
        opponentName = "Them",
        scope = CoroutineScope(Dispatchers.Unconfined),
        random = Random(3)
    )

    /** Where the cue ball is sitting. */
    private fun cueBall(controller: GameController): Vec2 =
        controller.session.physics.cueBall!!.position

    /** A point [distance] behind the cue ball, along the line the cue is aimed down. */
    private fun behindBall(controller: GameController, distance: Float): Vec2 =
        cueBall(controller) - Vec2.fromAngle(controller.aimAngle) * distance

    /** How far behind the ball the finger has to sit to wind the cue up to [power]. */
    private fun distanceFor(power: Float): Float =
        GameController.AIM_DEAD_ZONE + GameController.MAX_PULL_DISTANCE * power

    /** The smaller angle between two headings, in radians. */
    private fun angleBetween(a: Float, b: Float): Float {
        var difference = abs(a - b) % (2f * PI.toFloat())
        if (difference > PI.toFloat()) difference = 2f * PI.toFloat() - difference
        return difference
    }

    @Test
    fun `the cue is taken hold of by touching the ball, not the far end of the table`() {
        val controller = controller()
        assertFalse(
            "a touch across the table should aim, not grab the cue",
            controller.beginPull(cueBall(controller) + Vec2(0.9f, 0.4f))
        )
        assertFalse(controller.isPullingBack)

        assertTrue(controller.beginPull(cueBall(controller)))
        assertTrue(controller.isPullingBack)
    }

    @Test
    fun `power follows how far back the cue is drawn`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))

        controller.updatePull(behindBall(controller, distanceFor(0.25f)))
        assertEquals(0.25f, controller.power, 0.02f)

        controller.updatePull(behindBall(controller, distanceFor(0.5f)))
        assertEquals(0.5f, controller.power, 0.02f)

        controller.updatePull(behindBall(controller, distanceFor(1f)))
        assertEquals(1f, controller.power, 0.02f)
    }

    @Test
    fun `drawing back past the end of the stroke is still full power, not more`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, GameController.MAX_PULL_DISTANCE * 4f))
        assertEquals(1f, controller.power, 1e-4f)
    }

    @Test
    fun `drawing straight back leaves the aim exactly where it was`() {
        val controller = controller()
        val aimed = controller.aimAngle
        controller.beginPull(cueBall(controller))

        controller.updatePull(behindBall(controller, distanceFor(0.3f)))
        controller.updatePull(behindBall(controller, distanceFor(0.7f)))

        assertEquals(
            "pulling back along the line of the shot must not move the line",
            aimed,
            controller.aimAngle,
            1e-4f
        )
    }

    @Test
    fun `swinging the finger round swings the shot with it`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, distanceFor(0.5f)))
        val aimed = controller.aimAngle

        // Round to the side, the same distance from the ball: same power, new line.
        val swung = cueBall(controller) -
            Vec2.fromAngle(aimed + 0.6f) * distanceFor(0.5f)
        controller.updatePull(swung)

        assertEquals(
            "the shot should now go where the cue points",
            0.6f,
            angleBetween(aimed, controller.aimAngle),
            0.02f
        )
        assertEquals(
            "swinging round should not change how hard the ball is hit",
            0.5f,
            controller.power,
            0.02f
        )
    }

    @Test
    fun `carrying the finger past the ball turns the shot round`() {
        val controller = controller()
        val aimed = controller.aimAngle
        controller.beginPull(cueBall(controller))
        // Past the ball, onto the side it was aimed at: the cue is now behind it the other way.
        controller.updatePull(behindBall(controller, -distanceFor(0.4f)))

        assertEquals(
            "the shot should have turned about",
            PI.toFloat(),
            angleBetween(aimed, controller.aimAngle),
            0.02f
        )
        assertEquals(0.4f, controller.power, 0.02f)
    }

    @Test
    fun `a finger resting on the ball does not send the cue spinning`() {
        val controller = controller()
        val aimed = controller.aimAngle
        controller.beginPull(cueBall(controller))

        // Small wanders right on top of the ball, where there is no line to read.
        controller.updatePull(cueBall(controller) + Vec2(0.01f, -0.02f))
        controller.updatePull(cueBall(controller) + Vec2(-0.02f, 0.005f))

        assertEquals("the cue must hold still under the ball", aimed, controller.aimAngle, 1e-4f)
        assertEquals("and stay unwound", 0f, controller.power, 1e-4f)
    }

    @Test
    fun `letting go plays the shot`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, distanceFor(0.8f)))

        assertTrue("letting go should have played the shot", controller.releasePull())
        assertFalse(controller.isPullingBack)

        // The stroke plays out, then the balls move.
        var elapsed = 0f
        while (elapsed < 2f && !controller.uiState.value.shotInProgress) {
            controller.update(1f / 60f)
            elapsed += 1f / 60f
        }
        assertTrue("the shot never went off", controller.uiState.value.shotInProgress)
    }

    @Test
    fun `the shot goes down the line the finger left the cue on`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, distanceFor(0.6f)))

        // Swing round late, just before letting go.
        val swung = cueBall(controller) -
            Vec2.fromAngle(controller.aimAngle + 0.5f) * distanceFor(0.6f)
        controller.updatePull(swung)
        val aimed = controller.aimAngle
        controller.releasePull()

        // Play the stroke out until the tip arrives and the ball sets off.
        var elapsed = 0f
        while (elapsed < 2f && controller.session.physics.cueBall!!.velocity.length() < 0.1f) {
            controller.update(1f / 60f)
            elapsed += 1f / 60f
        }

        val travelling = controller.session.physics.cueBall!!.velocity
        assertTrue("the cue ball should be moving", travelling.length() > 0.1f)
        assertEquals(
            "the ball must set off down the line the cue was left on",
            0f,
            angleBetween(aimed, travelling.angle()),
            0.05f
        )
    }

    @Test
    fun `letting go without drawing back does not waste the shot`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, GameController.AIM_DEAD_ZONE + 0.005f))

        assertFalse("a tap should not fire the cue", controller.releasePull())
        assertFalse(controller.isPullingBack)

        var elapsed = 0f
        while (elapsed < 1f) {
            controller.update(1f / 60f)
            elapsed += 1f / 60f
        }
        assertFalse("the table should be untouched", controller.uiState.value.shotInProgress)
        assertTrue("the player should still be able to shoot", controller.canPlayerAct())
    }

    @Test
    fun `a pull can be abandoned`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, distanceFor(1f)))
        controller.cancelPull()

        assertFalse(controller.isPullingBack)
        var elapsed = 0f
        while (elapsed < 1f) {
            controller.update(1f / 60f)
            elapsed += 1f / 60f
        }
        assertFalse("an abandoned pull must not fire", controller.uiState.value.shotInProgress)
    }

    @Test
    fun `the cue cannot be drawn back while the balls are still moving`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, distanceFor(1f)))
        controller.releasePull()

        var elapsed = 0f
        while (elapsed < 2f && !controller.uiState.value.shotInProgress) {
            controller.update(1f / 60f)
            elapsed += 1f / 60f
        }

        assertFalse(
            "the cue must not be grabbable mid shot",
            controller.beginPull(cueBall(controller))
        )
    }

    @Test
    fun `drawing back is reported, so the power bar can follow the finger`() {
        val controller = controller()
        assertFalse(controller.uiState.value.pullingBack)

        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, distanceFor(0.5f)))

        val state = controller.uiState.value
        assertTrue(state.pullingBack)
        assertEquals("the bar should show what the finger is holding", 0.5f, state.power, 0.02f)
    }
}
