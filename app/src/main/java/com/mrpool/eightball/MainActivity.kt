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
import com.mrpool.eightball.data.AvatarResult
import com.mrpool.eightball.data.BonusDay
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.ProfileStore
import com.mrpool.eightball.game.ClothProperties
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.PlayerState
import com.mrpool.eightball.game.Seat
import com.mrpool.eightball.net.MatchConnection
import com.mrpool.eightball.net.MatchRoom
import com.mrpool.eightball.net.Matchmaking
import com.mrpool.eightball.net.ConnectionGate
import com.mrpool.eightball.net.ConnectionNotice
import com.mrpool.eightball.net.Connectivity
import com.mrpool.eightball.net.OnlineConfig
import com.mrpool.eightball.net.OnlineMatch
import com.mrpool.eightball.data.PurchaseResult
import com.mrpool.eightball.ui.CueShopScreen
import com.mrpool.eightball.ui.GameScreen
import com.mrpool.eightball.ui.HowToPlayScreen
import com.mrpool.eightball.ui.LobbyScreen
import com.mrpool.eightball.ui.MatchEndedOfflineDialog
import com.mrpool.eightball.ui.OfflineScreen
import com.mrpool.eightball.ui.OnlineScreen
import com.mrpool.eightball.ui.MrPoolTheme
import com.mrpool.eightball.ui.ProfileScreen
import com.mrpool.eightball.ui.RobotSetupScreen
import com.mrpool.eightball.ui.SplashScreen
import com.mrpool.eightball.ui.TableShopScreen
import com.mrpool.eightball.ui.WalletScreen
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

    // Mr. Pool is an online game: with no connection there is nothing to play, so this
    // watches the phone's network for the whole of the app's life.
    val connectivity = remember { Connectivity(context) }
    DisposableEffect(connectivity) {
        onDispose { connectivity.release() }
    }
    val hasInternet by connectivity.online.collectAsState()

    /** Why the last picture did not save, shown on the profile until the next attempt. */
    var pictureProblem by remember { mutableStateOf<String?>(null) }

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
            when (val result = withContext(Dispatchers.IO) { store.setAvatar(picked) }) {
                is AvatarResult.Saved -> pictureProblem = null
                is AvatarResult.Failed -> pictureProblem = result.reason
            }
        }
    }

    /** Matchmaking results all land here: on success, straight into the match. */
    fun onMatchmaking(update: Matchmaking) {
        matchmaking = update
        if (update is Matchmaking.Ready) {
            // The pairing can land a moment after the player has walked off the
            // matchmaking screen. Dropping them onto a pool table from the middle of the
            // cue shop would be startling, and sitting in the room without them would
            // leave the other player waiting at a table nobody is standing at. Leave.
            if (screen != Screen.Online) {
                update.room.transport.close()
                connection = null
                matchmaking = Matchmaking.Idle
                return
            }
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

    /**
     * Leaves the matchmaking screen properly.
     *
     * Walking away without telling the server leaves the player sitting in its queue, and
     * the queue does not care which screen they are looking at: the next person to press
     * Quick Match is paired with them and waits at a table they never arrive at.
     */
    fun leaveMatchmaking() {
        online()?.cancel()
        matchmaking = Matchmaking.Idle
        go(Screen.Lobby)
    }

    // Back closes the app from the lobby and from the studio card; everywhere else it
    // walks back to the lobby. A live match has its own handler below, because leaving one
    // is not simply a change of screen.
    BackHandler(
        enabled = hasInternet && screen != Screen.Lobby && screen != Screen.Splash &&
            screen !is Screen.OnlineMatchScreen
    ) {
        if (screen == Screen.Online) leaveMatchmaking() else go(Screen.Lobby)
    }

    val inMatch = screen is Screen.Match || screen is Screen.OnlineMatchScreen

    // Leaving a match because the connection went is the same leaving as any other: the
    // opponent has to be told, and the socket has to be let go of.
    fun abandonForOffline() {
        if (screen is Screen.OnlineMatchScreen) {
            runCatching { connection?.close() }
            connection = null
        }
        matchmaking = Matchmaking.Idle
        screen = Screen.Lobby
    }

    val notice = ConnectionGate.noticeFor(online = hasInternet, inMatch = inMatch)

    if (notice == ConnectionNotice.Blocked) {
        // Nothing is playable, so this replaces the screen rather than covering it, and
        // back from here closes the app exactly as back from the lobby does.
        OfflineScreen(onRetry = { connectivity.recheck() })
        return
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
            pictureProblem = pictureProblem,
            onPickPicture = {
                pictureProblem = null
                pickPicture.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemovePicture = {
                pictureProblem = null
                store.clearAvatar()
            },
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
            onBack = { leaveMatchmaking() }
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
            /** The one way out of a match, whichever way the player asks for it. */
            fun leaveMatch() {
                onlineMatch.forfeit()
                onlineMatch.close()
                connection = null
                go(Screen.Lobby)
            }

            // The back gesture used to be a plain change of screen, which left the
            // opponent watching a table that would never move again and the socket open
            // behind it. It takes the same way out as the button now.
            BackHandler { leaveMatch() }

            GameScreen(
                cue = profile.equippedCue,
                table = profile.equippedTable,
                difficulty = null,
                prize = 0,
                // Seat order, so both devices label the scoreboard the same way round.
                playerName = if (room.seat == Seat.ONE) profile.playerName else room.opponentName,
                opponentName = if (room.seat == Seat.ONE) room.opponentName else profile.playerName,
                avatarStamp = profile.avatarStamp,
                audio = audio,
                online = onlineMatch,
                soundEnabled = profile.soundEnabled,
                onToggleSound = { store.toggleSound() },
                onFinished = { },
                onRematchAllowed = { false },
                onExit = { leaveMatch() }
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
            avatarStamp = profile.avatarStamp,
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

    // After the screens, never before them: a sibling composed earlier is drawn *under*
    // the ones that follow, so a dialog put above this point would be hidden behind the
    // very table it is there to talk about.
    if (notice == ConnectionNotice.MatchEnded) {
        // One button, and it says OK. Back does the same thing rather than nothing, so
        // there is no way to sit on a match that cannot go on.
        BackHandler { abandonForOffline() }
        MatchEndedOfflineDialog(onOk = { abandonForOffline() })
    }
}

private fun isBonusAvailable(profile: PlayerProfile): Boolean =
    profile.lastBonusDay != BonusDay.local()
