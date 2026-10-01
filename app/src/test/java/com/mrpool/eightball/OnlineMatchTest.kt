package com.mrpool.eightball

import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.ai.RobotPlayer
import com.mrpool.eightball.game.ClothProperties
import com.mrpool.eightball.game.GamePhase
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.GameSnapshot
import com.mrpool.eightball.game.PlayerState
import com.mrpool.eightball.game.Seat
import com.mrpool.eightball.game.Vec2
import com.mrpool.eightball.net.MatchMove
import com.mrpool.eightball.net.MatchProtocol
import com.mrpool.eightball.net.MatchTransport
import com.mrpool.eightball.net.OnlineMatch
import com.mrpool.eightball.net.OnlineStatus
import com.mrpool.eightball.net.StateChecksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A transport that hands everything straight to its twin, with a settable delay in moves
 * so out of order delivery can be exercised.
 */
private class LoopbackTransport(val name: String) : MatchTransport {
    lateinit var peer: LoopbackTransport

    override var onRemoteMove: ((Int, MatchMove) -> Unit)? = null
    override var onRemoteChecksum: ((Int, String) -> Unit)? = null
    override var onSnapshot: ((Int, GameSnapshot) -> Unit)? = null
    override var onOpponentGone: (() -> Unit)? = null

    /** When true, deliveries queue up instead of arriving, to simulate a stalled network. */
    var paused = false
    private val held = mutableListOf<() -> Unit>()

    var movesSent = 0
        private set
    var snapshotsSent = 0
        private set

    private fun deliver(action: () -> Unit) {
        if (paused) held.add(action) else action()
    }

    fun flush() {
        paused = false
        val pending = held.toList()
        held.clear()
        pending.forEach { it() }
    }

    /** Delivers the held messages back to front, which is the worst case for ordering. */
    fun flushReversed() {
        paused = false
        val pending = held.toList().reversed()
        held.clear()
        pending.forEach { it() }
    }

    override fun sendMove(index: Int, move: MatchMove) {
        movesSent++
        // Round tripped through the wire format, exactly as the database would.
        val decoded = MatchProtocol.decodeMove(MatchProtocol.encode(move))
        requireNotNull(decoded) { "$name produced a move the protocol could not read" }
        deliver { peer.onRemoteMove?.invoke(index, decoded) }
    }

    override fun sendChecksum(index: Int, checksum: String) {
        deliver { peer.onRemoteChecksum?.invoke(index, checksum) }
    }

    override fun sendSnapshot(index: Int, snapshot: GameSnapshot) {
        snapshotsSent++
        val decoded = MatchProtocol.decodeSnapshot(MatchProtocol.encode(snapshot))
        requireNotNull(decoded) { "$name produced a snapshot the protocol could not read" }
        deliver { peer.onSnapshot?.invoke(index, decoded) }
    }

    override fun close() {
        held.clear()
    }
}

/** Two OnlineMatch instances wired to each other, standing in for two phones. */
private class FakeNetwork(seed: Int) {
    val hostTransport = LoopbackTransport("host")
    val guestTransport = LoopbackTransport("guest")
    val host: OnlineMatch
    val guest: OnlineMatch

    init {
        hostTransport.peer = guestTransport
        guestTransport.peer = hostTransport
        host = OnlineMatch(newSession(seed), Seat.ONE, isHost = true, hostTransport)
        guest = OnlineMatch(newSession(seed), Seat.TWO, isHost = false, guestTransport)
    }

    private fun newSession(seed: Int) = GameSession(
        PlayerState("Host", isRobot = false),
        PlayerState("Guest", isRobot = false),
        ClothProperties.TOURNAMENT,
        Random(seed)
    )

    /** Runs both tables until neither has a shot rolling. */
    fun settle(seconds: Float = 40f) {
        var elapsed = 0f
        while (elapsed < seconds) {
            host.update(1f / 60f)
            guest.update(1f / 60f)
            elapsed += 1f / 60f
            if (!host.session.isShooting && !guest.session.isShooting && elapsed > 0.2f) return
        }
    }

    fun tablesAgree(): Boolean = StateChecksum.of(host.session) == StateChecksum.of(guest.session)

    fun describe(): String = buildString {
        append("host=").append(StateChecksum.of(host.session))
        append(" guest=").append(StateChecksum.of(guest.session))
        append(" hostSeat=").append(host.session.currentSeat)
        append(" guestSeat=").append(guest.session.currentSeat)
    }
}

class OnlineMatchTest {

