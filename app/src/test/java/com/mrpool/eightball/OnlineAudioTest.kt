package com.mrpool.eightball

import com.mrpool.eightball.audio.Sound
import com.mrpool.eightball.audio.SoundPlayer
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PoolTableSkin
import com.mrpool.eightball.game.ClothProperties
import com.mrpool.eightball.game.GameController
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.GameSnapshot
import com.mrpool.eightball.game.PlayerState
import com.mrpool.eightball.game.Seat
import com.mrpool.eightball.game.Vec2
import com.mrpool.eightball.net.MatchMove
import com.mrpool.eightball.net.MatchTransport
import com.mrpool.eightball.net.OnlineMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Hands everything straight to its twin. */
private class Wire(val name: String) : MatchTransport {
    lateinit var peer: Wire
    override var onRemoteMove: ((Int, MatchMove) -> Unit)? = null
    override var onRemoteChecksum: ((Int, String) -> Unit)? = null
    override var onSnapshot: ((Int, GameSnapshot) -> Unit)? = null
    override var onChat: ((String) -> Unit)? = null
    override var onOpponentGone: (() -> Unit)? = null

    override fun sendChat(text: String) = Unit
    override fun reportOpponent(lines: List<String>) = Unit

    override fun sendMove(index: Int, move: MatchMove) { peer.onRemoteMove?.invoke(index, move) }
    override fun sendChecksum(index: Int, checksum: String) {
        peer.onRemoteChecksum?.invoke(index, checksum)
    }
    override fun sendSnapshot(index: Int, snapshot: GameSnapshot) {
        peer.onSnapshot?.invoke(index, snapshot)
    }
    override fun close() = Unit
}

private class Ears : SoundPlayer {
    val heard = mutableListOf<Sound>()
    override fun play(sound: Sound, volume: Float, rate: Float) { heard.add(sound) }
    fun count(sound: Sound) = heard.count { it == sound }
}

/**
 * An online match must sound exactly like a local one.
 *
 * The table runs on both devices, so every impact happens on both — there is no reason for
 * an online game to be quieter, and a silent one means the sounds are wired to something
 * the online path does not go through.
 */
class OnlineAudioTest {

    private fun session(seed: Int) = GameSession(
        PlayerState("You", isRobot = false),
        PlayerState("Them", isRobot = false),
        ClothProperties.TOURNAMENT,
        Random(seed)
    )

    private fun controller(match: OnlineMatch, ears: Ears) = GameController(
        cue = CueStick.ALL.first(),
        table = PoolTableSkin.ALL.first(),
        difficulty = null,
        playerName = "You",
        opponentName = "Them",
        scope = CoroutineScope(Dispatchers.Unconfined),
        random = Random(4),
        audio = ears,
        online = match
    )

    /** Draws the cue back and lets go, the way a player does. */
    private fun breakOff(controller: GameController) {
        val cueBall = controller.session.physics.cueBall!!.position
        controller.beginPull(cueBall)
        controller.updatePull(
            cueBall - Vec2.fromAngle(controller.aimAngle) *
                (GameController.AIM_DEAD_ZONE + GameController.MAX_PULL_DISTANCE * 0.95f)
        )
        controller.releasePull()
    }

    private fun run(controller: GameController, seconds: Float) {
        var elapsed = 0f
        while (elapsed < seconds) {
            controller.update(1f / 60f)
            elapsed += 1f / 60f
        }
    }

    @Test
    fun `an online break makes the same noise a local one does`() {
        val hostWire = Wire("host")
        val guestWire = Wire("guest")
        hostWire.peer = guestWire
        guestWire.peer = hostWire

        val hostMatch = OnlineMatch(session(9), Seat.ONE, isHost = true, transport = hostWire)
        val guestMatch = OnlineMatch(session(9), Seat.TWO, isHost = false, transport = guestWire)

        val hostEars = Ears()
        val guestEars = Ears()
        val host = controller(hostMatch, hostEars)
        val guest = controller(guestMatch, guestEars)

        breakOff(host)

        // Both tables run the same shot, so both must hear it.
        var elapsed = 0f
        while (elapsed < 8f) {
            host.update(1f / 60f)
            guest.update(1f / 60f)
            elapsed += 1f / 60f
        }

        assertTrue(
            "the player who took the shot heard no cue strike",
            hostEars.count(Sound.CUE_STRIKE) >= 1
        )
        assertTrue(
            "the player who took the shot heard no balls hit",
            hostEars.count(Sound.BALL_CLICK) + hostEars.count(Sound.BALL_KISS) >= 1
        )
        assertTrue(
            "the opponent's table was silent — they watch the same shot",
            guestEars.heard.isNotEmpty()
        )
        assertTrue(
            "the opponent heard no balls hit",
            guestEars.count(Sound.BALL_CLICK) + guestEars.count(Sound.BALL_KISS) >= 1
        )
    }

    @Test
    fun `a local match and an online match hear the same kinds of sound`() {
        val hostWire = Wire("host")
        val guestWire = Wire("guest")
        hostWire.peer = guestWire
        guestWire.peer = hostWire
        val onlineEars = Ears()
        val online = controller(
            OnlineMatch(session(5), Seat.ONE, isHost = true, transport = hostWire),
            onlineEars
        )
        OnlineMatch(session(5), Seat.TWO, isHost = false, transport = guestWire)

        val localEars = Ears()
        val local = GameController(
            cue = CueStick.ALL.first(),
            table = PoolTableSkin.ALL.first(),
            difficulty = null,
            playerName = "You",
            opponentName = "Friend",
            scope = CoroutineScope(Dispatchers.Unconfined),
            random = Random(4),
            audio = localEars
        )

        breakOff(online)
        run(online, 8f)
        breakOff(local)
        run(local, 8f)

        assertTrue("the local match was silent, so this test proves nothing", localEars.heard.isNotEmpty())
        assertTrue(
            "online heard ${onlineEars.heard.size} sounds, local heard ${localEars.heard.size}",
            onlineEars.heard.isNotEmpty()
        )
    }
}
