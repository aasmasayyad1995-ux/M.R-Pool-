package com.mrpool.eightball

import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PoolTableSkin
import com.mrpool.eightball.game.GameController
import com.mrpool.eightball.game.Vec2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** What the cue carries over from one shot to the next, and what it must not. */
class BetweenShotsTest {

    private fun controller(): GameController = GameController(
        cue = CueStick.ALL.first(),
        table = PoolTableSkin.ALL.first(),
        difficulty = null,
        playerName = "You",
        opponentName = "Them",
        scope = CoroutineScope(Dispatchers.Unconfined),
        random = Random(11)
    )

    /** Draws back and lets go, then runs the table until it is still again. */
    private fun playAShot(controller: GameController) {
        val cueBall = controller.session.physics.cueBall!!.position
        controller.beginPull(cueBall)
        controller.updatePull(
            cueBall - Vec2.fromAngle(controller.aimAngle) *
                (GameController.AIM_DEAD_ZONE + GameController.MAX_PULL_DISTANCE * 0.8f)
        )
        controller.releasePull()

        var elapsed = 0f
        while (elapsed < 30f) {
            controller.update(1f / 60f)
            elapsed += 1f / 60f
            if (elapsed > 1f && !controller.uiState.value.shotInProgress) return
        }
    }

    @Test
    fun `spin does not carry over into the next shot`() {
        val controller = controller()
        controller.setSpin(1f, -1f)                 // full right hand side, full draw
        val dialledIn = controller.spin
        assertTrue("the test needs the spin to have gone on", dialledIn.length() > 0.1f)

        playAShot(controller)

        // A player who puts draw on one shot and then closes the spin pad has no way of
        // seeing that it is still on. The next shot should start from a plain centre ball.
        assertEquals(
            "the cue should be back on centre ball for the next shot",
            0f,
            controller.spin.length(),
            0.0001f
        )
    }

    @Test
    fun `the power bar comes back to rest after a shot`() {
        val controller = controller()
        playAShot(controller)
        assertEquals(
            "the bar should not still be showing the last shot's power",
            GameController.RESTING_POWER,
            controller.power,
            0.0001f
        )
    }
}
