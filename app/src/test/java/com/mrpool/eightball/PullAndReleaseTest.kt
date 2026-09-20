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
import kotlin.random.Random

/**
 * Drawing the cue back with a finger and letting go to play the shot.
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

        controller.updatePull(behindBall(controller, GameController.MAX_PULL_DISTANCE / 4f))
        assertEquals(0.25f, controller.power, 0.02f)

        controller.updatePull(behindBall(controller, GameController.MAX_PULL_DISTANCE / 2f))
        assertEquals(0.5f, controller.power, 0.02f)

        controller.updatePull(behindBall(controller, GameController.MAX_PULL_DISTANCE))
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
    fun `pushing the cue forwards does not wind it up`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        // Forwards, towards the target: the opposite of drawing back.
        controller.updatePull(behindBall(controller, -0.3f))
        assertEquals(0f, controller.power, 1e-4f)
    }

    @Test
    fun `sliding along the cue rather than back does not wind it up`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        val sideways = Vec2.fromAngle(controller.aimAngle).perpendicular() * 0.3f
        controller.updatePull(cueBall(controller) + sideways)
        assertEquals(
            "moving across the shot should not add power",
            0f,
            controller.power,
            1e-4f
        )
    }

    @Test
    fun `the aim holds still once the cue is drawn back`() {
        val controller = controller()
        val aimed = controller.aimAngle
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, 0.2f))

        controller.aimAt(cueBall(controller) + Vec2(0.5f, 0.5f))
        assertEquals(
            "the shot was already wound up; the aim must not swing under it",
            aimed,
            controller.aimAngle,
            1e-5f
        )
    }

    @Test
    fun `letting go plays the shot`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, GameController.MAX_PULL_DISTANCE * 0.8f))

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
    fun `letting go without drawing back does not waste the shot`() {
        val controller = controller()
        controller.beginPull(cueBall(controller))
        controller.updatePull(behindBall(controller, 0.005f))

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
        controller.updatePull(behindBall(controller, GameController.MAX_PULL_DISTANCE))
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
        controller.updatePull(behindBall(controller, GameController.MAX_PULL_DISTANCE))
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
        controller.updatePull(behindBall(controller, GameController.MAX_PULL_DISTANCE / 2f))

        val state = controller.uiState.value
        assertTrue(state.pullingBack)
        assertEquals("the bar should show what the finger is holding", 0.5f, state.power, 0.02f)
    }
}
