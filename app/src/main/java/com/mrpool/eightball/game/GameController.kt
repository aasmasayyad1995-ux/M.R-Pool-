package com.mrpool.eightball.game

import com.mrpool.eightball.ai.PlannedShot
import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.ai.RobotPlayer
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.audio.Sound
import com.mrpool.eightball.audio.SoundPlayer
import com.mrpool.eightball.audio.SoundSynth
import com.mrpool.eightball.data.PoolTableSkin
import com.mrpool.eightball.net.OnlineMatch
import com.mrpool.eightball.net.OnlineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Snapshot of the match, in the shape the Compose HUD wants it. */
data class GameUiState(
    val phase: GamePhase = GamePhase.BREAK,
    val statusMessage: String = "",
    val currentPlayerName: String = "",
    val isHumanTurn: Boolean = true,
    val playerOneGroup: BallGroup? = null,
    val playerTwoGroup: BallGroup? = null,
    val playerOneRemaining: Int = 7,
    val playerTwoRemaining: Int = 7,
    val pocketedBalls: List<Int> = emptyList(),
    val winner: Seat? = null,
    val humanWon: Boolean = false,
    val robotThinking: Boolean = false,
    val robotIntent: String = "",
    val ballInHand: Boolean = false,
    val shotInProgress: Boolean = false,
    /** True when this device is waiting on the other player to shoot. */
    val waitingForOpponent: Boolean = false,
    /** Set when the opponent left, or while a desynced table is being repaired. */
    val onlineNotice: String? = null,
    val tableOpen: Boolean = true,
    /** Mirrors of the aim controls, so Compose re-draws the power bar and spin pad. */
    val power: Float = 0.55f,
    val spin: Vec2 = Vec2.ZERO
)

/** How the aim assist should be drawn this frame. */
data class AimPreview(
    val from: Vec2,
    val direction: Vec2,
    val contactPoint: Vec2,
    val ghostBall: Vec2?,
    val objectDirection: Vec2?,
    val cueBallAfter: Vec2?
)

/**
 * Glue between the rules engine, the robot and the screen.
 *
 * The renderer ticks [update] on the GL thread; Compose reads [uiState] and pushes input
 * in. The robot thinks on a background dispatcher and hands back a decision that is applied
 * on the next tick, so the ball list is only ever mutated from one thread.
 */
