package com.mrpool.eightball

import com.mrpool.eightball.game.Ball
import com.mrpool.eightball.game.BallGroup
import com.mrpool.eightball.game.ClothProperties
import com.mrpool.eightball.game.GamePhase
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.PlayerState
import com.mrpool.eightball.game.Seat
import com.mrpool.eightball.game.TableGeometry
import com.mrpool.eightball.game.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EightBallRulesTest {

    private fun session(): GameSession = GameSession(
        PlayerState("One", isRobot = false),
        PlayerState("Two", isRobot = false),
        ClothProperties.TOURNAMENT,
        Random(42)
    )

    /** Clears the table down to the listed balls and takes the game past the break. */
    private fun layout(session: GameSession, vararg keep: Pair<Int, Vec2>) {
        session.physics.balls.forEach { ball ->
            if (ball.number != 0) {
                ball.pocketed = true
                ball.stop()
            }
        }
        keep.forEach { (number, position) ->
            val ball = session.physics.ball(number)
            requireNotNull(ball) { "ball $number is not on this table" }
            ball.pocketed = false
            ball.position = position
            ball.stop()
        }
    }

    /**
     * Takes a shot at [speed] metres per second.
     *
     * Speed rather than a position on the power bar: the bar's shape is a feel decision
     * that gets tuned, and a test that says "0.36" silently stops meaning "hard enough to
     * reach the pocket" the moment it changes.
     */
    private fun runShotAt(session: GameSession, direction: Vec2, speed: Float) =
        runShot(session, direction, GameSession.powerForSpeed(speed))

    private fun runShot(session: GameSession, direction: Vec2, power: Float) {
        if (session.phase == GamePhase.BALL_IN_HAND) {
            val here = session.physics.cueBall!!.position
            assertTrue(
                "could not place the cue ball to take the shot",
                session.placeCueBall(session.nearestValidCueBallPosition(here))
            )
        }
        session.shoot(direction, power)
        var guard = 0
        while (session.isShooting && guard < 20000) {
            session.update(1f / 120f)
            guard++
        }
        assertFalse("the shot never came to rest", session.isShooting)
    }

    private fun pocket(name: String) = TableGeometry.pockets.first { it.id.name == name }

    /** Places the cue ball so that a straight shot pots [target] into [pocketName]. */
    private fun lineUp(session: GameSession, target: Ball, pocketName: String, gap: Float = 0.4f): Vec2 {
        val aim = TableGeometry.aimPoint(pocket(pocketName))
        val potLine = (aim - target.position).normalized()
        val cue = session.physics.cueBall!!
        cue.position = target.position - potLine * gap
        cue.pocketed = false
        cue.stop()
        return potLine
    }

    @Test
    fun `a legal break leaves the table open`() {
        val session = session()
        runShot(session, Vec2(1f, 0.015f).normalized(), 1f)
        assertTrue("the table should still be open after the break", session.tableOpen)
        assertNull(session.groupOf(Seat.ONE))
        assertTrue(session.phase != GamePhase.GAME_OVER)
    }

    @Test
    fun `potting the first ball after the break claims that group`() {
        val session = session()
        runShot(session, Vec2(1f, 0.015f).normalized(), 1f)

        // Give whoever is at the table a plain, straight pot on the 1.
        layout(
            session,
            1 to Vec2(0.4f, 0.2f),
            12 to Vec2(-0.55f, -0.3f),
            8 to Vec2(0.7f, -0.38f)
        )
        val one = session.physics.ball(1)!!
        val shooter = session.currentSeat
        val direction = lineUp(session, one, "TOP_RIGHT")
        runShotAt(session, direction, POT_SPEED)

        assertEquals(BallGroup.SOLIDS, session.groupOf(shooter))
        assertEquals(BallGroup.STRIPES, session.groupOf(session.opponentOf(shooter)))
        assertFalse(session.tableOpen)
    }

    @Test
    fun `potting the last ball of your group is not a foul`() {
        // The bug this guards: legality was read after the pot, so clearing your group
        // looked like you had failed to hit the 8 first.
        val session = session()
        runShot(session, Vec2(1f, 0.015f).normalized(), 1f)
        layout(
            session,
            3 to Vec2(0.45f, 0.24f),
            8 to Vec2(-0.6f, -0.3f),
            11 to Vec2(0.1f, -0.42f)
        )
        forceGroups(session, BallGroup.SOLIDS)
        val three = session.physics.ball(3)!!
        val direction = lineUp(session, three, "TOP_RIGHT")
        runShotAt(session, direction, POT_SPEED)

        val result = session.lastResult
        assertNotNull(result)
        assertFalse("clearing the group was wrongly called a foul: ${result!!.foulReason}", result.foul)
        assertTrue("the shooter should keep the table", result.keepsTurn)
        assertEquals(0, session.ballsRemaining(BallGroup.SOLIDS))
    }

    @Test
    fun `potting the 8 after clearing your group wins the game`() {
        val session = session()
        runShot(session, Vec2(1f, 0.015f).normalized(), 1f)
        layout(session, 8 to Vec2(0.45f, 0.24f), 11 to Vec2(-0.3f, 0.4f))
        forceGroups(session, BallGroup.SOLIDS)
        val shooter = session.currentSeat
        val eight = session.physics.ball(8)!!
        val direction = lineUp(session, eight, "TOP_RIGHT")
        runShotAt(session, direction, POT_SPEED)

        assertEquals(GamePhase.GAME_OVER, session.phase)
        assertEquals(shooter, session.winner)
    }

    @Test
    fun `potting the 8 with balls still on the table loses the game`() {
        val session = session()
        runShot(session, Vec2(1f, 0.015f).normalized(), 1f)
        layout(session, 8 to Vec2(0.45f, 0.24f), 3 to Vec2(-0.3f, 0.4f))
        forceGroups(session, BallGroup.SOLIDS)
        val shooter = session.currentSeat
        val eight = session.physics.ball(8)!!
        val direction = lineUp(session, eight, "TOP_RIGHT")
        runShotAt(session, direction, POT_SPEED)

        assertEquals(GamePhase.GAME_OVER, session.phase)
        assertEquals(session.opponentOf(shooter), session.winner)
    }

    @Test
    fun `a scratch hands the opponent ball in hand`() {
        val session = session()
        runShot(session, Vec2(1f, 0.015f).normalized(), 1f)
        layout(session, 3 to Vec2(0.2f, 0.2f))
        forceGroups(session, BallGroup.SOLIDS)
        val shooter = session.currentSeat

        // Shoot the cue ball straight into a corner pocket, missing everything.
        val cue = session.physics.cueBall!!
        cue.position = Vec2(0f, -0.2f)
        cue.pocketed = false
        cue.stop()
        val corner = pocket("BOTTOM_RIGHT").center
        runShotAt(session, (corner - cue.position).normalized(), POT_SPEED)

        val result = session.lastResult!!
        assertTrue("potting the cue ball must be a foul", result.foul)
        assertEquals(GamePhase.BALL_IN_HAND, session.phase)
        assertEquals(session.opponentOf(shooter), session.currentSeat)
        assertFalse("the cue ball must come back on the table", cue.pocketed)
    }

    @Test
    fun `ball in hand refuses illegal positions and accepts legal ones`() {
        val session = session()
        runShot(session, Vec2(1f, 0.015f).normalized(), 1f)
        layout(session, 3 to Vec2(0.2f, 0.2f))
        val onTopOfTheThree = Vec2(0.2f, 0.2f)
        assertFalse(session.isValidCueBallPosition(onTopOfTheThree))
        assertFalse(session.isValidCueBallPosition(Vec2(99f, 0f)))
        assertFalse(session.isValidCueBallPosition(pocket("TOP_LEFT").center))

        val rescued = session.nearestValidCueBallPosition(onTopOfTheThree)
        assertTrue("the fallback position must itself be legal",
            session.isValidCueBallPosition(rescued))
    }

    @Test
    fun `legal targets follow the group you own`() {
        val session = session()
        runShot(session, Vec2(1f, 0.015f).normalized(), 1f)
        layout(session, 3 to Vec2(0.2f, 0.2f), 11 to Vec2(-0.2f, -0.2f), 8 to Vec2(0.6f, 0f))
        forceGroups(session, BallGroup.SOLIDS)
        assertEquals(listOf(3), session.legalTargets(session.currentSeat))

        session.physics.ball(3)!!.pocketed = true
        assertEquals(listOf(8), session.legalTargets(session.currentSeat))
    }

    private companion object {
        /** Comfortably enough pace to send a ball the length of a pot and drop it. */
        const val POT_SPEED = 3.4f
    }

    /** Assigns groups directly, so a test can start from a chosen position. */
    private fun forceGroups(session: GameSession, groupForCurrentSeat: BallGroup) {
        session.assignGroupsForTest(session.currentSeat, groupForCurrentSeat)
    }
}
