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
    val playerName: String = "Player"
) {
    fun owns(cue: CueStick): Boolean = cue.isFree || ownedCueIds.contains(cue.id)

    fun owns(table: PoolTableSkin): Boolean = table.isFree || ownedTableIds.contains(table.id)

    /**
     * The cue actually in the player's hand.
     *
     * Falls back to the house cue rather than handing out one the player does not own, so
     * a profile that has somehow got out of step cannot put an unearned cue on the table.
     */
    val equippedCue: CueStick
        get() = CueStick.byId(equippedCueId).let { if (owns(it)) it else CueStick.ALL.first() }

    val equippedTable: PoolTableSkin
        get() = PoolTableSkin.byId(equippedTableId)
            .let { if (owns(it)) it else PoolTableSkin.ALL.first() }

    /**
     * This profile with the sound flipped.
     *
     * A flip rather than a set, because the caller does not have to be holding a current
     * profile to get it right. Working out the new value from a snapshot means a stale
     * one computes the value it already has, and a store that ignores no-op writes then
     * does nothing at all — which is a mute that cannot be undone.
     */
    fun withSoundToggled(): PlayerProfile = copy(soundEnabled = !soundEnabled)

    val matchesPlayed: Int get() = wins + losses

    companion object {
        const val STARTING_COINS = 1500

        /**
         * Worth one win against the medium robot, so showing up each day helps without
         * making the matches themselves pointless.
         */
        const val DAILY_BONUS = 50
    }
}
