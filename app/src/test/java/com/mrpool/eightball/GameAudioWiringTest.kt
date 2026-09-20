package com.mrpool.eightball

import com.mrpool.eightball.audio.Sound
import com.mrpool.eightball.audio.SoundPlayer
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PoolTableSkin
import com.mrpool.eightball.game.GameController
import com.mrpool.eightball.game.Vec2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Records what the game asked to be played, instead of playing it. */
private class RecordingPlayer : SoundPlayer {
    val played = mutableListOf<Pair<Sound, Float>>()

    override fun play(sound: Sound, volume: Float, rate: Float) {
        played.add(sound to volume)
        assertTrue("volume $volume for $sound is out of range", volume in 0f..1f)
        assertTrue("rate $rate for $sound is out of SoundPool's range", rate in 0.5f..2f)
    }

    fun countOf(sound: Sound): Int = played.count { it.first == sound }
}

class GameAudioWiringTest {

    private fun controller(player: SoundPlayer, difficulty: com.mrpool.eightball.ai.RobotDifficulty?): GameController =
        GameController(
            cue = CueStick.ALL.first(),
            table = PoolTableSkin.ALL.first(),
            difficulty = difficulty,
            playerName = "You",
            opponentName = "Them",
            scope = CoroutineScope(Dispatchers.Unconfined),
            random = Random(4),
            audio = player
        )

    /** Plays a full power shot the way a player does: draw the cue back, and let go. */
    private fun breakOff(controller: GameController) {
        val cueBall = controller.session.physics.cueBall!!.position
        assertTrue("could not take hold of the cue", controller.beginPull(cueBall))
        controller.updatePull(
            cueBall - Vec2.fromAngle(controller.aimAngle) * GameController.MAX_PULL_DISTANCE
        )
        assertTrue("letting go did not play the shot", controller.releasePull())
    }

    /** Runs frames until the table is at rest again, or the budget runs out. */
    private fun settle(controller: GameController, seconds: Float = 30f) {
        var elapsed = 0f
        while (elapsed < seconds) {
            controller.update(1f / 60f)
            elapsed += 1f / 60f
            if (!controller.uiState.value.shotInProgress && elapsed > 0.5f) return
        }
    }

    @Test
    fun `a break makes one cue strike and a scatter of impacts`() = runBlocking {
        val player = RecordingPlayer()
        val controller = controller(player, difficulty = null)

        breakOff(controller)
        settle(controller)

        assertEquals("the cue should be struck exactly once", 1, player.countOf(Sound.CUE_STRIKE))
        val impacts = player.countOf(Sound.BALL_CLICK) + player.countOf(Sound.BALL_KISS)
        assertTrue("a break should produce a scatter of clicks, got $impacts", impacts >= 4)
        assertTrue(
            "a break should rattle the cushions",
            player.countOf(Sound.CUSHION) > 0
        )
    }

    @Test
    fun `harder contacts are louder than soft ones`() {
        val player = RecordingPlayer()
        val controller = controller(player, difficulty = null)

        breakOff(controller)
        settle(controller)
        val loudBreak = player.played
            .filter { it.first == Sound.BALL_CLICK }
            .maxOfOrNull { it.second } ?: 0f

        assertTrue("a full power break should be loud, was $loudBreak", loudBreak > 0.7f)
    }

    @Test
    fun `a rematch keeps the table wired to the speaker`() {
        // rematch() builds a brand new session, so it has to re-attach the listener. If it
        // forgets, the first rack is loud and every one after it is silent.
        val player = RecordingPlayer()
        val controller = controller(player, difficulty = null)

        breakOff(controller)
        settle(controller)
        assertTrue("the first rack made no sound", player.played.isNotEmpty())

        controller.rematch()
        player.played.clear()

        breakOff(controller)
        settle(controller)

        assertEquals("the rematch lost the cue strike", 1, player.countOf(Sound.CUE_STRIKE))
        val impacts = player.countOf(Sound.BALL_CLICK) + player.countOf(Sound.BALL_KISS)
        assertTrue("the rematch broke the impact sounds", impacts >= 4)
    }

    @Test
    fun `no sound is requested when the game is muted by leaving the player out`() {
        val controller = GameController(
            cue = CueStick.ALL.first(),
            table = PoolTableSkin.ALL.first(),
            difficulty = null,
            playerName = "You",
            opponentName = "Them",
            scope = CoroutineScope(Dispatchers.Unconfined),
            random = Random(4),
            audio = null
        )
        // Simply must not throw: a null player is the silent path the tests and the
        // muted game both take.
        breakOff(controller)
        settle(controller)
        assertTrue(controller.session.physics.balls.isNotEmpty())
    }
}
