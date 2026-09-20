package com.mrpool.eightball

import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.data.PoolTableSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prize for beating each robot, which is the whole of the game's economy. */
class RewardsTest {

    @Test
    fun `beating each robot pays the advertised prize`() {
        assertEquals(25, RobotDifficulty.BEGINNER.reward)
        assertEquals(50, RobotDifficulty.MEDIUM.reward)
        assertEquals(100, RobotDifficulty.HARD.reward)
    }

    @Test
    fun `a tougher robot always pays more`() {
        val rewards = RobotDifficulty.entries.map { it.reward }
        assertEquals(
            "the difficulties are no longer ordered by what they pay",
            rewards.sorted(),
            rewards
        )
        assertTrue("two robots pay the same", rewards.toSet().size == rewards.size)
    }

    @Test
    fun `shop prices still start where they were promised`() {
        assertEquals(20, PoolTableSkin.ALL.size)
        val cheapestPaidTable = PoolTableSkin.ALL.filter { !it.isFree }.minOf { it.price }
        assertEquals(1000, cheapestPaidTable)
        assertTrue("the first table must be free", PoolTableSkin.ALL.first().isFree)
    }

    @Test
    fun `a cue is reachable in a sensible number of wins`() {
        // The cheapest cue is 200 coins: eight beginner wins, or two hard wins.
        val cheapestPaidCue = com.mrpool.eightball.data.CueStick.ALL
            .filter { !it.isFree }
            .minOf { it.price }
        assertEquals(200, cheapestPaidCue)

        val hardWins = cheapestPaidCue / RobotDifficulty.HARD.reward
        assertTrue("a cue should not take more than a handful of hard wins", hardWins <= 4)
    }
}
