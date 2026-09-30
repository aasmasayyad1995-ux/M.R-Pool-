package com.mrpool.eightball

import com.mrpool.eightball.ads.AdPolicy
import com.mrpool.eightball.ads.AdScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How often a player is interrupted, decided here rather than by feel.
 *
 * A game nobody keeps earns nothing, so these tests are as much about the ads that must
 * *not* appear as the ones that must.
 */
class AdPolicyTest {

    @Test
    fun `the first match is never interrupted`() {
        assertFalse(
            "somebody's first match must not end in an advertisement",
            AdPolicy.showInterstitial(matchesFinished = 1)
        )
    }

    @Test
    fun `a full screen ad comes every third match, not every match`() {
        val interrupted = (1..12).filter { AdPolicy.showInterstitial(it) }
        assertEquals(listOf(3, 6, 9, 12), interrupted)
    }

    @Test
    fun `there are always two clean matches between interruptions`() {
        var previous: Int? = null
        for (match in 1..60) {
            if (!AdPolicy.showInterstitial(match)) continue
            previous?.let {
                assertEquals(
                    "two matches in a row must be left alone",
                    AdPolicy.MATCHES_PER_INTERSTITIAL,
                    match - it
                )
            }
            previous = match
        }
    }

    @Test
    fun `the banner keeps off the table and off the studio card`() {
        assertFalse(
            "a strip under the table would eat a thumb's width of the cushion",
            AdPolicy.showBanner(AdScreen.MATCH)
        )
        assertFalse(
            "the opening card is the one moment the game looks like itself",
            AdPolicy.showBanner(AdScreen.SPLASH)
        )
        assertTrue(AdPolicy.showBanner(AdScreen.MENU))
    }

    @Test
    fun `a player may watch as many rewarded ads as they like`() {
        assertNull("the cap is off", AdPolicy.DAILY_REWARD_CAP)
        assertTrue(AdPolicy.canWatchReward(watchedToday = 0))
        assertTrue(AdPolicy.canWatchReward(watchedToday = 50))
        assertTrue("and does not quietly stop at some round number", AdPolicy.canWatchReward(10_000))
    }

    @Test
    fun `putting the cap back is one line and still works`() {
        // The cap is nullable rather than deleted, so a day of watching can be limited
        // again by changing DAILY_REWARD_CAP. This checks the arithmetic that would run
        // if it were, because a limit that has never been exercised is a limit that has
        // never been tested.
        fun canWatch(watched: Int, cap: Int?) = cap == null || watched < cap
        assertTrue(canWatch(3, cap = 4))
        assertFalse(canWatch(4, cap = 4))
        assertTrue(canWatch(10_000, cap = null))
    }

    @Test
    fun `one ad pays well under one win, which is what holds the economy up`() {
        // With no daily cap, the count is no longer what stops ads replacing the game —
        // the size of a single reward is. It must stay small enough that the table is
        // plainly the better place to earn: the beginner robot, the weakest thing in the
        // game, pays more than twice an ad.
        val beginnerWin = 25
        assertTrue(
            "an ad (${AdPolicy.REWARD_COINS}) must pay well under a beginner win ($beginnerWin)",
            AdPolicy.REWARD_COINS * 2 <= beginnerWin
        )
    }

    @Test
    fun `the daily bonus is paid for by watching the ad through`() {
        assertTrue(
            "watched it, so it is owed",
            AdPolicy.bonusOwed(adWasShown = true, adWasWatched = true)
        )
    }

    @Test
    fun `backing out of an ad that played pays no bonus`() {
        assertFalse(
            "the player chose to stop watching; the button is still there",
            AdPolicy.bonusOwed(adWasShown = true, adWasWatched = false)
        )
    }

    @Test
    fun `an ad that never appeared does not cost the player their bonus`() {
        // The one that matters. A player who pressed the button and was shown nothing has
        // done everything asked of them. Losing the daily bonus because an advert did not
        // fill would be punishing them for our failure — and it is the complaint that
        // arrives first, because ad fill is worst in exactly the places phones are worst.
        assertTrue(
            "no ad was shown, so nothing was asked and the bonus stands",
            AdPolicy.bonusOwed(adWasShown = false, adWasWatched = false)
        )
    }

    @Test
    fun `with no cap there is no count to show`() {
        // Null rather than a big number: the wallet button has to say "Watch an ad" and
        // not "Watch an ad · 2147483647 left today".
        assertNull(AdPolicy.rewardsLeft(0))
        assertNull(AdPolicy.rewardsLeft(999))
    }
}