    @Test
    fun `both devices rack the same table from the same seed`() {
        val network = FakeNetwork(seed = 21)
        assertTrue("the two racks differ before a ball is struck", network.tablesAgree())
    }

    @Test
    fun `a shot taken on one device plays out identically on the other`() {
        val network = FakeNetwork(seed = 3)

        assertTrue(network.host.isLocalTurn)
        assertFalse(network.guest.isLocalTurn)

        assertTrue(network.host.submitShot(angle = 0.02f, power = 0.95f, sideSpin = 0f, topSpin = 0f))
        network.settle()

        assertTrue("the break diverged: ${network.describe()}", network.tablesAgree())
        assertEquals(1, network.host.appliedMoves)
        assertEquals(1, network.guest.appliedMoves)
    }

    @Test
    fun `a device cannot move out of turn`() {
        val network = FakeNetwork(seed = 5)
        // It is the host's break; the guest must not be able to shoot.
        assertFalse(network.guest.submitShot(0f, 0.9f, 0f, 0f))
        assertEquals(0, network.guestTransport.movesSent)
        assertEquals(0, network.guest.appliedMoves)
    }

    @Test
    fun `a full match stays in step from break to eight ball`() {
        // Two robots play each other over the fake network. Every shot crosses the wire as
        // four numbers and is replayed on the far side; if the physics or the rules drifted
        // even slightly, the fingerprints would stop matching long before the eight ball.
        val network = FakeNetwork(seed = 11)
        val brains = mapOf(
            Seat.ONE to RobotPlayer(RobotDifficulty.MEDIUM, Random(1)),
            Seat.TWO to RobotPlayer(RobotDifficulty.MEDIUM, Random(2))
        )

        var moves = 0
        while (network.host.session.phase != GamePhase.GAME_OVER && moves < 260) {
            val mover = if (network.host.isLocalTurn) network.host else network.guest
            val seat = mover.localSeat
            val brain = brains.getValue(seat)

            if (mover.session.phase == GamePhase.BALL_IN_HAND) {
                val spot = brain.planCueBallPlacement(mover.session)
                assertTrue("placement rejected at move $moves", mover.submitPlacement(spot))
            } else {
                val shot = brain.planShot(mover.session)
                assertTrue(
                    "shot rejected at move $moves",
                    mover.submitShot(shot.direction.angle(), shot.power, shot.sideSpin, shot.topSpin)
                )
                network.settle()
            }
            moves++

            assertTrue(
                "the tables drifted apart after move $moves: ${network.describe()}",
                network.tablesAgree()
            )
            assertEquals(
                "the two devices disagree about whose turn it is after move $moves",
                network.host.session.currentSeat,
                network.guest.session.currentSeat
            )
        }

        assertEquals(
            "the game did not finish on both devices",
            network.host.session.phase,
            network.guest.session.phase
        )
        assertEquals(GamePhase.GAME_OVER, network.host.session.phase)
        assertEquals(network.host.session.winner, network.guest.session.winner)
        assertEquals("a clean match should never need repairing", 0, network.host.repairs)
        assertTrue("the match ended in no moves at all", moves > 4)
    }

    @Test
    fun `moves that arrive out of order are applied in order`() {
        val network = FakeNetwork(seed = 7)

        // The host breaks and pots nothing in particular, then the guest replies. Hold both
        // deliveries and release them backwards.
        network.hostTransport.paused = true
        assertTrue(network.host.submitShot(0.01f, 0.95f, 0f, 0f))
        // The host's own table runs on regardless of what the network is doing.
        var elapsed = 0f
        while (network.host.session.isShooting && elapsed < 40f) {
            network.host.update(1f / 60f)
            elapsed += 1f / 60f
        }

        network.hostTransport.flushReversed()

        elapsed = 0f
        while (network.guest.session.isShooting && elapsed < 40f) {
            network.guest.update(1f / 60f)
            elapsed += 1f / 60f
        }

        assertEquals(1, network.guest.appliedMoves)
        assertTrue("out of order delivery broke the table: ${network.describe()}", network.tablesAgree())
    }

    @Test
    fun `a duplicate delivery is ignored`() {
        val network = FakeNetwork(seed = 13)
        assertTrue(network.host.submitShot(0.01f, 0.9f, 0f, 0f))
        network.settle()
        val before = network.guest.appliedMoves

        // The same move delivered a second time, as a flaky connection might.
        network.guestTransport.onRemoteMove?.invoke(0, MatchMove.Shoot(Seat.ONE, 0.01f, 0.9f, 0f, 0f))
        network.settle()

        assertEquals("a replayed move was applied twice", before, network.guest.appliedMoves)
        assertTrue(network.tablesAgree())
    }

