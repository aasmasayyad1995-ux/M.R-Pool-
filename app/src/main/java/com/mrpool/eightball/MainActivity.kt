package com.mrpool.eightball

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.WindowCompat
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.audio.GameAudio
import com.mrpool.eightball.audio.Sound
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.ProfileStore
import com.mrpool.eightball.game.ClothProperties
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.PlayerState
import com.mrpool.eightball.game.Seat
import com.mrpool.eightball.net.MatchConnection
import com.mrpool.eightball.net.MatchRoom
import com.mrpool.eightball.net.Matchmaking
import com.mrpool.eightball.net.OnlineConfig
import com.mrpool.eightball.net.OnlineMatch
import com.mrpool.eightball.data.PurchaseResult
import com.mrpool.eightball.ui.CueShopScreen
import com.mrpool.eightball.ui.GameScreen
import com.mrpool.eightball.ui.HowToPlayScreen
import com.mrpool.eightball.ui.LobbyScreen
import com.mrpool.eightball.ui.OnlineScreen
import com.mrpool.eightball.ui.MrPoolTheme
import com.mrpool.eightball.ui.ProfileScreen
import com.mrpool.eightball.ui.RobotSetupScreen
import com.mrpool.eightball.ui.SplashScreen
import com.mrpool.eightball.ui.TableShopScreen
import com.mrpool.eightball.ui.WalletScreen
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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

    /** The player: their picture, their name and their record. */
    data object Profile : Screen
    data object HowToPlay : Screen
    data object RobotSetup : Screen
    data class Match(val difficulty: RobotDifficulty?, val prize: Int) : Screen

    /** The matchmaking screen. */
    data object Online : Screen

    /** A live online match against the player in [room]. */
    data class OnlineMatchScreen(val room: MatchRoom) : Screen
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
    // The one place audio.enabled is written. Every toggle goes through the store, the
    // store publishes, and this follows — so the speaker icon and the actual sound can
    // never disagree about whether the game is muted.
    LaunchedEffect(profile.soundEnabled) {
        audio.enabled = profile.soundEnabled
    }

    var matchmaking by remember { mutableStateOf<Matchmaking>(Matchmaking.Idle) }


    // One socket does the matchmaking and then becomes the match's transport, so there is a
    // single thing to open, to watch and to close.
    var connection by remember { mutableStateOf<MatchConnection?>(null) }
    DisposableEffect(Unit) {
        onDispose { connection?.close() }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // The system photo picker. It hands back a read grant on one picture and asks for no
    // permission at all, so the app never gets to see the rest of the gallery.
    val scope = rememberCoroutineScope()
    val pickPicture = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        // A phone photo can be forty megapixels. Reading and scaling one takes long
        // enough that doing it here, on the thread that draws, would freeze the app.
        scope.launch {
            val saved = withContext(Dispatchers.IO) { store.setAvatar(picked) }
            if (!saved) toast("That picture could not be read")
        }
    }

    /** Matchmaking results all land here: on success, straight into the match. */
    fun onMatchmaking(update: Matchmaking) {
        matchmaking = update
        if (update is Matchmaking.Ready) {
            screen = Screen.OnlineMatchScreen(update.room)
            matchmaking = Matchmaking.Idle
        }
    }

    /** Opens the connection on demand, so a player who never goes online never dials out. */
    fun online(): MatchConnection? {
        if (!OnlineConfig.isConfigured) return null
        val existing = connection
        if (existing != null) return existing
        val fresh = MatchConnection(OnlineConfig.serverUrl, profile.playerName, ::onMatchmaking)
        connection = fresh
        fresh.connect()
        return fresh
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
            onPlayOnline = { go(Screen.Online) },
            onChooseCue = { go(Screen.Cues) },
            onChooseTable = { go(Screen.Tables) },
            onHowToPlay = { go(Screen.HowToPlay) },
            onWallet = { go(Screen.Wallet) },
            onProfile = { go(Screen.Profile) },
            onToggleSound = {
                // The store flips its own value and audio follows profile.soundEnabled,
                // so there is one writer and nothing to get out of step with.
                if (store.toggleSound()) audio.play(Sound.TAP, 0.6f)
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

        Screen.Profile -> ProfileScreen(
            profile = profile,
            onPickPicture = {
                pickPicture.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemovePicture = { store.clearAvatar() },
            onNameChange = { store.setPlayerName(it) },
            onBack = { go(Screen.Lobby) }
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

        Screen.Online -> OnlineScreen(
            coins = profile.coins,
            playerName = profile.playerName,
            state = matchmaking,
            configured = OnlineConfig.isConfigured,
            setupHint = OnlineConfig.SETUP_HINT,
            onQuickMatch = { online()?.quickMatch() },
            onHost = { online()?.createRoom() },
            onJoin = { code -> online()?.joinRoom(code) },
            onCancel = {
                online()?.cancel()
                matchmaking = Matchmaking.Idle
            },
            onNameChange = { store.setPlayerName(it) },
            onBack = { go(Screen.Lobby) }
        )

        is Screen.OnlineMatchScreen -> {
            val room = current.room
            // Both devices rack from the room's seed, so the two tables start identical.
            val onlineMatch = remember(room.code) {
                OnlineMatch(
                    session = GameSession(
                        PlayerState(
                            if (room.seat == Seat.ONE) profile.playerName else room.opponentName,
                            isRobot = false
                        ),
                        PlayerState(
                            if (room.seat == Seat.ONE) room.opponentName else profile.playerName,
                            isRobot = false
                        ),
                        ClothProperties.TOURNAMENT,
                        Random(room.seed)
                    ),
                    localSeat = room.seat,
                    isHost = room.isHost,
                    transport = room.transport
                )
            }
            GameScreen(
                cue = profile.equippedCue,
                table = profile.equippedTable,
                difficulty = null,
                prize = 0,
                // Seat order, so both devices label the scoreboard the same way round.
                playerName = if (room.seat == Seat.ONE) profile.playerName else room.opponentName,
                opponentName = if (room.seat == Seat.ONE) room.opponentName else profile.playerName,
                audio = audio,
                online = onlineMatch,
                soundEnabled = profile.soundEnabled,
                onToggleSound = { store.toggleSound() },
                onFinished = { },
                onRematchAllowed = { false },
                onExit = {
                    onlineMatch.forfeit()
                    onlineMatch.close()
                    connection = null
                    go(Screen.Lobby)
                }
            )
        }

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
            playerName = profile.playerName,
            opponentName = current.difficulty?.let { "${it.label} Bot" } ?: "Friend",
            audio = audio,
            soundEnabled = profile.soundEnabled,
            onToggleSound = { store.toggleSound() },
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
