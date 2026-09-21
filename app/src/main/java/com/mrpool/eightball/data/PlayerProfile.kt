package com.mrpool.eightball.data

/** Everything the game remembers about the player between sessions. */
data class PlayerProfile(
    val coins: Int = STARTING_COINS,
    val ownedCueIds: Set<Int> = setOf(0),
    val ownedTableIds: Set<Int> = setOf(0),
    val equippedCueId: Int = 0,
    val equippedTableId: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val bestWinStreak: Int = 0,
    val currentWinStreak: Int = 0,
    /** Epoch day of the last claimed daily bonus, -1 when never claimed. */
    val lastBonusDay: Long = -1L,
    val soundEnabled: Boolean = true,
    /** Shown to the other player in an online match. */
    val playerName: String = "Player",
    /**
     * When the subscription runs out, in epoch millis. Zero when there has never been one.
     *
     * The server decides this, from what the payment provider signed; the app only caches
     * the answer so a subscriber can still play on a train.
     */
    val proUntilMillis: Long = 0L,
    /**
     * Whether the subscription was live when this snapshot was taken.
     *
     * Kept beside [proUntilMillis] rather than worked out on demand so that everything
     * below stays a pure function of the profile. [ProfileStore] is what keeps the two
     * honest, and re-publishes the profile when a subscription lapses under the player.
     */
    val pro: Boolean = false
) {
    // ------------------------------------------------------------------- what is owned

    /**
     * The shop is unaffected by the subscription: every cue in it is still earned with
     * coins. Pro only carries the two cues that are not for sale at all.
     *
     * Keeping it this way is the point. A subscriber and a free player who have played the
     * same amount hold the same cues, so nothing across an online table was bought.
     */
    fun owns(cue: CueStick): Boolean =
        cue.isFree || ownedCueIds.contains(cue.id) || (cue.proOnly && pro)

    fun owns(table: PoolTableSkin): Boolean =
        table.isFree || ownedTableIds.contains(table.id) || (table.proOnly && pro)

    /** Pro items are never for sale, whatever the player's balance. */
    fun canBuy(cue: CueStick): Boolean = !cue.proOnly && !owns(cue)

    fun canBuy(table: PoolTableSkin): Boolean = !table.proOnly && !owns(table)

    /**
     * The cue actually in the player's hand.
     *
     * A lapsed subscription can leave a Pro cue equipped that is no longer unlocked. Rather
     * than play a match with a cue the player does not have, fall back to the house cue —
     * silently, because being dropped into a menu on the day a payment fails is a worse
     * way to find out.
     */
    val equippedCue: CueStick
        get() = CueStick.byId(equippedCueId).let { if (owns(it)) it else CueStick.ALL.first() }

    val equippedTable: PoolTableSkin
        get() = PoolTableSkin.byId(equippedTableId)
            .let { if (owns(it)) it else PoolTableSkin.ALL.first() }

    // ----------------------------------------------------------------- what Pro pays

    /** The daily bonus this player gets: five times as much with a subscription. */
    val dailyBonus: Int get() = if (pro) PRO_DAILY_BONUS else DAILY_BONUS

    /** What beating [difficulty] pays: doubled with a subscription. */
    fun prizeFor(difficulty: com.mrpool.eightball.ai.RobotDifficulty): Int =
        difficulty.reward * if (pro) PRO_PRIZE_MULTIPLIER else 1

    /**
     * This profile with the sound flipped.
     *
     * A flip rather than a set, because the caller does not have to be holding a current
     * profile to get it right. Working out the new value from a snapshot means a stale
     * one computes the value it already has, and a store that ignores no-op writes then
     * does nothing at all — which is a mute that cannot be undone.
     */
    fun withSoundToggled(): PlayerProfile = copy(soundEnabled = !soundEnabled)

    /** The name the opponent sees, with the subscriber's star on it. */
    val displayName: String get() = if (pro) "$playerName ★" else playerName

    val matchesPlayed: Int get() = wins + losses

    companion object {
        const val STARTING_COINS = 1500

        /**
         * Worth one win against the medium robot, so showing up each day helps without
         * making the matches themselves pointless.
         */
        const val DAILY_BONUS = 50

        /** What the daily bonus is worth with a subscription. */
        const val PRO_DAILY_BONUS = 250

        const val PRO_PRIZE_MULTIPLIER = 2
    }
}