    @Test
    fun `a drifted guest is repaired from the host's table`() {
        val network = FakeNetwork(seed = 17)
        assertTrue(network.host.submitShot(0.01f, 0.92f, 0f, 0f))
        network.settle()
        assertTrue(network.tablesAgree())

        // Shove one of the guest's balls, exactly as accumulated float drift would.
        val strayBall = network.guest.session.physics.balls.first { !it.pocketed && !it.isCue }
        strayBall.position = strayBall.position + Vec2(0.06f, 0.03f)
        assertFalse("the test did not actually break anything", network.tablesAgree())

        // The next completed move exchanges fingerprints, which no longer match.
        val mover = if (network.host.isLocalTurn) network.host else network.guest
        if (mover.session.phase == GamePhase.BALL_IN_HAND) {
            mover.submitPlacement(Vec2(-0.6f, 0f))
        } else {
            mover.submitShot(0.4f, 0.4f, 0f, 0f)
            network.settle()
        }

        assertTrue("the host never sent a repair", network.hostTransport.snapshotsSent > 0)
        assertTrue("the guest was not repaired: ${network.describe()}", network.tablesAgree())
        assertEquals(OnlineStatus.PLAYING, network.guest.status)
        assertTrue(network.guest.repairs > 0)
    }

    @Test
    fun `a forfeit tells the other player rather than leaving them waiting`() {
        val network = FakeNetwork(seed = 19)
        network.guest.forfeit()
        assertEquals(OnlineStatus.OPPONENT_GONE, network.host.status)
        assertFalse("a finished match must not still accept shots", network.host.isLocalTurn)
    }

    @Test
    fun `the checksum notices a moved ball but tolerates rounding noise`() {
        val session = GameSession(
            PlayerState("A", isRobot = false),
            PlayerState("B", isRobot = false),
            ClothProperties.TOURNAMENT,
            Random(31)
        )
        val original = StateChecksum.of(session)

        // Noise far below the quantisation step must not register as a desync.
        val ball = session.physics.balls.first { !it.isCue }
        ball.position = ball.position + Vec2(StateChecksum.PRECISION / 8f, 0f)
        assertEquals("rounding noise was reported as a desync", original, StateChecksum.of(session))

        // A real move must.
        ball.position = ball.position + Vec2(0.01f, 0f)
        assertTrue("a moved ball went unnoticed", original != StateChecksum.of(session))
    }

    @Test
    fun `the protocol survives a round trip, and rejects nonsense`() {
        val shoot = MatchMove.Shoot(Seat.TWO, 1.23f, 0.75f, -0.4f, 0.2f)
        assertEquals(shoot, MatchProtocol.decodeMove(MatchProtocol.encode(shoot)))

        val place = MatchMove.PlaceCueBall(Seat.ONE, -0.42f, 0.11f)
        assertEquals(place, MatchProtocol.decodeMove(MatchProtocol.encode(place)))

        val forfeit = MatchMove.Forfeit(Seat.ONE)
        assertEquals(forfeit, MatchProtocol.decodeMove(MatchProtocol.encode(forfeit)))

        assertNull(MatchProtocol.decodeMove(null))
        assertNull(MatchProtocol.decodeMove(emptyMap()))
        assertNull(MatchProtocol.decodeMove(mapOf("v" to 1, "type" to "shoot", "seat" to "ONE")))
        assertNull(
            "a future protocol version must be refused, not misread",
            MatchProtocol.decodeMove(
                MatchProtocol.encode(shoot) + ("v" to MatchProtocol.VERSION + 1)
            )
        )
    }