class GameController(
    val cue: CueStick,
    val table: PoolTableSkin,
    val difficulty: RobotDifficulty?,
    playerName: String,
    opponentName: String,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    /** Null runs the game silently, which is what the head-less tests do. */
    private val audio: SoundPlayer? = null,
    /** Set for an online match; the session then belongs to the network, not to us. */
    private val online: OnlineMatch? = null
) {

    /** True when the second seat is played by a person on the same device. */
    val isFriendMatch: Boolean = difficulty == null

    private val robot: RobotPlayer? = difficulty?.let { RobotPlayer(it, random) }

    private val playerOneName = playerName
    private val playerTwoName = opponentName

    val isOnline: Boolean = online != null

    var session: GameSession = online?.session ?: newSession()
        private set

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /** Aim direction in table space, radians. */
    var aimAngle: Float = 0f
        private set

    /** 0..1 power the player has dialled in. */
    var power: Float = 0.55f
        private set

    /** Side spin (-1..1) and follow / draw (-1..1). */
    var spin: Vec2 = Vec2.ZERO
        private set

    /** Where the cue ball is being dragged to during ball in hand, if anywhere. */
    var ballInHandGhost: Vec2? = null
        private set

    /** How far the cue is drawn back from the ball, in metres, for the renderer. */
    var cuePullback: Float = 0.05f
        private set

    /** True while the cue is mid stroke and should not be redrawn behind the ball. */
    var cueHidden: Boolean = false
        private set

    var onMatchFinished: ((humanWon: Boolean) -> Unit)? = null

    private var pendingShot: PlannedShot? = null
    private var strokeTimer = 0f
    @Volatile
    private var robotDecision: RobotDecision? = null
    @Volatile
    private var robotBusy = false
    private var matchReported = false
    private var robotIntent: String = ""
    private var wasShooting = false

    /**
     * Turns physics impacts into sounds.
     *
     * Attached to the live table only — [PoolPhysics.copy] drops it, so the robot's
     * rehearsals stay silent however many shots it plays out.
     */
    private val soundListener = object : CollisionListener {
        override fun onBallCollision(speed: Float, position: Vec2, cueBallInvolved: Boolean) {
            val sound = if (speed > SOFT_CONTACT_SPEED) Sound.BALL_CLICK else Sound.BALL_KISS
            audio?.play(
                sound,
                SoundSynth.volumeForImpact(speed),
                SoundSynth.rateForImpact(speed)
            )
        }

        override fun onCushionCollision(speed: Float, position: Vec2) {
            audio?.play(
                Sound.CUSHION,
                SoundSynth.volumeForImpact(speed, reference = 3.6f) * 0.8f,
                SoundSynth.rateForImpact(speed, reference = 3.6f)
            )
        }

        override fun onPocketed(ballNumber: Int, speed: Float) {
            audio?.play(Sound.POCKET, 0.85f, 0.94f + random.nextFloat() * 0.12f)
        }

        override fun onCueStrike(speed: Float) {
            audio?.play(
                Sound.CUE_STRIKE,
                (0.35f + SoundSynth.volumeForImpact(speed, reference = 8f) * 0.65f),
                SoundSynth.rateForImpact(speed, reference = 8f)
            )
        }
    }

    init {
        session.physics.collisionListener = soundListener
        aimAtNearestTarget()
        publish()
    }

    private fun newSession(): GameSession = GameSession(
        PlayerState(playerOneName, isRobot = false),
        PlayerState(playerTwoName, isRobot = !isFriendMatch),
        table.cloth,
        random
    )

    /**
     * Starts a fresh rack with the same players.
     *
     * Does nothing online: a new rack there needs both players to agree to one, and one
     * device quietly re-racking would leave the other playing a different game.
     */
    fun rematch() {
        if (isOnline) return
        session = newSession()
        session.physics.collisionListener = soundListener
        pendingShot = null
        robotDecision = null
        robotBusy = false
        matchReported = false
        strokeTimer = 0f
        wasShooting = false
        cueHidden = false
        ballInHandGhost = null
        power = 0.55f
        spin = Vec2.ZERO
        aimAtNearestTarget()
        publish()
    }

    // ---------------------------------------------------------------------- per frame

    fun update(dt: Float) {
        val step = dt.coerceIn(0f, 0.05f)

        applyRobotDecision()

        if (pendingShot != null) {
            advanceStroke(step)
        } else if (online != null) {
            online.update(step)
        } else {
            session.update(step)
        }

        // The table has just come to rest: show the cue again and line up the next shot.
        if (wasShooting && !session.isShooting && pendingShot == null) {
            cueHidden = false
            cuePullback = restingPullback(power)
            if (session.phase != GamePhase.GAME_OVER && !session.isRobotTurn &&
                session.phase != GamePhase.BALL_IN_HAND
            ) {
                aimAtNearestTarget()
            }
        }
        wasShooting = session.isShooting || pendingShot != null

        if (session.phase == GamePhase.GAME_OVER && !matchReported) {
            matchReported = true
            val humanWon = session.winner == Seat.ONE
            audio?.play(if (humanWon) Sound.WIN else Sound.LOSE, 0.9f)
            // update() runs on the GL thread; the listener pays out coins and shows a
            // toast, so it has to be handed back to the main thread.
            scope.launch { onMatchFinished?.invoke(humanWon) }
        }

        maybeStartRobotTurn()
        publish()
    }

    /** Plays the forward stroke, then actually strikes the ball when the tip arrives. */
    private fun advanceStroke(dt: Float) {
        val shot = pendingShot ?: return
        strokeTimer += dt
        val t = (strokeTimer / STROKE_SECONDS).coerceIn(0f, 1f)
        val pullback = restingPullback(shot.power)
        cuePullback = pullback * (1f - t * t)
        if (t >= 1f) {
            pendingShot = null
            strokeTimer = 0f
            cueHidden = true
            // The cue's power bonus is applied here, before the shot goes anywhere: online
            // the two players may hold different cues, and a multiplier applied after this
            // point would be applied on one device and not the other.
            val speed = (shot.power * cue.power).coerceIn(0f, 1f)
            if (online != null) {
                online.submitShot(shot.direction.angle(), speed, shot.sideSpin, shot.topSpin)
            } else {
                session.shoot(shot.direction, speed, shot.sideSpin, shot.topSpin)
            }
        }
    }

    private fun restingPullback(power: Float): Float = 0.05f + power * 0.26f

    // ------------------------------------------------------------------- player input

    fun setAimAngle(radians: Float) {
        if (!canPlayerAct()) return
        aimAngle = normalizeAngle(radians)
        cueHidden = false
        cuePullback = restingPullback(power)
    }

    /** Nudges the aim by [delta] radians, for the fine tune buttons. */
    fun nudgeAim(delta: Float) = setAimAngle(aimAngle + delta)

    /** Points the cue at [target] in table coordinates. */
    fun aimAt(target: Vec2) {
        val cueBall = session.physics.cueBall ?: return
        val dir = target - cueBall.position
        if (dir.isNearlyZero(1e-4f)) return
        setAimAngle(dir.angle())
    }

    fun setPower(value: Float) {
        if (!canPlayerAct()) return
        power = value.coerceIn(0.05f, 1f)
        cuePullback = restingPullback(power)
        publish()
    }

    fun setSpin(side: Float, top: Float) {
        if (!canPlayerAct()) return
        val limit = cue.spin
        var s = side.coerceIn(-1f, 1f)
        var t = top.coerceIn(-1f, 1f)
        val magnitude = Vec2(s, t).length()
        if (magnitude > 1f) {
            s /= magnitude
            t /= magnitude
        }
        spin = Vec2(s * limit, t * limit)
        publish()
    }

    fun clearSpin() {
        spin = Vec2.ZERO
    }

    /** Fires the shot the player has lined up. */
    fun shoot() {
        if (!canPlayerAct()) return
        pendingShot = PlannedShot(Vec2.fromAngle(aimAngle), power, spin.x, spin.y)
        strokeTimer = 0f
        publish()
    }

    /** Drags the cue ball while the player has ball in hand. */
    fun dragCueBall(position: Vec2) {
        if (session.phase != GamePhase.BALL_IN_HAND || session.isRobotTurn) return
        if (online != null && !online.isLocalTurn) return
        ballInHandGhost = session.nearestValidCueBallPosition(position)
    }

    /** Drops the cue ball where it is being dragged. Returns true when it was placed. */
    fun dropCueBall(): Boolean {
        val ghost = ballInHandGhost ?: return false
        val placed = if (online != null) online.submitPlacement(ghost) else session.placeCueBall(ghost)
        if (placed) {
            ballInHandGhost = null
            aimAtNearestTarget()
            publish()
        }
        return placed
    }

    fun canPlayerAct(): Boolean =
        session.canAim && !session.isRobotTurn && pendingShot == null && !session.isShooting &&
            (online == null || online.isLocalTurn)

    /** Points the cue at the easiest legal ball, so the player always starts somewhere sane. */
    fun aimAtNearestTarget() {
        val cueBall = session.physics.cueBall ?: return
        val targets = session.legalTargets()
        val ball = session.physics.balls
            .filter { !it.pocketed && targets.contains(it.number) }
            .minByOrNull { it.position.distanceTo(cueBall.position) } ?: return
        val dir = ball.position - cueBall.position
        if (!dir.isNearlyZero(1e-4f)) {
            aimAngle = dir.angle()
            cuePullback = restingPullback(power)
        }
    }

    // ------------------------------------------------------------------------- robot

    private data class RobotDecision(val placement: Vec2?, val shot: PlannedShot?)

    private fun maybeStartRobotTurn() {
        if (isOnline) return
        val bot = robot ?: return
        if (robotBusy || pendingShot != null) return
        if (session.phase == GamePhase.GAME_OVER || session.isShooting) return
        if (!session.isRobotTurn) return

        robotBusy = true
        val needsPlacement = session.phase == GamePhase.BALL_IN_HAND
        scope.launch(Dispatchers.Default) {
            try {
                if (needsPlacement) {
                    delay(PLACEMENT_THINK_MILLIS)
                    val spot = bot.planCueBallPlacement(session)
                    robotDecision = RobotDecision(spot, null)
                } else {
                    val shot = bot.planShot(session)
                    delay((bot.difficulty.thinkSeconds * 1000).toLong())
                    robotDecision = RobotDecision(null, shot)
                }
            } catch (t: Throwable) {
                // A failed plan must never freeze the table: tap the nearest legal ball.
                robotDecision = RobotDecision(null, PlannedShot(Vec2.fromAngle(aimAngle), 0.4f))
            }
        }
    }

    private fun applyRobotDecision() {
        val decision = robotDecision ?: return
        robotDecision = null
        decision.placement?.let { spot ->
            session.placeCueBall(spot)
            robotBusy = false
            return
        }
        decision.shot?.let { shot ->
            aimAngle = shot.direction.angle()
            power = shot.power
            spin = Vec2(shot.sideSpin, shot.topSpin)
            pendingShot = shot
            strokeTimer = 0f
            robotIntent = shot.intent
        }
        robotBusy = false
    }

    // --------------------------------------------------------------------- aim assist

    /**
     * The guide the player sees: where the cue ball goes, what it hits first, and which way
     * both balls leave the collision.
     */
    fun aimPreview(): AimPreview? {
        if (session.isShooting || pendingShot != null) return null
        val cueBall = session.physics.cueBall ?: return null
        if (cueBall.pocketed) return null
        val from = ballInHandGhost ?: cueBall.position
        val dir = Vec2.fromAngle(aimAngle)
        val hit = com.mrpool.eightball.ai.AimSolver.firstContactAlong(session.physics, from, dir)
            ?: return AimPreview(from, dir, from + dir * 0.5f, null, null, null)

        val contact = hit.point
        val center = hit.ballCenter
        return if (center != null) {
            val objectDir = (center - contact).normalized()
            val tangent = objectDir.perpendicular().let {
                if (it.dot(dir) >= 0f) it else -it
            }
            val cueAfter = contact + tangent * CUE_TANGENT_PREVIEW
            AimPreview(from, dir, contact, contact, objectDir, cueAfter)
        } else {
            AimPreview(from, dir, contact, null, null, null)
        }
    }

    /** Length of the guide line, scaled by the equipped cue's aim rating. */
    fun guideLength(): Float = BASE_GUIDE_LENGTH * cue.aim

    // ------------------------------------------------------------------------ UI state

    private fun publish() {
        val one = session.playerAt(Seat.ONE)
        val two = session.playerAt(Seat.TWO)
        _uiState.value = GameUiState(
            phase = session.phase,
            statusMessage = session.statusMessage,
            currentPlayerName = session.currentPlayer.name,
            isHumanTurn = !session.isRobotTurn && session.phase != GamePhase.GAME_OVER &&
                (online == null || online.isLocalTurn),
            playerOneGroup = one.group,
            playerTwoGroup = two.group,
            playerOneRemaining = one.group?.let { session.ballsRemaining(it) } ?: 7,
            playerTwoRemaining = two.group?.let { session.ballsRemaining(it) } ?: 7,
            pocketedBalls = session.physics.balls.filter { it.pocketed }.map { it.number },
            winner = session.winner,
            humanWon = session.winner == Seat.ONE,
            robotThinking = robotBusy && session.isRobotTurn,
            robotIntent = robotIntent,
            ballInHand = session.phase == GamePhase.BALL_IN_HAND,
            waitingForOpponent = online != null &&
                !online.isLocalTurn &&
                session.phase != GamePhase.GAME_OVER &&
                online.status == OnlineStatus.PLAYING,
            onlineNotice = when (online?.status) {
                OnlineStatus.OPPONENT_GONE -> "Your opponent left the match"
                OnlineStatus.REPAIRING -> "Re-syncing with your opponent…"
                else -> null
            },
            shotInProgress = session.isShooting || pendingShot != null,
            tableOpen = session.tableOpen,
            power = power,
            spin = spin
        )
    }

    /** Leaves an online match, so the opponent is not left waiting on a still table. */
    fun leaveOnlineMatch() {
        online?.forfeit()
        online?.close()
    }

    companion object {
        /** Below this closing speed a contact is a kiss rather than a click. */
        private const val SOFT_CONTACT_SPEED = 1.15f
        private const val STROKE_SECONDS = 0.16f
        private const val PLACEMENT_THINK_MILLIS = 700L
        private const val BASE_GUIDE_LENGTH = 0.55f
        private const val CUE_TANGENT_PREVIEW = 0.28f
    }
}
