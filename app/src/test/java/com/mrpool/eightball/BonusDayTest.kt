package com.mrpool.eightball

import com.mrpool.eightball.data.BonusDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which day the daily bonus belongs to.
 *
 * The whole point is that the day turns over at the player's own midnight. Counting whole
 * days from the epoch gives UTC days, which in India start at half past five in the
 * morning — so these are written in India's offset, and in the awkward ones either side.
 */
class BonusDayTest {

    private val india = (5 * 60 + 30) * 60 * 1000          // +05:30
    private val nepal = (5 * 60 + 45) * 60 * 1000          // +05:45
    private val losAngeles = -8 * 60 * 60 * 1000           // -08:00

    /** Milliseconds since the epoch at a given UTC wall clock time. */
    private fun utc(day: Long, hour: Int, minute: Int = 0): Long =
        day * BonusDay.DAY_MILLIS + hour * 3_600_000L + minute * 60_000L

    @Test
    fun `an evening claim and the next morning are different days in India`() {
        // 22:00 and 08:00 the next morning, India time — plainly two different days.
        val tonight = utc(20_000, 16, 30)                   // 22:00 IST
        val tomorrow = utc(20_001, 2, 30)                   // 08:00 IST next day

        assertNotEquals(
            "a bonus claimed at night must not still be claimable at breakfast",
            BonusDay.of(tonight, india),
            BonusDay.of(tomorrow, india)
        )
    }

    @Test
    fun `the whole of one Indian day counts as one day`() {
        // 00:05 IST through to 23:55 IST on the same date.
        val justAfterMidnight = utc(20_000, 18, 35)         // 00:05 IST on day 20001
        val lateThatNight = utc(20_001, 18, 25)             // 23:55 IST, same date

        assertEquals(
            "the bonus must not come back in the middle of the player's own day",
            BonusDay.of(justAfterMidnight, india),
            BonusDay.of(lateThatNight, india)
        )
    }

    @Test
    fun `half past five in the morning is no longer when the day turns over`() {
        // The old arithmetic rolled over at 05:30 IST. Either side of it is one day now.
        val beforeHalfFive = utc(20_000, 23, 0)             // 04:30 IST on day 20001
        val afterHalfFive = utc(20_001, 1, 0)               // 06:30 IST, same date

        assertEquals(
            "05:30 in the morning is the middle of the night, not a new day",
            BonusDay.of(beforeHalfFive, india),
            BonusDay.of(afterHalfFive, india)
        )
    }

    @Test
    fun `a three quarter hour offset works too`() {
        val beforeMidnight = utc(20_000, 18, 10)            // 23:55 Nepal, day 20000
        val afterMidnight = utc(20_000, 18, 20)             // 00:05 Nepal, day 20001
        assertNotEquals(BonusDay.of(beforeMidnight, nepal), BonusDay.of(afterMidnight, nepal))
    }

    @Test
    fun `the negative side of the world rolls over at its own midnight`() {
        val beforeMidnight = utc(20_001, 7, 55)             // 23:55 in Los Angeles
        val afterMidnight = utc(20_001, 8, 5)               // 00:05 the next day
        assertNotEquals(
            BonusDay.of(beforeMidnight, losAngeles),
            BonusDay.of(afterMidnight, losAngeles)
        )
        assertEquals(
            "and the day before must hold together",
            BonusDay.of(utc(20_000, 9, 0), losAngeles),
            BonusDay.of(utc(20_001, 7, 0), losAngeles)
        )
    }

    @Test
    fun `a day is never skipped or repeated as the clock runs forward`() {
        // Walk a fortnight in ten minute steps. The day number may hold or step up by one
        // and nothing else: a jump would be a bonus the player never got, and a step back
        // would be a bonus they could take twice.
        var t = utc(20_000, 0)
        var previous = BonusDay.of(t, india)
        val end = t + 14 * BonusDay.DAY_MILLIS
        while (t <= end) {
            val step = BonusDay.of(t, india) - previous
            assertTrue("the day number moved by $step", step == 0L || step == 1L)
            previous = BonusDay.of(t, india)
            t += 600_000L
        }
        assertEquals("a fortnight should be fourteen days", 14L, previous - BonusDay.of(utc(20_000, 0), india))
    }
}
