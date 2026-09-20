package com.mrpool.eightball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.audio.SoundPlayer
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PoolTableSkin
import com.mrpool.eightball.game.BallGroup
import com.mrpool.eightball.game.GameController
import com.mrpool.eightball.game.GamePhase
import com.mrpool.eightball.game.GameUiState
import com.mrpool.eightball.net.OnlineMatch
import com.mrpool.eightball.render.PoolRenderer
import com.mrpool.eightball.render.PoolSurfaceView
import com.mrpool.eightball.render.SceneStyle
import kotlin.math.sqrt

/**
 * The match itself: a GL surface with the 3D table, a heads up display over it and the
 * aiming, power and spin controls.
 */
@Composable
fun GameScreen(
    cue: CueStick,
    table: PoolTableSkin,
    difficulty: RobotDifficulty?,
    prize: Int,
    audio: SoundPlayer?,
    online: OnlineMatch? = null,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onFinished: (won: Boolean) -> Unit,
    /** Pays the entry fee for another rack; false means the player cannot afford it. */
    onRematchAllowed: () -> Boolean,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val controller = remember(cue.id, table.id, difficulty, online) {
        GameController(
            cue = cue,
            table = table,
            difficulty = difficulty,
            playerName = "You",
            opponentName = difficulty?.let { "${it.label} Bot" } ?: "Friend",
            scope = scope,
            audio = audio,
            online = online
        )
    }
    val renderer = remember(controller) { PoolRenderer(controller, SceneStyle.from(table, cue)) }
    val state by controller.uiState.collectAsState()

    var spinPadOpen by remember { mutableStateOf(false) }
    var hasPulled by remember { mutableStateOf(false) }
    if (state.pullingBack) hasPulled = true

    DisposableEffect(controller) {
        controller.onMatchFinished = { won ->
            onFinished(won)
        }
        onDispose { controller.onMatchFinished = null }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        TableSurface(renderer = renderer, controller = controller)

        MatchHud(
            state = state,
            table = table,
            difficulty = difficulty,
            onExit = onExit
        )

        // Camera and fine tune controls, bottom left.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundControl("⟲", onClick = { controller.nudgeAim(-0.0035f) })
                RoundControl("⟳", onClick = { controller.nudgeAim(0.0035f) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundControl("▦", onClick = { renderer.snapOverhead() })
                RoundControl("◉", onClick = { renderer.snapBehindCue() })
                RoundControl(
                    label = "✦",
                    background = if (spinPadOpen) Gold.copy(alpha = 0.85f) else Color(0xAA141B1D),
                    contentColor = if (spinPadOpen) Ink else Chalk,
                    onClick = { spinPadOpen = !spinPadOpen }
                )
                RoundControl(
                    label = if (soundEnabled) "🔊" else "🔇",
                    contentColor = if (soundEnabled) Chalk else Chalk.copy(alpha = 0.4f),
                    onClick = onToggleSound
                )
            }
        }

        if (spinPadOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 110.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SpinPad(
                    spin = state.spin,
                    limit = cue.spin,
                    enabled = state.isHumanTurn && !state.shotInProgress,
                    onSpinChange = { side, top -> controller.setSpin(side, top) }
                )
                Text(
                    "Spin",
                    color = Chalk.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Power and shoot, bottom right.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PowerBar(
                power = state.power,
                enabled = state.isHumanTurn && !state.shotInProgress && !state.ballInHand,
                onPowerChange = { controller.setPower(it) }
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.ballInHand && state.isHumanTurn) {
                    Button(
                        onClick = { controller.dropCueBall() },
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Text("Place ball", color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
                ShootButton(
                    enabled = state.isHumanTurn && !state.shotInProgress && !state.ballInHand,
                    onShoot = { controller.shoot() }
                )
            }
        }

        state.onlineNotice?.let { notice ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC2A1416))
                    .border(1.dp, Crimson.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(notice, color = Crimson, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (state.waitingForOpponent) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xAA0D1315))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    "Waiting for ${state.currentPlayerName}…",
                    color = Cyan,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Only until they have done it once — the control is not obvious until it is.
        if (state.isHumanTurn && !state.shotInProgress && !state.ballInHand &&
            !state.pullingBack && !hasPulled
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x990D1315))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    "Pull the cue ball back and let go to shoot",
                    color = Chalk.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (state.robotThinking) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xAA0D1315))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    "${state.currentPlayerName} is thinking…",
                    color = Gold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (state.phase == GamePhase.GAME_OVER) {
            ResultDialog(
                won = state.humanWon,
                allowRematch = online == null,
                isFriendMatch = difficulty == null,
                winnerName = state.winner?.let { controller.session.playerAt(it).name } ?: "",
                prize = prize,
                message = state.statusMessage,
                onRematch = {
                    if (onRematchAllowed()) controller.rematch() else onExit()
                },
                onLobby = onExit
            )
        }
    }
}

/**
 * The GL surface plus every touch gesture: one finger aims (or drags the cue ball when the
 * player has ball in hand), two fingers orbit and pinch the camera.
 */
