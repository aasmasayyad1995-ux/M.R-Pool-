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
    val soundEnabled: Boolean = true
) {
    val equippedCue: CueStick get() = CueStick.byId(equippedCueId)
    val equippedTable: PoolTableSkin get() = PoolTableSkin.byId(equippedTableId)

    fun owns(cue: CueStick): Boolean = cue.isFree || ownedCueIds.contains(cue.id)
    fun owns(table: PoolTableSkin): Boolean = table.isFree || ownedTableIds.contains(table.id)

    val matchesPlayed: Int get() = wins + losses

    companion object {
        const val STARTING_COINS = 1500
        const val DAILY_BONUS = 250
        /** Handed out when the player is broke so a match is always reachable. */
        const val BAILOUT_COINS = 150
    }
}
