package com.mrpool.eightball.data

import android.content.Context
import android.content.SharedPreferences
import com.mrpool.eightball.ai.RobotDifficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/** Outcome of a shop purchase, so the UI can explain what happened. */
sealed interface PurchaseResult {
    data class Success(val remainingCoins: Int) : PurchaseResult
    data class NotEnoughCoins(val missing: Int) : PurchaseResult
    data object AlreadyOwned : PurchaseResult
}

/**
 * Persists the player's coins and unlocks in [SharedPreferences] and exposes them as a
 * [StateFlow] so every screen sees the same balance the moment it changes.
 */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    val current: PlayerProfile get() = _profile.value

    // ---------------------------------------------------------------------- purchases

    fun buyCue(cue: CueStick): PurchaseResult {
        val p = current
        if (p.owns(cue)) return PurchaseResult.AlreadyOwned
        if (p.coins < cue.price) return PurchaseResult.NotEnoughCoins(cue.price - p.coins)
        update(
            p.copy(
                coins = p.coins - cue.price,
                ownedCueIds = p.ownedCueIds + cue.id,
                equippedCueId = cue.id
            )
        )
        return PurchaseResult.Success(current.coins)
    }

    fun buyTable(table: PoolTableSkin): PurchaseResult {
        val p = current
        if (p.owns(table)) return PurchaseResult.AlreadyOwned
        if (p.coins < table.price) return PurchaseResult.NotEnoughCoins(table.price - p.coins)
        update(
            p.copy(
                coins = p.coins - table.price,
                ownedTableIds = p.ownedTableIds + table.id,
                equippedTableId = table.id
            )
        )
        return PurchaseResult.Success(current.coins)
    }

    fun equipCue(cue: CueStick): Boolean {
        if (!current.owns(cue)) return false
        update(current.copy(equippedCueId = cue.id))
        return true
    }

    fun equipTable(table: PoolTableSkin): Boolean {
        if (!current.owns(table)) return false
        update(current.copy(equippedTableId = table.id))
        return true
    }

    // -------------------------------------------------------------------------- coins

    /** Takes the entry fee for a match. Returns false when the player cannot cover it. */
    fun payStake(amount: Int): Boolean {
        if (amount <= 0) return true
        val p = current
        if (p.coins < amount) return false
        update(p.copy(coins = p.coins - amount))
        return true
    }

    fun addCoins(amount: Int) {
        if (amount <= 0) return
        update(current.copy(coins = current.coins + amount))
    }

    /** Records the end of a match and pays out the prize. Returns the coins won. */
    fun settleMatch(won: Boolean, prize: Int): Int {
        val p = current
        val streak = if (won) p.currentWinStreak + 1 else 0
        val payout = if (won) prize else 0
        update(
            p.copy(
                coins = p.coins + payout,
                wins = if (won) p.wins + 1 else p.wins,
                losses = if (won) p.losses else p.losses + 1,
                currentWinStreak = streak,
                bestWinStreak = maxOf(p.bestWinStreak, streak)
            )
        )
        return payout
    }

    /** Free coins once a day; returns the amount granted, or null when already claimed. */
    fun claimDailyBonus(nowMillis: Long = System.currentTimeMillis()): Int? {
        val today = TimeUnit.MILLISECONDS.toDays(nowMillis)
        if (current.lastBonusDay == today) return null
        update(
            current.copy(
                coins = current.coins + PlayerProfile.DAILY_BONUS,
                lastBonusDay = today
            )
        )
        return PlayerProfile.DAILY_BONUS
    }

    /** Keeps a broke player in the game. */
    fun bailoutIfBroke(minimumStake: Int): Boolean {
        if (current.coins >= minimumStake) return false
        update(current.copy(coins = current.coins + PlayerProfile.BAILOUT_COINS))
        return true
    }

    /** Prize for beating a robot on the given table at the given difficulty. */
    fun prizeFor(table: PoolTableSkin, difficulty: RobotDifficulty): Int =
        (table.basePrize * difficulty.rewardMultiplier).toInt()

    fun resetProgress() {
        prefs.edit().clear().apply()
        _profile.value = PlayerProfile()
    }

    // ------------------------------------------------------------------- persistence

    private fun update(profile: PlayerProfile) {
        _profile.value = profile
        save(profile)
    }

    private fun save(p: PlayerProfile) {
        prefs.edit()
            .putInt(KEY_COINS, p.coins)
            .putString(KEY_CUES, p.ownedCueIds.joinToString(","))
            .putString(KEY_TABLES, p.ownedTableIds.joinToString(","))
            .putInt(KEY_EQUIPPED_CUE, p.equippedCueId)
            .putInt(KEY_EQUIPPED_TABLE, p.equippedTableId)
            .putInt(KEY_WINS, p.wins)
            .putInt(KEY_LOSSES, p.losses)
            .putInt(KEY_BEST_STREAK, p.bestWinStreak)
            .putInt(KEY_STREAK, p.currentWinStreak)
            .putLong(KEY_BONUS_DAY, p.lastBonusDay)
            .apply()
    }

    private fun load(): PlayerProfile {
        if (!prefs.contains(KEY_COINS)) return PlayerProfile()
        return PlayerProfile(
            coins = prefs.getInt(KEY_COINS, PlayerProfile.STARTING_COINS),
            ownedCueIds = prefs.getString(KEY_CUES, "0").toIdSet(),
            ownedTableIds = prefs.getString(KEY_TABLES, "0").toIdSet(),
            equippedCueId = prefs.getInt(KEY_EQUIPPED_CUE, 0),
            equippedTableId = prefs.getInt(KEY_EQUIPPED_TABLE, 0),
            wins = prefs.getInt(KEY_WINS, 0),
            losses = prefs.getInt(KEY_LOSSES, 0),
            bestWinStreak = prefs.getInt(KEY_BEST_STREAK, 0),
            currentWinStreak = prefs.getInt(KEY_STREAK, 0),
            lastBonusDay = prefs.getLong(KEY_BONUS_DAY, -1L)
        )
    }

    private fun String?.toIdSet(): Set<Int> =
        this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()?.plus(0) ?: setOf(0)

    companion object {
        private const val PREFS = "mr_pool_profile"
        private const val KEY_COINS = "coins"
        private const val KEY_CUES = "owned_cues"
        private const val KEY_TABLES = "owned_tables"
        private const val KEY_EQUIPPED_CUE = "equipped_cue"
        private const val KEY_EQUIPPED_TABLE = "equipped_table"
        private const val KEY_WINS = "wins"
        private const val KEY_LOSSES = "losses"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_STREAK = "streak"
        private const val KEY_BONUS_DAY = "bonus_day"

        @Volatile
        private var instance: ProfileStore? = null

        fun get(context: Context): ProfileStore =
            instance ?: synchronized(this) {
                instance ?: ProfileStore(context).also { instance = it }
            }
    }
}