@Composable
private fun TableSurface(renderer: PoolRenderer, controller: GameController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceView = remember(renderer) { PoolSurfaceView(context, renderer) }

    DisposableEffect(lifecycleOwner, surfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> surfaceView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            surfaceView.onPause()
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(renderer, controller) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    // What the first finger lands on decides what the whole drag does:
                    // on the cue ball it takes hold of the cue, anywhere else it aims.
                    var gesture = startGesture(renderer, controller, first.position)
                    var previousCentroid: Offset? = null
                    var previousSpread = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.isEmpty()) break

                        if (active.size == 1) {
                            previousCentroid = null
                            previousSpread = 0f
                            continueGesture(renderer, controller, gesture, active[0].position)
                        } else {
                            // A second finger means the camera, so any pull is abandoned
                            // rather than fired off by accident.
                            if (gesture == TableGesture.PULL) {
                                controller.cancelPull()
                                gesture = TableGesture.CAMERA
                            }
                            var sumX = 0f
                            var sumY = 0f
                            active.forEach { sumX += it.position.x; sumY += it.position.y }
                            val centroid = Offset(sumX / active.size, sumY / active.size)
                            var spread = 0f
                            active.forEach {
                                val dx = it.position.x - centroid.x
                                val dy = it.position.y - centroid.y
                                spread += sqrt(dx * dx + dy * dy)
                            }
                            spread /= active.size

                            val last = previousCentroid
                            if (last != null) {
                                renderer.orbit(
                                    (centroid.x - last.x) * -0.006f,
                                    (centroid.y - last.y) * 0.004f
                                )
                                if (previousSpread > 1f && spread > 1f) {
                                    renderer.zoom(previousSpread / spread)
                                }
                            }
                            previousCentroid = centroid
                            previousSpread = spread
                        }
                        active.forEach { it.consume() }
                    }

                    // Letting go is what plays the shot.
                    when (gesture) {
                        TableGesture.PULL -> controller.releasePull()
                        TableGesture.PLACE_BALL -> controller.dropCueBall()
                        else -> Unit
                    }
                }
            }
    )
}

/** What a drag on the table is doing. */
private enum class TableGesture { AIM, PULL, PLACE_BALL, CAMERA }

/** Decides what this drag is, from wherever the first finger landed. */
private fun startGesture(
    renderer: PoolRenderer,
    controller: GameController,
    position: Offset
): TableGesture {
    val point = renderer.unproject(position.x, position.y) ?: return TableGesture.AIM
    val state = controller.uiState.value
    if (state.ballInHand && state.isHumanTurn) {
        controller.dragCueBall(point)
        return TableGesture.PLACE_BALL
    }
    if (controller.beginPull(point)) return TableGesture.PULL
    if (controller.canPlayerAct()) controller.aimAt(point)
    return TableGesture.AIM
}

/** Carries on whatever the drag started out as. */
private fun continueGesture(
    renderer: PoolRenderer,
    controller: GameController,
    gesture: TableGesture,
    position: Offset
) {
    val point = renderer.unproject(position.x, position.y) ?: return
    when (gesture) {
        TableGesture.PULL -> controller.updatePull(point)
        TableGesture.PLACE_BALL -> controller.dragCueBall(point)
        TableGesture.AIM -> if (controller.canPlayerAct()) controller.aimAt(point)
        TableGesture.CAMERA -> Unit
    }
}

/** Score line, turn indicator and the balls each player still owes. */
@Composable
private fun MatchHud(
    state: GameUiState,
    table: PoolTableSkin,
    difficulty: RobotDifficulty?,
    onExit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundControl("✕", diameter = 36.dp, onClick = onExit)
            PlayerPlate(
                name = "You",
                group = state.playerOneGroup,
                remaining = state.playerOneRemaining,
                active = state.isHumanTurn,
                modifier = Modifier.padding(start = 10.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    table.name,
                    color = Chalk.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    if (state.tableOpen) "TABLE OPEN" else "",
                    color = Gold.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            PlayerPlate(
                name = difficulty?.let { "${it.label} Bot" } ?: "Friend",
                group = state.playerTwoGroup,
                remaining = state.playerTwoRemaining,
                active = !state.isHumanTurn && state.phase != GamePhase.GAME_OVER,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        if (state.statusMessage.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x991A2326))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    state.statusMessage,
                    color = Chalk,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.ballInHand) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Cyan.copy(alpha = 0.18f))
                    .border(1.dp, Cyan.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    "Ball in hand — drag the cue ball anywhere",
                    color = Cyan,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun PlayerPlate(
    name: String,
    group: BallGroup?,
    remaining: Int,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(112.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Gold.copy(alpha = 0.16f) else Color(0x99121A1C))
            .border(
                1.dp,
                if (active) Gold else Chalk.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            name,
            color = if (active) Gold else Chalk,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1
        )
        Text(
            when (group) {
                BallGroup.SOLIDS -> "Solids · $remaining left"
                BallGroup.STRIPES -> "Stripes · $remaining left"
                else -> "Group open"
            },
            color = Chalk.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ResultDialog(
    won: Boolean,
    allowRematch: Boolean,
    isFriendMatch: Boolean,
    winnerName: String,
    prize: Int,
    message: String,
    onRematch: () -> Unit,
    onLobby: () -> Unit
) {
    DimScrim {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(InkSoft)
                .border(1.dp, if (won) Gold else Crimson, RoundedCornerShape(22.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                when {
                    isFriendMatch -> "$winnerName wins"
                    won -> "YOU WIN"
                    else -> "YOU LOSE"
                },
                color = if (won || isFriendMatch) Gold else Crimson,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                message,
                color = Chalk.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!isFriendMatch) {
                Text(
                    if (won) "+${formatCoins(prize)} coins" else "Entry fee lost",
                    color = if (won) Gold else Chalk.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLobby,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (allowRematch) Color(0xFF2A3134) else Gold
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Lobby",
                        color = if (allowRematch) Chalk else Ink,
                        fontWeight = if (allowRematch) FontWeight.Normal else FontWeight.Bold
                    )
                }
                // A rematch online would need both players to agree to one.
                if (allowRematch) {
                    Button(
                        onClick = onRematch,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Rematch", color = Ink, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
