package com.mrpool.eightball.ads

/**
 * When an ad is allowed to appear, and what a rewarded one pays.
 *
 * Kept away from Android and away from the ad SDK so the awkward part — how often a player
 * is interrupted — can be decided by tests rather than by whatever felt right on the day.
 *
 * The shape of it is one rule: **an ad never interrupts pool.** A full screen ad in the
 * middle of a frame, or a banner under the table eating a thumb's width of it, would make
 * the game worse to play, and a game people stop playing earns nothing anyway. So the
 * banner lives on the menus, the full screen ad waits until a match is over, and the
 * rewarded one only ever runs because the player asked for it.
 */
object AdPolicy {

    // ------------------------------------------------------------------ interstitial

    /**
     * How many finished matches between full screen ads.
     *
     * One after every match is the most common way to make people uninstall a game. Every
     * third leaves two clean matches in a row, which is enough that the ad reads as the
     * price of a free game rather than a toll gate.
     */
    const val MATCHES_PER_INTERSTITIAL = 3

    /**
     * The first match is never interrupted, whatever the count says.
     *
     * Somebody opening the game for the first time should reach their second match before
     * they meet an advertisement.
     */
    const val FREE_MATCHES_AT_START = 1

    /**
     * True when a full screen ad should run now that [matchesFinished] matches are done.
     *
     * [matchesFinished] counts every finished match on this install, so the decision does
     * not depend on the app having stayed open.
     */
    fun showInterstitial(matchesFinished: Int): Boolean {
        if (matchesFinished <= FREE_MATCHES_AT_START) return false
        return matchesFinished % MATCHES_PER_INTERSTITIAL == 0
    }

    // ---------------------------------------------------------------------- rewarded

    /**
     * Coins for watching one rewarded ad.
     *
     * Small on purpose: well under a beginner win, so the table is by a long way the
     * better place to earn and nothing about the economy leans on advertising.
     */
    const val REWARD_COINS = 10

    /**
     * How many rewarded ads a player may take in a day.
     *
     * Coins are the whole of this game's economy and they are meant to be won at the
     * table. An unlimited tap-for-coins button would make the matches pointless, which
     * costs more than the ads bring in.
     */
    const val REWARDS_PER_DAY = 4

    /** True when the player may watch another rewarded ad today. */
    fun canWatchReward(watchedToday: Int): Boolean = watchedToday < REWARDS_PER_DAY

    /** How many are left today, never below zero. */
    fun rewardsLeft(watchedToday: Int): Int = (REWARDS_PER_DAY - watchedToday).coerceAtLeast(0)

    // -------------------------------------------------------------------- daily bonus

    /**
     * Whether the daily bonus is owed after an attempt to show an ad for it.
     *
     * Two ways to be owed it, and the second is the one worth writing down: a player who
     * pressed the button and was shown nothing has done everything that was asked of
     * them, and taking their bonus away because an advert did not turn up would be
     * punishing them for our failure. Backing out of an ad that *did* play is different —
     * that is a choice, it pays nothing, and the button is still there to press again.
     */
    fun bonusOwed(adWasShown: Boolean, adWasWatched: Boolean): Boolean =
        adWasWatched || !adWasShown

    // ------------------------------------------------------------------------ banner

    /**
     * Whether the strip along the bottom belongs on this screen.
     *
     * Never on the table and never over the studio card, which is the one moment the game
     * gets to look like itself before anything is sold.
     */
    fun showBanner(screen: AdScreen): Boolean = when (screen) {
        AdScreen.MATCH -> false
        AdScreen.SPLASH -> false
        AdScreen.MENU -> true
    }
}

/** The kinds of screen the banner rule cares about. */
enum class AdScreen { SPLASH, MENU, MATCH }
