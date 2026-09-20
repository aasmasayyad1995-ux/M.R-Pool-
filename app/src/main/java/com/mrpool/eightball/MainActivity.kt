package com.mrpool.eightball

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.ProfileStore
import com.mrpool.eightball.data.PurchaseResult
import com.mrpool.eightball.ui.CueShopScreen
import com.mrpool.eightball.ui.GameScreen
import com.mrpool.eightball.ui.HowToPlayScreen
import com.mrpool.eightball.ui.LobbyScreen
import com.mrpool.eightball.ui.MrPoolTheme
import com.mrpool.eightball.ui.RobotSetupScreen
import com.mrpool.eightball.ui.TableShopScreen
import com.mrpool.eightball.ui.WalletScreen
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MrPoolTheme {
                MrPoolApp()
            }
        }
    }
}

/** Where the player currently is. */
private sealed interface Screen {
    data object Lobby : Screen
    data object Cues : Screen
    data object Tables : Screen
    data object Wallet : Screen
    data object HowToPlay : Screen
    data object RobotSetup : Screen
    data class Match(val difficulty: RobotDifficulty?, val stake: Int, val prize: Int) : Screen
}

/** Top level navigation, coin handling and shop purchases. */
@Composable
private fun MrPoolApp() {
    val context = LocalContext.current
    val store = remember { ProfileStore.get(context) }
    val profile by store.profile.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Lobby) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun startRobotMatch(difficulty: RobotDifficulty) {
        val table = profile.equippedTable
        val stake = table.stake
        if (!store.payStake(stake)) {
            store.bailoutIfBroke(stake)
            toast("Not enough coins for this table — try a cheaper one")
            return
        }
        screen = Screen.Match(difficulty, stake, store.prizeFor(table, difficulty))
    }

    BackHandler(enabled = screen != Screen.Lobby) {
        screen = Screen.Lobby
    }

    when (val current = screen) {
        Screen.Lobby -> LobbyScreen(
            profile = profile,
            onPlayRobot = { screen = Screen.RobotSetup },
            onPlayFriend = { screen = Screen.Match(null, 0, 0) },
            onChooseCue = { screen = Screen.Cues },
            onChooseTable = { screen = Screen.Tables },
            onHowToPlay = { screen = Screen.HowToPlay },
            onWallet = { screen = Screen.Wallet }
        )

        Screen.Cues -> CueShopScreen(
            profile = profile,
            onBack = { screen = Screen.Lobby },
            onBuy = { cue ->
                when (val result = store.buyCue(cue)) {
                    is PurchaseResult.Success -> toast("${cue.name} unlocked and equipped")
                    is PurchaseResult.NotEnoughCoins ->
                        toast("You need ${result.missing} more coins")
                    PurchaseResult.AlreadyOwned -> store.equipCue(cue)
                }
            },
            onEquip = { cue ->
                store.equipCue(cue)
                toast("${cue.name} equipped")
            }
        )

        Screen.Tables -> TableShopScreen(
            profile = profile,
            onBack = { screen = Screen.Lobby },
            onBuy = { table ->
                when (val result = store.buyTable(table)) {
                    is PurchaseResult.Success -> toast("${table.name} unlocked")
                    is PurchaseResult.NotEnoughCoins ->
                        toast("You need ${result.missing} more coins")
                    PurchaseResult.AlreadyOwned -> store.equipTable(table)
                }
            },
            onEquip = { table ->
                store.equipTable(table)
                toast("Now playing on ${table.name}")
            }
        )

        Screen.Wallet -> WalletScreen(
            profile = profile,
            bonusAvailable = isBonusAvailable(profile),
            onClaimBonus = {
                val granted = store.claimDailyBonus()
                if (granted != null) toast("+$granted coins") else toast("Already claimed today")
            },
            onBack = { screen = Screen.Lobby }
        )

        Screen.HowToPlay -> HowToPlayScreen(
            coins = profile.coins,
            onBack = { screen = Screen.Lobby }
        )

        Screen.RobotSetup -> RobotSetupScreen(
            profile = profile,
            onBack = { screen = Screen.Lobby },
            onStart = { difficulty -> startRobotMatch(difficulty) }
        )

        is Screen.Match -> GameScreen(
            cue = profile.equippedCue,
            table = profile.equippedTable,
            difficulty = current.difficulty,
            prize = current.prize,
            onFinished = { won ->
                if (current.difficulty != null) {
                    val payout = store.settleMatch(won, current.prize)
                    if (won) toast("You won $payout coins")
                    store.bailoutIfBroke(profile.equippedTable.stake)
                }
            },
            onRematchAllowed = {
                if (current.difficulty == null) {
                    true
                } else {
                    val paid = store.payStake(current.stake)
                    if (!paid) {
                        store.bailoutIfBroke(current.stake)
                        toast("Not enough coins for another rack")
                    }
                    paid
                }
            },
            onExit = { screen = Screen.Lobby }
        )
    }
}

private fun isBonusAvailable(profile: PlayerProfile): Boolean {
    val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
    return profile.lastBonusDay != today
}
