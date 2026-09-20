package com.mrpool.eightball.ai

import com.mrpool.eightball.game.BallGroup
import com.mrpool.eightball.game.GamePhase
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.PoolPhysics
import com.mrpool.eightball.game.ShotEvents
import com.mrpool.eightball.game.TableGeometry
import com.mrpool.eightball.game.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** A shot the robot has decided on, ready to hand to [GameSession.shoot]. */
data class PlannedShot(
    val direction: Vec2,
    val power: Float,
    val sideSpin: Float = 0f,
    val topSpin: Float = 0f,
    val intent: String = ""
)

/**
 * The opponent.
 *
 * Beginner picks a shot by eye and sprays it. Medium picks the right ball and rehearses a
 * handful of options. Hard rehearses everything in the real physics engine, weighs where
 * the cue ball finishes, and plays a calculated safety when there is nothing on - which is
 * what makes it feel like a champion rather than a machine that never misses.
 */
class RobotPlayer(
    val difficulty: RobotDifficulty,
    private val random: Random = Random.Default
) {

    // ------------------------------------------------------------------------- shooting

    fun planShot(session: GameSession): PlannedShot {
        if (session.phase == GamePhase.BREAK) return planBreak(session)

        val targets = session.legalTargets()
        val candidates = AimSolver.candidates(session.physics, targets)
            .filter { it.quality > MIN_VIABLE_QUALITY }

        if (candidates.isEmpty()) {
            return if (difficulty.playsSafeties) planSafety(session, targets)
            else planHopefulPoke(session, targets)
        }

        val chosen = if (difficulty.lookAheadCandidates == 0) {
            pickByEye(candidates)
        } else {
            pickByRehearsal(session, candidates)
        }
        return applyHumanError(chosen)
    }

    private fun planBreak(session: GameSession): PlannedShot {
        val cue = session.physics.cueBall ?: return PlannedShot(Vec2(1f, 0f), 0.9f)
        val apex = session.physics.balls
            .filter { !it.pocketed && !it.isCue }
            .minByOrNull { it.position.x }
            ?: return PlannedShot(Vec2(1f, 0f), 0.95f)

        val base = (apex.position - cue.position).normalized()
        val power = when (difficulty) {
            RobotDifficulty.BEGINNER -> 0.75f + random.nextFloat() * 0.25f
            RobotDifficulty.MEDIUM -> 0.88f
            RobotDifficulty.HARD -> 0.94f
        }
        val jitter = when (difficulty) {
            RobotDifficulty.BEGINNER -> gaussian() * 0.045f
            RobotDifficulty.MEDIUM -> gaussian() * 0.012f
            RobotDifficulty.HARD -> gaussian() * 0.004f
        }
        return PlannedShot(
            direction = base.rotated(jitter),
            power = power,
            sideSpin = 0f,
            topSpin = if (difficulty == RobotDifficulty.HARD) -0.12f else 0f,
            intent = "Break"
        )
    }

    /** Beginner shot choice: often the easiest looking ball, sometimes a silly one. */
    private fun pickByEye(candidates: List<ShotCandidate>): PlannedShot {
        val pick = if (random.nextFloat() < difficulty.blunderChance) {
            candidates[random.nextInt(candidates.size)]
        } else {
            candidates.take(3).let { it[random.nextInt(it.size)] }
        }
        // Beginners hit almost everything too hard.
        val power = (pick.suggestedPower * (1.25f + random.nextFloat() * 0.35f)).coerceIn(0.2f, 1f)
        return PlannedShot(pick.direction, power, 0f, 0f, intentOf(pick))
    }

    /** Medium and hard: rehearse the shortlist in the physics engine and take the best outcome. */
    private fun pickByRehearsal(session: GameSession, candidates: List<ShotCandidate>): PlannedShot {
        val shortlist = candidates.take(difficulty.lookAheadCandidates)
        val group = session.groupOf(session.currentSeat)
        var best: PlannedShot? = null
        var bestScore = -Float.MAX_VALUE

        for (candidate in shortlist) {
            for (variant in powerVariants(candidate)) {
                val shot = PlannedShot(
                    direction = candidate.direction,
                    power = variant.first,
                    sideSpin = variant.second,
                    topSpin = variant.third,
                    intent = intentOf(candidate)
                )
                val score = rehearse(session, shot, group)
                if (score > bestScore) {
                    bestScore = score
                    best = shot
                }
            }
        }

        if (best == null || bestScore < POT_FAILED_THRESHOLD) {
            // Nothing the robot rehearsed actually dropped; fall back to a safety if it can.
            if (difficulty.playsSafeties) {
                return planSafety(session, session.legalTargets())
            }
        }

        if (random.nextFloat() < difficulty.blunderChance) {
            val sloppy = candidates[random.nextInt(min(candidates.size, 4))]
            return PlannedShot(sloppy.direction, sloppy.suggestedPower, 0f, 0f, intentOf(sloppy))
        }
        return best ?: PlannedShot(
            candidates.first().direction,
            candidates.first().suggestedPower,
            intent = intentOf(candidates.first())
        )
    }

    /** Speed / spin options the robot tries for a given pot. */
    private fun powerVariants(candidate: ShotCandidate): List<Triple<Float, Float, Float>> {
        val p = candidate.suggestedPower
        if (!difficulty.usesSpin) return listOf(Triple(p, 0f, 0f))
        return when (difficulty) {
            RobotDifficulty.BEGINNER -> listOf(Triple(p, 0f, 0f))
            RobotDifficulty.MEDIUM -> listOf(
                Triple(p, 0f, 0f),
                Triple((p * 1.18f).coerceAtMost(0.95f), 0f, 0.35f)
            )
            RobotDifficulty.HARD -> listOf(
                Triple(p, 0f, 0f),
                Triple((p * 1.15f).coerceAtMost(0.95f), 0f, 0.55f),
                Triple((p * 1.05f).coerceAtMost(0.95f), 0f, -0.6f),
                Triple((p * 1.10f).coerceAtMost(0.95f), 0.4f, 0.2f),
                Triple((p * 1.10f).coerceAtMost(0.95f), -0.4f, 0.2f)
            )
        }
    }

    /**
     * Plays [shot] out on a copy of the table and scores the result: pots are worth a lot,
     * fouls cost more, and the quality of the next shot is worth what the difficulty says
     * position play is worth.
     */
    private fun rehearse(session: GameSession, shot: PlannedShot, group: BallGroup?): Float {
        val sim = session.physics.copy()
        sim.strike(shot.direction, speedOf(shot.power), shot.sideSpin, shot.topSpin)
        val events = ShotEvents()
        sim.simulateUntilRest(SIM_SECONDS, events)

        val potted = events.pocketedExcludingCue()
        val firstContact = events.firstContact
        val onEight = group != null && session.ballsRemaining(group) == 0

        var score = 0f

        val legalContact = when {
            firstContact == null -> false
            group == null -> firstContact != 8
            onEight -> firstContact == 8
            else -> session.physics.balls.firstOrNull { it.number == firstContact }?.group == group
        }
        if (!legalContact) score -= 400f
        if (events.cueBallPocketed) score -= 350f
        if (potted.isEmpty() && !events.cushionAfterContact) score -= 300f

        for (number in potted) {
            val ballGroup = session.physics.balls.firstOrNull { it.number == number }?.group
            score += when {
                number == 8 && onEight && !events.cueBallPocketed && legalContact -> 1000f
                number == 8 -> -1000f
                group == null -> 90f
                ballGroup == group -> 120f
                else -> -55f
            }
        }

        if (difficulty.positionWeight > 0f) {
            val nextTargets = nextLegalTargets(sim, group)
            val next = AimSolver.candidates(sim, nextTargets).firstOrNull()
            val positionScore = (next?.quality ?: 0f) * 110f
            score += positionScore * difficulty.positionWeight
            // Leaving the cue ball frozen on a cushion is a real cost.
            val cue = sim.cueBall
            if (cue != null && !cue.pocketed && nearCushion(cue.position)) {
                score -= 22f * difficulty.positionWeight
            }
        }
        return score
    }

    private fun nextLegalTargets(sim: PoolPhysics, group: BallGroup?): List<Int> {
        val live = sim.balls.filter { !it.pocketed && !it.isCue }
        if (group == null) {
            return live.filter { it.number != 8 }.map { it.number }
                .ifEmpty { live.map { it.number } }
        }
        val own = live.filter { it.group == group }
        return if (own.isNotEmpty()) own.map { it.number }
        else live.filter { it.number == 8 }.map { it.number }
    }

    // ------------------------------------------------------------------------ safeties

    /**
     * No pot available. Find a legal shot that leaves the opponent with nothing: ideally
     * snookered behind one of our balls, at worst long and thin.
     */
    private fun planSafety(session: GameSession, targets: List<Int>): PlannedShot {
        val cue = session.physics.cueBall ?: return planHopefulPoke(session, targets)
        val opponentGroup = session.groupOf(session.opponentOf(session.currentSeat))
        val group = session.groupOf(session.currentSeat)

        var best: PlannedShot? = null
        var bestScore = -Float.MAX_VALUE

        for (number in targets) {
            val ball = session.physics.balls.firstOrNull { it.number == number && !it.pocketed } ?: continue
            // Only worth trying if the cue ball can actually see this one.
            val direct = AimSolver.raycastFirstBall(session.physics, cue.position, (ball.position - cue.position).normalized(), 0)
            if (direct == null || direct.ballNumber != number) continue
            val straight = (ball.position - cue.position).normalized()
            for (offset in SAFETY_OFFSETS) {
                val contactPoint = ball.position + straight.perpendicular() * (offset * TableGeometry.BALL_RADIUS)
                val ghost = contactPoint - straight * TableGeometry.BALL_DIAMETER
                val dir = (ghost - cue.position).normalized()
                if (dir.isNearlyZero()) continue
                for (speed in SAFETY_SPEEDS) {
                    val shot = PlannedShot(dir, powerFor(speed), 0f, -0.25f, "Safety")
                    val score = rehearseSafety(session, shot, group, opponentGroup)
                    if (score > bestScore) {
                        bestScore = score
                        best = shot
                    }
                }
            }
        }
        // A safety that still fouls is no safety at all: go and find a kick instead.
        if (best == null || bestScore < LEGAL_SHOT_THRESHOLD) {
            val escape = planEscape(session, targets)
            if (escape != null) return escape
        }
        return best?.let { applyHumanError(it) } ?: planHopefulPoke(session, targets)
    }

    /**
     * Snookered. Scans every direction for one that reaches a legal ball off one or two
     * cushions, then rehearses the most forgiving of them and plays the best.
     *
     * This is what stops the strong robot from simply fouling whenever it is hooked.
     */
    private fun planEscape(session: GameSession, targets: List<Int>): PlannedShot? {
        val cue = session.physics.cueBall ?: return null
        if (cue.pocketed) return null

        // Sweep the circle and keep every heading that reaches one of our balls.
        val reaching = BooleanArray(SCAN_STEPS)
        var any = false
        for (i in 0 until SCAN_STEPS) {
            val angle = (i.toFloat() / SCAN_STEPS) * TWO_PI
            val result = AimSolver.raycastFirstBall(
                session.physics, cue.position, Vec2.fromAngle(angle), 2
            )
            if (result != null && targets.contains(result.ballNumber)) {
                reaching[i] = true
                any = true
            }
        }
        if (!any) return null

        // Aim down the middle of each usable arc: that is the shot with the most margin.
        val headings = mutableListOf<Float>()
        var index = 0
        while (index < SCAN_STEPS) {
            if (!reaching[index]) {
                index++
                continue
            }
            var end = index
            while (end + 1 < SCAN_STEPS && reaching[end + 1]) end++
            val middle = (index + end) / 2
            headings.add((middle.toFloat() / SCAN_STEPS) * TWO_PI)
            index = end + 1
        }

        val group = session.groupOf(session.currentSeat)
        val opponentGroup = session.groupOf(session.opponentOf(session.currentSeat))
        var best: PlannedShot? = null
        var bestScore = -Float.MAX_VALUE
        for (heading in headings.take(MAX_ESCAPE_HEADINGS)) {
            for (speed in ESCAPE_SPEEDS) {
                val shot = PlannedShot(Vec2.fromAngle(heading), powerFor(speed), 0f, 0f, "Escape")
                val score = rehearseSafety(session, shot, group, opponentGroup)
                if (score > bestScore) {
                    bestScore = score
                    best = shot
                }
            }
        }
        if (best == null || bestScore < LEGAL_SHOT_THRESHOLD) return null
        return applyHumanError(best)
    }

    private fun rehearseSafety(
        session: GameSession,
        shot: PlannedShot,
        group: BallGroup?,
        opponentGroup: BallGroup?
    ): Float {
        val sim = session.physics.copy()
        sim.strike(shot.direction, speedOf(shot.power), shot.sideSpin, shot.topSpin)
        val events = ShotEvents()
        sim.simulateUntilRest(SIM_SECONDS, events)

        val potted = events.pocketedExcludingCue()
        val firstContact = events.firstContact
        var score = 0f

        val legalContact = when {
            firstContact == null -> false
            group == null -> firstContact != 8
            else -> session.physics.balls.firstOrNull { it.number == firstContact }?.group == group ||
                (session.ballsRemaining(group) == 0 && firstContact == 8)
        }
        if (!legalContact) return -1000f
        if (events.cueBallPocketed) return -900f
        if (potted.isEmpty() && !events.cushionAfterContact) return -800f
        if (potted.contains(8)) return -1000f

        // An accidental pot of our own ball is a bonus, not a problem.
        for (number in potted) {
            val ballGroup = session.physics.balls.firstOrNull { it.number == number }?.group
            score += if (ballGroup == group) 140f else -40f
        }

        val opponentTargets = opponentTargetsAfter(sim, opponentGroup)
        val opponentBest = AimSolver.candidates(sim, opponentTargets).firstOrNull()?.quality ?: 0f
        score += (1f - opponentBest) * 150f
        if (opponentBest <= 0.0001f) score += 120f

        val cue = sim.cueBall
        if (cue != null && !cue.pocketed) {
            // Distance from the opponent's easiest ball makes the shot longer for them.
            val nearest = sim.balls
                .filter { !it.pocketed && !it.isCue && opponentTargets.contains(it.number) }
                .minOfOrNull { it.position.distanceTo(cue.position) } ?: 0f
            score += nearest * 22f
        }
        return score
    }

    private fun opponentTargetsAfter(sim: PoolPhysics, opponentGroup: BallGroup?): List<Int> {
        val live = sim.balls.filter { !it.pocketed && !it.isCue }
        if (opponentGroup == null) {
            return live.filter { it.number != 8 }.map { it.number }.ifEmpty { live.map { it.number } }
        }
        val own = live.filter { it.group == opponentGroup }
        return if (own.isNotEmpty()) own.map { it.number }
        else live.filter { it.number == 8 }.map { it.number }
    }

    /** Last resort: touch a legal ball so that at least it is not a foul. */
    private fun planHopefulPoke(session: GameSession, targets: List<Int>): PlannedShot {
        val cue = session.physics.cueBall ?: return PlannedShot(Vec2(1f, 0f), 0.5f)
        val reachable = session.physics.balls
            .filter { !it.pocketed && targets.contains(it.number) }
            .sortedBy { it.position.distanceTo(cue.position) }

        // Prefer a ball the cue ball can see; a blocked one is a foul waiting to happen.
        for (ball in reachable) {
            val dir = (ball.position - cue.position).normalized()
            val seen = AimSolver.raycastFirstBall(session.physics, cue.position, dir, 0)
            if (seen != null && seen.ballNumber == ball.number) {
                val speed = POKE_SPEED_RANGE.start +
                    random.nextFloat() * (POKE_SPEED_RANGE.endInclusive - POKE_SPEED_RANGE.start)
                return applyHumanError(
                    PlannedShot(dir, powerFor(speed), 0f, 0f, "Hit the ${ball.number}")
                )
            }
        }
        // Nothing in sight: a bank off a cushion is the only legal option left.
        planEscape(session, targets)?.let { return it }

        val ball = reachable.firstOrNull() ?: return PlannedShot(Vec2(1f, 0f), 0.5f)
        val dir = (ball.position - cue.position).normalized()
        return applyHumanError(PlannedShot(dir, powerFor(4.6f), 0f, 0f, "Hit the ${ball.number}"))
    }

    // ---------------------------------------------------------------- ball in hand

    /** Where the robot puts the cue ball when it has ball in hand. */
    fun planCueBallPlacement(session: GameSession): Vec2 {
        val targets = session.legalTargets()
        val samples = mutableListOf<Vec2>()

        // Ideal spots: straight back from each target along each pot line.
        for (number in targets) {
            val ball = session.physics.balls.firstOrNull { it.number == number && !it.pocketed } ?: continue
            for (pocket in TableGeometry.pockets) {
                val potLine = (TableGeometry.aimPoint(pocket) - ball.position).normalized()
                if (potLine.isNearlyZero()) continue
                for (distance in PLACEMENT_DISTANCES) {
                    samples.add(TableGeometry.clampInsideCushions(ball.position - potLine * distance))
                }
            }
        }
        // A scattering of random spots keeps the beginner honest and covers odd layouts.
        repeat(if (difficulty == RobotDifficulty.BEGINNER) 12 else 40) {
            samples.add(
                Vec2(
                    (random.nextFloat() * 2f - 1f) * (TableGeometry.HALF_LENGTH - 0.08f),
                    (random.nextFloat() * 2f - 1f) * (TableGeometry.HALF_WIDTH - 0.08f)
                )
            )
        }

        val legal = samples.filter { session.isValidCueBallPosition(it) }
        if (legal.isEmpty()) {
            return session.nearestValidCueBallPosition(Vec2(TableGeometry.HEAD_STRING_X, 0f))
        }

        // Score the candidates on a copy of the table: this runs on a background thread
        // while the renderer is drawing the live one, which must not be moved underneath it.
        val sim = session.physics.copy()
        val simCue = sim.cueBall
        var best = legal.first()
        var bestScore = -Float.MAX_VALUE
        val scored = ArrayList<Pair<Vec2, Float>>(legal.size)

        for (spot in legal) {
            simCue?.position = spot
            val quality = AimSolver.candidates(sim, targets).firstOrNull()?.quality ?: 0f
            scored.add(spot to quality)
            if (quality > bestScore) {
                bestScore = quality
                best = spot
            }
        }

        // A beginner finds a decent spot, not the perfect one.
        if (difficulty == RobotDifficulty.BEGINNER) {
            val ranked = scored.sortedByDescending { it.second }
            val slice = ranked.drop(ranked.size / 3).ifEmpty { ranked }
            return slice[random.nextInt(slice.size)].first
        }
        return best
    }

    // ---------------------------------------------------------------------- helpers

    private fun applyHumanError(shot: PlannedShot): PlannedShot {
        val aimOff = gaussian() * difficulty.aimError
        val powerOff = 1f + gaussian() * difficulty.powerError
        return shot.copy(
            direction = shot.direction.rotated(aimOff),
            power = (shot.power * powerOff).coerceIn(0.08f, 1f)
        )
    }

    private fun intentOf(candidate: ShotCandidate): String {
        val pocketName = candidate.pocket.id.name.lowercase().replace('_', ' ')
        return "Ball ${candidate.targetNumber} into the $pocketName"
    }

    private fun nearCushion(p: Vec2): Boolean =
        abs(p.x) > TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS * 2.4f ||
            abs(p.y) > TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS * 2.4f

    private fun speedOf(power: Float): Float = GameSession.speedForPower(power)

    /** The bar position that produces [speed]. */
    private fun powerFor(speed: Float): Float = GameSession.powerForSpeed(speed)

    /** Box-Muller, clamped so the robot never produces an absurd outlier. */
    private fun gaussian(): Float {
        val u1 = max(random.nextFloat(), 1e-6f)
        val u2 = random.nextFloat()
        val z = kotlin.math.sqrt(-2f * kotlin.math.ln(u1)) *
            cos(2f * Math.PI.toFloat() * u2)
        return z.coerceIn(-2.5f, 2.5f)
    }

    companion object {
        private const val SIM_SECONDS = 14f
        private const val MIN_VIABLE_QUALITY = 0.012f
        private const val POT_FAILED_THRESHOLD = 20f
        private val SAFETY_OFFSETS = floatArrayOf(-0.85f, -0.45f, 0f, 0.45f, 0.85f)

        /**
         * Speeds, not positions on the power bar.
         *
         * The bar's shape is a feel decision that gets tuned; a safety written as "0.14"
         * quietly became a shot that could not reach a cushion the moment it changed. A
         * speed means the same thing whatever the bar looks like.
         */
        private val SAFETY_SPEEDS = floatArrayOf(1.7f, 2.4f, 3.3f, 4.6f)
        private val ESCAPE_SPEEDS = floatArrayOf(3.4f, 4.8f, 6.3f)
        private val POKE_SPEED_RANGE = 3.9f..6.4f

        private val PLACEMENT_DISTANCES = floatArrayOf(0.22f, 0.36f, 0.55f)
        private const val LEGAL_SHOT_THRESHOLD = -200f
        private const val SCAN_STEPS = 240
        private const val MAX_ESCAPE_HEADINGS = 8
        private val TWO_PI = (Math.PI * 2.0).toFloat()
    }
}
