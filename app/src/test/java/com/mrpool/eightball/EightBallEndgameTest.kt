package com.mrpool.eightball

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

/**
 * The ways a game of 8 ball ends, and the ones people argue about in a pub.
 *
 * Every case here decides a whole game on one shot, so getting any of them backwards
 * hands somebody a win they did not have. None of them were covered.
 */
class EightBallEndgameTest {

    private fun session() = GameSession(
        PlayerState("One", isRobot = false),
        PlayerState("Two", isRobot = false),
        ClothProperties.TOURNAMENT,
        Random(42)
    )

    /** Clears the table down to the listed balls. */
    private fun layout(session: GameSession, vararg keep: Pair<Int, Vec2>) {
        session.physics.balls.forEach { if (it.number != 0) { it.pocketed = true; it.stop() } }
        keep.forEach { (number, position) ->
            val ball = requireNotNull(session.physics.ball(number))
            ball.pocketed = false
            ball.position = position
            ball.stop()
        }
    }

    /**
     * A spot in the jaws of the bottom right pocket.
     *
     * A ball left here is past the cushion line and comes to rest there, so the table
     * settles it into the pocket at the end of the shot. It is a reliable way to drop one
     * particular ball on one particular shot without having to aim at it.
     */
    private val inTheJaws = Vec2(1.05f, -0.60f)

    private fun runShot(
        session: GameSession,
        direction: Vec2,
        speed: Float,
        topSpin: Float = 0f
    ) {
        if (session.phase == GamePhase.BALL_IN_HAND) {
            val here = session.physics.cueBall!!.position
            assertTrue(session.placeCueBall(session.nearestValidCueBallPosition(here)))
        }
        session.shoot(direction, GameSession.powerForSpeed(speed), 0f, topSpin)
        var guard = 0
        while (session.isShooting && guard < 20000) {
            session.update(1f / 120f)
            guard++
        }
        assertFalse("the shot never came to rest", session.isShooting)
    }

    private fun pocket(name: String) = TableGeometry.pockets.first { it.id.name == name }

    /** Puts the cue ball behind [target] on the line into [pocketName], and returns that line. */
    private fun lineUp(session: GameSession, target: Int, pocketName: String, gap: Float = 0.4f): Vec2 {
        val ball = requireNotNull(session.physics.ball(target))
        val aim = TableGeometry.aimPoint(pocket(pocketName))
        val line = (aim - ball.position).normalized()
        val cue = session.physics.cueBall!!
        cue.position = ball.position - line * gap
        cue.pocketed = false
        cue.stop()
        return line
    }

    @Test
    fun `the last ball of your group and the 8 on the same shot is a loss`() {
        // The one people get wrong at the table. You were not on the 8 when you took the
        // shot, so potting it is early, however tidy it looked.
        val s = session()
        s.assignGroupsForTest(Seat.ONE, BallGroup.SOLIDS)
        layout(s, 3 to Vec2(0.2f, 0.30f), 8 to inTheJaws)
        val line = lineUp(s, 3, "TOP_LEFT")

        runShot(s, line, 2.2f)

        assertTrue("ball 3 should have gone down", s.physics.ball(3)!!.pocketed)
        assertTrue("the 8 should have gone down", s.physics.ball(8)!!.pocketed)
        assertEquals("the 8 went down early, so the shooter loses", Seat.TWO, s.winner)
    }

    @Test
    fun `potting the 8 while the table is still open is a loss`() {
        val s = session()
        s.restore(s.snapshot().copy(isBreakShot = false))
        layout(s, 3 to Vec2(0.2f, 0.30f), 11 to Vec2(-0.3f, -0.2f), 8 to inTheJaws)
        val line = lineUp(s, 3, "TOP_LEFT")

        runShot(s, line, 2.2f)

        assertTrue("nobody owns a group yet", s.playerAt(Seat.ONE).group == null)
        assertEquals("the 8 is never yours while the table is open", Seat.TWO, s.winner)
    }

    @Test
    fun `the 8 on the break is re-spotted and the game goes on`() {
        val s = session()   // a fresh session is on the break
        layout(s, 3 to Vec2(0.2f, 0.30f), 11 to Vec2(0.35f, -0.1f), 8 to inTheJaws)
        val line = lineUp(s, 3, "TOP_LEFT")

        runShot(s, line, 2.2f)

        assertNull("the break is not lost on the 8", s.winner)
        assertFalse("the 8 is back on the table", s.physics.ball(8)!!.pocketed)
        assertTrue("and somewhere legal", TableGeometry.isInsideCushions(s.physics.ball(8)!!.position))
    }

    @Test
    fun `hitting the 8 first while you still have balls is a foul`() {
        val s = session()
        s.assignGroupsForTest(Seat.ONE, BallGroup.SOLIDS)
        layout(s, 3 to Vec2(-0.8f, 0.4f), 8 to Vec2(0.3f, 0f))
        val cue = s.physics.cueBall!!
        cue.position = Vec2(-0.2f, 0f)
        cue.pocketed = false
        cue.stop()

        runShot(s, Vec2(1f, 0f), 1.5f)

        val result = requireNotNull(s.lastResult)
        assertTrue("hitting the 8 first should foul", result.foul)
        assertTrue("and say so: ${result.foulReason}", result.foulReason!!.contains("8"))
        assertEquals("the opponent gets ball in hand", GamePhase.BALL_IN_HAND, s.phase)
    }

    @Test
    fun `hitting the 8 first once your group is gone is perfectly legal`() {
        val s = session()
        s.assignGroupsForTest(Seat.ONE, BallGroup.SOLIDS)
        layout(s, 11 to Vec2(-0.8f, 0.4f), 8 to Vec2(0.3f, 0f))
        val cue = s.physics.cueBall!!
        cue.position = Vec2(-0.2f, 0f)
        cue.pocketed = false
        cue.stop()

        runShot(s, Vec2(1f, 0f), 3.5f)

        assertNotNull(s.lastResult)
        assertFalse("nothing wrong with this shot: ${s.lastResult!!.foulReason}", s.lastResult!!.foul)
    }

    @Test
    fun `potting the 8 and scratching on the same shot is a loss`() {
        // Standard everywhere: the 8 going down does not save a scratch. The shooter here
        // has cleared their group, so without the scratch this would be the win.
        val s = session()
        s.assignGroupsForTest(Seat.ONE, BallGroup.SOLIDS)
        // The 8 waits in the jaws, so it drops when the table settles. The cue ball is
        // sent into the far pocket, which is the scratch. Aiming one ball into one pocket
        // is the only part of this that has to work, so the test cannot go flaky on a
        // follow shot that does not quite follow.
        layout(s, 8 to inTheJaws)
        val cue = s.physics.cueBall!!
        cue.position = Vec2(0.2f, 0.1f)
        cue.pocketed = false
        cue.stop()
        val intoTheCorner = (TableGeometry.aimPoint(pocket("TOP_LEFT")) - cue.position).normalized()

        runShot(s, intoTheCorner, 2.5f)

        assertTrue("the 8 should have gone down", s.physics.ball(8)!!.pocketed)
        assertTrue("and the cue ball with it", s.physics.cueBall!!.pocketed)
        assertEquals("a scratch on the 8 loses the game", Seat.TWO, s.winner)
    }
}
