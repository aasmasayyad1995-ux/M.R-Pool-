package com.mrpool.eightball

import com.mrpool.eightball.ads.AdPolicy
import com.mrpool.eightball.ads.AdScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `rewarded ads are capped so the matches still mean something`() {
        assertTrue(AdPolicy.canWatchReward(watchedToday = 0))
        assertTrue(AdPolicy.canWatchReward(watchedToday = AdPolicy.REWARDS_PER_DAY - 1))
        assertFalse(AdPolicy.canWatchReward(watchedToday = AdPolicy.REWARDS_PER_DAY))
        assertFalse("and never comes back by going over", AdPolicy.canWatchReward(99))
    }

    @Test
    fun `what a day of watching ads pays stays under what playing pays`() {
        // The whole economy is coins won at the table. If a player can tap through ads for
        // more than they earn by winning, the matches stop mattering — which costs more
        // than the ads bring in.
        val fromAds = AdPolicy.REWARD_COINS * AdPolicy.REWARDS_PER_DAY
        val twoHardWins = 100 * 2
        assertTrue(
            "a day of ads ($fromAds) must not beat a couple of hard wins ($twoHardWins)",
            fromAds <= twoHardWins
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
    fun `the count left never goes negative`() {
        assertEquals(AdPolicy.REWARDS_PER_DAY, AdPolicy.rewardsLeft(0))
        assertEquals(0, AdPolicy.rewardsLeft(AdPolicy.REWARDS_PER_DAY))
        assertEquals(0, AdPolicy.rewardsLeft(AdPolicy.REWARDS_PER_DAY + 7))
    }
}
