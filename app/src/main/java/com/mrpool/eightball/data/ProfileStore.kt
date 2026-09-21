package com.mrpool.eightball.data

import android.content.Context
import android.content.SharedPreferences
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.billing.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Outcome of a shop purchase, so the UI can explain what happened. */
sealed interface PurchaseResult {
    data class Success(val remainingCoins: Int) : PurchaseResult
    data class NotEnoughCoins(val missing: Int) : PurchaseResult
    data object AlreadyOwned : PurchaseResult

    /** Comes with the subscription and is not for sale at any price. */
    data object ProOnly : PurchaseResult
}

/**
 * Persists the player's coins and unlocks in [SharedPreferences] and exposes them as a
 * [StateFlow] so every screen sees the same balance the moment it changes.
 */
class ProfileStore(
    context: Context,
    /** Injectable so a lapsing subscription can be tested without waiting a month. */
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    val current: PlayerProfile get() = _profile.value

    // ---------------------------------------------------------------------- purchases

    fun buyCue(cue: CueStick): PurchaseResult {
        val p = current
        if (p.owns(cue)) return PurchaseResult.AlreadyOwned
        // A Pro cue carries no price, which without this would make it free to everyone.
        if (cue.proOnly) return PurchaseResult.ProOnly
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
        if (table.proOnly) return PurchaseResult.ProOnly
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
    fun claimDailyBonus(nowMillis: Long = clock()): Int? {
        val today = TimeUnit.MILLISECONDS.toDays(nowMillis)
        if (current.lastBonusDay == today) return null
        val amount = current.dailyBonus
        update(current.copy(coins = current.coins + amount, lastBonusDay = today))
        return amount
    }

    /**
     * A stable id for this installation, used to tell the two sides of an online match
     * apart. Generated once and kept; it is not tied to the person, only to the install.
     */
    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    /** The name the opponent sees online. */
    fun setPlayerName(name: String) {
        val trimmed = name.trim().take(16).ifBlank { "Player" }
        if (current.playerName == trimmed) return
        update(current.copy(playerName = trimmed))
    }

    /** Turns the game's sound on or off, and remembers the choice. */
    fun setSoundEnabled(enabled: Boolean) {
        if (current.soundEnabled == enabled) return
        update(current.copy(soundEnabled = enabled))
    }

    /**
     * Coins paid for beating [difficulty]: 25, 50 or 100, doubled for a subscriber.
     *
     * The reward comes from the robot, not from the table, so a player is never out of
     * pocket for choosing a nicer table to play on.
     */
    fun prizeFor(difficulty: RobotDifficulty): Int = current.prizeFor(difficulty)

    // ------------------------------------------------------------------ subscription

    /**
     * Caches what the server said about the subscription.
     *
     * The cache is what lets a subscriber play offline, and it expires by itself, so a
     * phone that never reaches the server again loses Pro when the paid time runs out
     * rather than keeping it forever.
     */
    fun applySubscription(subscription: Subscription) {
        val until = subscription.activeUntilMillis
        if (current.proUntilMillis == until) {
            refreshSubscriptionState()
            return
        }
        update(current.copy(proUntilMillis = until))
    }

    /**
     * Re-publishes the profile if the subscription has lapsed since it was last looked at.
     *
     * Nothing else would notice: the expiry is a moment in time, not an event, so without
     * this a player who left the app open would keep Pro until they closed it.
     */
    fun refreshSubscriptionState() {
        val live = clock() < current.proUntilMillis
        if (live != current.pro) update(current.copy(pro = live))
    }

    fun resetProgress() {
        prefs.edit().clear().apply()
        _profile.value = PlayerProfile()
    }

    // ------------------------------------------------------------------- persistence

    private fun update(profile: PlayerProfile) {
        // Worked out here and nowhere else, so every screen reading `pro` sees the same
        // answer and PlayerProfile stays a pure function of its own fields.
        val withPro = profile.copy(pro = clock() < profile.proUntilMillis)
        _profile.value = withPro
        save(withPro)
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
            .putLong(KEY_PRO_UNTIL, p.proUntilMillis)
            .apply()
    }

    private fun load(): PlayerProfile {
        if (!prefs.contains(KEY_COINS)) return PlayerProfile()
        val proUntil = prefs.getLong(KEY_PRO_UNTIL, 0L)
        return PlayerProfile(
            proUntilMillis = proUntil,
            pro = clock() < proUntil,
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
            playerName = prefs.getString(KEY_NAME, "Player") ?: "Player"
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
        private const val KEY_PRO_UNTIL = "pro_until"
        private const val KEY_DEVICE_ID = "device_id"

        @Volatile
        private var instance: ProfileStore? = null

        fun get(context: Context): ProfileStore =
            instance ?: synchronized(this) {
                instance ?: ProfileStore(context).also { instance = it }
            }
    }
}
