package com.mrpool.eightball

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.audio.GameAudio
import com.mrpool.eightball.audio.Sound
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.ProfileStore
import com.mrpool.eightball.data.PurchaseResult
import com.mrpool.eightball.ui.CueShopScreen
import com.mrpool.eightball.ui.GameScreen
import com.mrpool.eightball.ui.HowToPlayScreen
import com.mrpool.eightball.ui.LobbyScreen
import com.mrpool.eightball.ui.MrPoolTheme
import com.mrpool.eightball.ui.RobotSetupScreen
import com.mrpool.eightball.ui.SplashScreen
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
    /** The studio card, shown once when the app opens. */
    data object Splash : Screen
    data object Lobby : Screen
    data object Cues : Screen
    data object Tables : Screen
    data object Wallet : Screen
    data object HowToPlay : Screen
    data object RobotSetup : Screen
    data class Match(val difficulty: RobotDifficulty?, val prize: Int) : Screen
}

/** Top level navigation, coin handling and shop purchases. */
@Composable
private fun MrPoolApp() {
    val context = LocalContext.current
    val store = remember { ProfileStore.get(context) }
    val profile by store.profile.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }

    val audio = remember { GameAudio(context) }
    DisposableEffect(audio) {
        onDispose { audio.release() }
    }
    LaunchedEffect(profile.soundEnabled) {
        audio.enabled = profile.soundEnabled
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** Every navigation tap clicks, so the menus feel connected to the table. */
    fun go(destination: Screen) {
        audio.play(Sound.TAP, 0.55f)
        screen = destination
    }

    fun startRobotMatch(difficulty: RobotDifficulty) {
        // Matches are free to enter; the reward comes from the robot you beat.
        audio.play(Sound.TAP, 0.6f)
        screen = Screen.Match(difficulty, store.prizeFor(difficulty))
    }

    // Back closes the app from the lobby and from the studio card; everywhere else it
    // walks back to the lobby.
    BackHandler(enabled = screen != Screen.Lobby && screen != Screen.Splash) {
        go(Screen.Lobby)
    }

    when (val current = screen) {
        Screen.Splash -> SplashScreen(onFinished = { screen = Screen.Lobby })

        Screen.Lobby -> LobbyScreen(
            profile = profile,
            onPlayRobot = { go(Screen.RobotSetup) },
            onPlayFriend = { go(Screen.Match(null, 0)) },
            onChooseCue = { go(Screen.Cues) },
            onChooseTable = { go(Screen.Tables) },
            onHowToPlay = { go(Screen.HowToPlay) },
            onWallet = { go(Screen.Wallet) },
            onToggleSound = {
                val turningOn = !profile.soundEnabled
                store.setSoundEnabled(turningOn)
                audio.enabled = turningOn
                if (turningOn) audio.play(Sound.TAP, 0.6f)
            }
        )

        Screen.Cues -> CueShopScreen(
            profile = profile,
            onBack = { go(Screen.Lobby) },
            onBuy = { cue ->
                when (val result = store.buyCue(cue)) {
                    is PurchaseResult.Success -> {
                        audio.play(Sound.COINS)
                        toast("${cue.name} unlocked and equipped")
                    }
                    is PurchaseResult.NotEnoughCoins ->
                        toast("You need ${result.missing} more coins")
                    PurchaseResult.AlreadyOwned -> store.equipCue(cue)
                }
            },
            onEquip = { cue ->
                store.equipCue(cue)
                audio.play(Sound.TAP, 0.6f)
                toast("${cue.name} equipped")
            }
        )

        Screen.Tables -> TableShopScreen(
            profile = profile,
            onBack = { go(Screen.Lobby) },
            onBuy = { table ->
                when (val result = store.buyTable(table)) {
                    is PurchaseResult.Success -> {
                        audio.play(Sound.COINS)
                        toast("${table.name} unlocked")
                    }
                    is PurchaseResult.NotEnoughCoins ->
                        toast("You need ${result.missing} more coins")
                    PurchaseResult.AlreadyOwned -> store.equipTable(table)
                }
            },
            onEquip = { table ->
                store.equipTable(table)
                audio.play(Sound.TAP, 0.6f)
                toast("Now playing on ${table.name}")
            }
        )

        Screen.Wallet -> WalletScreen(
            profile = profile,
            bonusAvailable = isBonusAvailable(profile),
            onClaimBonus = {
                val granted = store.claimDailyBonus()
                if (granted != null) {
                    audio.play(Sound.COINS)
                    toast("+$granted coins")
                } else {
                    toast("Already claimed today")
                }
            },
            onBack = { go(Screen.Lobby) }
        )

        Screen.HowToPlay -> HowToPlayScreen(
            coins = profile.coins,
            onBack = { go(Screen.Lobby) }
        )

        Screen.RobotSetup -> RobotSetupScreen(
            profile = profile,
            onBack = { go(Screen.Lobby) },
            onStart = { difficulty -> startRobotMatch(difficulty) }
        )

        is Screen.Match -> GameScreen(
            cue = profile.equippedCue,
            table = profile.equippedTable,
            difficulty = current.difficulty,
            prize = current.prize,
            audio = audio,
            soundEnabled = profile.soundEnabled,
            onToggleSound = {
                val turningOn = !profile.soundEnabled
                store.setSoundEnabled(turningOn)
                audio.enabled = turningOn
            },
            onFinished = { won ->
                if (current.difficulty != null) {
                    val payout = store.settleMatch(won, current.prize)
                    if (won) toast("You won $payout coins")
                }
            },
            // Nothing to pay, so another rack is always on.
            onRematchAllowed = { true },
            onExit = { go(Screen.Lobby) }
        )
    }
}

private fun isBonusAvailable(profile: PlayerProfile): Boolean {
    val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
    return profile.lastBonusDay != today
}
