package com.mrpool.eightball.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import com.mrpool.eightball.ads.AdPolicy
import com.mrpool.eightball.ai.RobotDifficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** The profile picture, which lives in a file rather than in the preferences. */
    val avatars = AvatarStore(context)

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
                matchesFinished = p.matchesFinished + 1,
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
        val today = BonusDay.local(nowMillis)
        if (current.lastBonusDay == today) return null
        update(
            current.copy(
                coins = current.coins + PlayerProfile.DAILY_BONUS,
                lastBonusDay = today
            )
        )
        return PlayerProfile.DAILY_BONUS
    }

    // ------------------------------------------------------------------- rewarded ads

    /**
     * Counts a rewarded ad and pays for it.
     *
     * The cap is checked here rather than trusted from the screen, so a second tap while
     * the first ad is still closing cannot pay twice. Returns the coins paid, or null when
     * the player has already had their allowance today.
     */
    @Synchronized
    fun claimAdReward(nowMillis: Long = System.currentTimeMillis()): Int? {
        val today = BonusDay.local(nowMillis)
        val p = current
        if (!AdPolicy.canWatchReward(p.rewardsWatchedOn(today))) return null
        update(
            p.withRewardWatched(today).let { it.copy(coins = it.coins + AdPolicy.REWARD_COINS) }
        )
        return AdPolicy.REWARD_COINS
    }

    /** How many rewarded ads the player may still watch today. */
    fun adRewardsLeft(nowMillis: Long = System.currentTimeMillis()): Int =
        AdPolicy.rewardsLeft(current.rewardsWatchedOn(BonusDay.local(nowMillis)))

    /** The name the opponent sees online. */
    fun setPlayerName(name: String) {
        val renamed = current.withName(name)
        if (renamed.playerName == current.playerName) return
        update(renamed)
    }

    // -------------------------------------------------------------- profile picture

    /**
     * Copies the picked photo in as the profile picture.
     *
     * On failure it says why and leaves whatever was there before in place — a picture
     * that fails to load should not wipe the one the player already had.
     */
    fun setAvatar(source: Uri): AvatarResult {
        val result = avatars.save(source)
        if (result is AvatarResult.Saved) {
            update(current.copy(avatarStamp = System.currentTimeMillis()))
        }
        return result
    }

    /** Goes back to the plain eight ball. */
    fun clearAvatar() {
        avatars.clear()
        update(current.copy(avatarStamp = System.currentTimeMillis()))
    }

    /** The saved picture, or null when the player has not set one. */
    fun avatar(): Bitmap? = avatars.load()

    /** Turns the game's sound on or off, and remembers the choice. */
    fun setSoundEnabled(enabled: Boolean) {
        if (current.soundEnabled == enabled) return
        update(current.copy(soundEnabled = enabled))
    }

    /**
     * Flips the sound and returns what it now is.
     *
     * The screens use this rather than working out the new value themselves. A tap handler
     * holds whatever profile its last composition captured, and during a match that can
     * be a frame or two behind; asking it for the opposite of a stale value can produce
     * the value already stored, which [setSoundEnabled] then discards as a no-op. The
     * sound stays off, the button still reads muted, and every further tap repeats the
     * same arithmetic — a mute with no way back.
     *
     * Reading and writing here, under the store's own lock, cannot be stale.
     */
    @Synchronized
    fun toggleSound(): Boolean {
        val flipped = current.withSoundToggled()
        update(flipped)
        return flipped.soundEnabled
    }

    /**
     * Coins paid for beating [difficulty]: 25, 50 or 100.
     *
     * The reward comes from the robot, not from the table, so a player is never out of
     * pocket for choosing a nicer table to play on.
     */
    fun prizeFor(difficulty: RobotDifficulty): Int = difficulty.reward


    fun resetProgress() {
        prefs.edit().clear().apply()
        // The picture lives in a file, not in the preferences, so clearing those leaves
        // the player's face on a profile that is otherwise brand new.
        avatars.clear()
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
            .putBoolean(KEY_SOUND, p.soundEnabled)
            .putString(KEY_NAME, p.playerName)
            .putLong(KEY_AVATAR_STAMP, p.avatarStamp)
            .putInt(KEY_MATCHES, p.matchesFinished)
            .putLong(KEY_REWARD_DAY, p.rewardDay)
            .putInt(KEY_REWARDS_TODAY, p.rewardsToday)
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
            lastBonusDay = prefs.getLong(KEY_BONUS_DAY, -1L),
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            playerName = prefs.getString(KEY_NAME, "Player") ?: "Player",
            avatarStamp = prefs.getLong(KEY_AVATAR_STAMP, 0L),
            matchesFinished = prefs.getInt(KEY_MATCHES, 0),
            rewardDay = prefs.getLong(KEY_REWARD_DAY, -1L),
            rewardsToday = prefs.getInt(KEY_REWARDS_TODAY, 0)
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
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_NAME = "player_name"
        private const val KEY_AVATAR_STAMP = "avatar_stamp"
        private const val KEY_MATCHES = "matches_finished"
        private const val KEY_REWARD_DAY = "reward_day"
        private const val KEY_REWARDS_TODAY = "rewards_today"

        @Volatile
        private var instance: ProfileStore? = null

        fun get(context: Context): ProfileStore =
            instance ?: synchronized(this) {
                instance ?: ProfileStore(context).also { instance = it }
            }
    }
}