    @Test
    fun `a snapshot survives a round trip`() {
        val session = GameSession(
            PlayerState("A", isRobot = false),
            PlayerState("B", isRobot = false),
            ClothProperties.TOURNAMENT,
            Random(41)
        )
        session.physics.balls.first { it.number == 3 }.pocketed = true
        val snapshot = session.snapshot()

        val decoded = MatchProtocol.decodeSnapshot(MatchProtocol.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(snapshot.balls.size, decoded!!.balls.size)
        assertEquals(snapshot.currentSeat, decoded.currentSeat)
        assertEquals(snapshot.phase, decoded.phase)
        assertEquals(snapshot.tableOpen, decoded.tableOpen)
        assertTrue("the pocketed ball was lost in transit",
            decoded.balls.first { it.number == 3 }.pocketed)
    }

    // ------------------------------------------------- moves the other table refuses

    /** Puts both tables into a chosen state the way the repair path does. */
    private fun FakeNetwork.setBoth(phase: GamePhase, seat: Seat) {
        val state = host.session.snapshot().copy(currentSeat = seat, phase = phase)
        host.session.restore(state)
        guest.session.restore(state)
    }

    @Test
    fun `a placement the other table thinks is illegal does not strand the match`() {
        // Why this happens: placeCueBall asks whether the spot is clear of every other
        // ball, by a threshold. Two devices that differ in the last bit of a float can
        // answer that differently -- which is the whole reason the checksum exists. The
        // sender places and moves on; the receiver refuses, and the move was already gone
        // from the buffer, so nothing ever applied it and nothing ever retried it.
        val net = FakeNetwork(seed = 11)
        net.setBoth(GamePhase.BALL_IN_HAND, Seat.ONE)

        // Free on the host's table, occupied on the guest's.
        val spot = Vec2(-0.7f, 0.2f)
        net.guest.session.physics.balls.first { !it.isCue }.position = spot

        assertTrue("the host sees a legal spot", net.host.submitPlacement(spot))
        net.settle()

        assertEquals(
            "the guest is still waiting for a move that was thrown away: ${net.describe()}",
            net.host.appliedMoves,
            net.guest.appliedMoves
        )
    }

    @Test
    fun `a move that cannot land yet is retried rather than lost`() {
        val net = FakeNetwork(seed = 12)
        net.setBoth(GamePhase.BALL_IN_HAND, Seat.ONE)
        // The guest has not reached ball in hand yet, so the placement cannot land when it
        // arrives. A moment later it catches up, and the move must still be there.
        val behind = net.guest.session.snapshot().copy(phase = GamePhase.AIMING)
        net.guest.session.restore(behind)

        val spot = Vec2(-0.6f, -0.15f)
        assertTrue(net.host.submitPlacement(spot))
        assertEquals("the guest could not take it yet", 0, net.guest.appliedMoves)

        net.guest.session.restore(net.guest.session.snapshot().copy(phase = GamePhase.BALL_IN_HAND))
        net.settle()

        assertEquals(
            "the move should have been retried once the guest could take it: ${net.describe()}",
            1,
            net.guest.appliedMoves
        )
    }

    @Test
    fun `a forfeit is honoured even when it arrives with an old index`() {
        // forfeit() stamps the move with the leaver's own move count. If the other side is
        // already further on -- easy enough when a move is still in flight the other way --
        // the index is behind, receiveMove drops it as a duplicate, and the player left
        // behind waits at a still table for a game nobody is playing.
        val net = FakeNetwork(seed = 13)
        net.setBoth(GamePhase.AIMING, Seat.TWO)

        // The guest moves, and the news does not reach the host.
        net.guestTransport.paused = true
        assertTrue(net.guest.submitShot(0.5f, 0.6f, 0f, 0f))
        net.settle()
        assertTrue("the guest is ahead of the host", net.guest.appliedMoves > net.host.appliedMoves)

        net.host.forfeit()
        net.settle()

        assertEquals(
            "the guest was never told: ${net.describe()}",
            OnlineStatus.OPPONENT_GONE,
            net.guest.status
        )
    }

    @Test
    fun `a move that can never land gets the guest repaired instead of hanging it`() {
        // Retrying is right for a table that is a step behind. It is not enough on its own:
        // a table that genuinely disagrees would refuse the same move forever and the match
        // would stop with no error anywhere. After a while the guest says so with a
        // fingerprint it knows will differ, and the host's repair puts it back in the game.
        val net = FakeNetwork(seed = 14)
        net.setBoth(GamePhase.AIMING, Seat.ONE)
        // A guest that thinks the game is over will never accept a shot.
        net.guest.session.restore(net.guest.session.snapshot().copy(phase = GamePhase.GAME_OVER))

        assertTrue(net.host.submitShot(0.3f, 0.8f, 0f, 0f))
        // Driven like the real game loop, which keeps running whether or not a ball is
        // rolling. settle() stops as soon as both tables are still, and a short shot can
        // be over before a stuck table has been refused long enough to say anything.
        repeat(200) {
            net.host.update(1f / 60f)
            net.guest.update(1f / 60f)
        }

        assertEquals(
            "the guest should have been repaired back into the game: ${net.describe()}",
            net.host.appliedMoves,
            net.guest.appliedMoves
        )
        assertTrue("and the repair should be counted, not hidden", net.guest.repairs > 0)
        assertEquals(OnlineStatus.PLAYING, net.guest.status)
    }
}
