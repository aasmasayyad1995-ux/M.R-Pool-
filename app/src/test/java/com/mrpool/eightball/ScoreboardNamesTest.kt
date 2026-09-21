package com.mrpool.eightball

import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PoolTableSkin
import com.mrpool.eightball.game.ClothProperties
import com.mrpool.eightball.game.GameController
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.GameSnapshot
import com.mrpool.eightball.game.PlayerState
import com.mrpool.eightball.game.Seat
import com.mrpool.eightball.net.MatchMove
import com.mrpool.eightball.net.MatchTransport
import com.mrpool.eightball.net.OnlineMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

private class Silent : MatchTransport {
    override var onRemoteMove: ((Int, MatchMove) -> Unit)? = null
    override var onRemoteChecksum: ((Int, String) -> Unit)? = null
    override var onSnapshot: ((Int, GameSnapshot) -> Unit)? = null
    override var onOpponentGone: (() -> Unit)? = null
    override fun sendMove(index: Int, move: MatchMove) = Unit
    override fun sendChecksum(index: Int, checksum: String) = Unit
    override fun sendSnapshot(index: Int, snapshot: GameSnapshot) = Unit
    override fun close() = Unit
}

/**
 * The scoreboard names the two players.
 *
 * It used to print "You" and "Friend" whatever anyone was called, which also meant the
 * left plate was labelled "You" on both devices — so the player who joined a room, who is
 * seat two, watched their own balls under the other player's name.
 */
class ScoreboardNamesTest {

    private fun controller(
        difficulty: RobotDifficulty?,
        playerName: String,
        opponentName: String,
        online: OnlineMatch? = null
    ) = GameController(
        cue = CueStick.ALL.first(),
        table = PoolTableSkin.ALL.first(),
        difficulty = difficulty,
        playerName = playerName,
        opponentName = opponentName,
        scope = CoroutineScope(Dispatchers.Unconfined),
        random = Random(11),
        online = online
    )

    private fun onlineMatch(seat: Seat, one: String, two: String) = OnlineMatch(
        session = GameSession(
            PlayerState(one, isRobot = false),
            PlayerState(two, isRobot = false),
            ClothProperties.TOURNAMENT,
            Random(3)
        ),
        localSeat = seat,
        isHost = seat == Seat.ONE,
        transport = Silent()
    )

    @Test
    fun `a robot match shows the player's own name, not You`() {
        val state = controller(RobotDifficulty.MEDIUM, "Asad", "Medium Bot").uiState.value
        assertEquals("Asad", state.playerOneName)
        assertEquals("Medium Bot", state.playerTwoName)
    }

    @Test
    fun `a match on one device names both players`() {
        val state = controller(null, "Asad", "Friend").uiState.value
        assertEquals("Asad", state.playerOneName)
        assertEquals("Friend", state.playerTwoName)
        assertNull("there is nobody to tell apart on one phone", state.localSeat)
    }

    @Test
    fun `both devices label the scoreboard the same way round`() {
        val host = controller(
            null, "Asad", "Imran",
            online = onlineMatch(Seat.ONE, one = "Asad", two = "Imran")
        ).uiState.value
        val guest = controller(
            null, "Asad", "Imran",
            online = onlineMatch(Seat.TWO, one = "Asad", two = "Imran")
        ).uiState.value

        assertEquals("Asad", host.playerOneName)
        assertEquals("Imran", host.playerTwoName)
        assertEquals(
            "the two scoreboards must agree, or the plates are swapped on one of them",
            host.playerOneName,
            guest.playerOneName
        )
        assertEquals(host.playerTwoName, guest.playerTwoName)
    }

    @Test
    fun `each device knows which side of the scoreboard is its own`() {
        val host = controller(
            null, "Asad", "Imran",
            online = onlineMatch(Seat.ONE, one = "Asad", two = "Imran")
        ).uiState.value
        val guest = controller(
            null, "Asad", "Imran",
            online = onlineMatch(Seat.TWO, one = "Asad", two = "Imran")
        ).uiState.value

        assertEquals(Seat.ONE, host.localSeat)
        assertEquals(
            "the player who joined is seat two, and had been reading the wrong plate",
            Seat.TWO,
            guest.localSeat
        )
    }

    @Test
    fun `the scoreboard highlights whoever's turn it is, by seat`() {
        val guest = controller(
            null, "Asad", "Imran",
            online = onlineMatch(Seat.TWO, one = "Asad", two = "Imran")
        )
        assertEquals(
            "seat one breaks, so seat one is lit first on every device",
            Seat.ONE,
            guest.uiState.value.currentSeat
        )
    }

    @Test
    fun `an empty name never reaches the scoreboard`() {
        // ProfileStore refuses a blank name, so the scoreboard should never be handed one.
        val state = controller(RobotDifficulty.HARD, "Player", "Hard Bot").uiState.value
        assertEquals("Player", state.playerOneName)
        assertEquals(false, state.playerOneName.isBlank())
        assertEquals(false, state.playerTwoName.isBlank())
    }
}
