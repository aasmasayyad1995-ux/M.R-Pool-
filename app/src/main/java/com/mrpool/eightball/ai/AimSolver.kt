package com.mrpool.eightball.ai

import com.mrpool.eightball.game.Ball
import com.mrpool.eightball.game.GameSession
import com.mrpool.eightball.game.Pocket
import com.mrpool.eightball.game.PoolPhysics
import com.mrpool.eightball.game.TableGeometry
import com.mrpool.eightball.game.Vec2
import com.mrpool.eightball.game.distancePointToSegment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A pot the robot (or the player's aim assist) could attempt. */
data class ShotCandidate(
    val targetNumber: Int,
    val pocket: Pocket,
    /** Where the cue ball must be when it touches the object ball. */
    val ghostBall: Vec2,
    val direction: Vec2,
    /** 0 for a straight in shot, up to PI/2 for an impossible cut. */
    val cutAngle: Float,
    val cueDistance: Float,
    val objectDistance: Float,
    /** Smallest gap between an interfering ball and either path; negative means blocked. */
    val clearance: Float,
    val suggestedPower: Float,
    /** Rough 0..1 chance this goes in, before any aiming error. */
    val quality: Float
)

/**
 * Pure geometry: given a table, which pots exist and how hard are they?
 *
 * Used by the robot to pick a shot and by the aim assist line the player sees.
 */
object AimSolver {

    private const val TWO_R = TableGeometry.BALL_DIAMETER
    private val MAX_CUT = (PI / 2.0).toFloat() * 0.95f

    /** Every pot available to [targets] from the current layout, best first. */
    fun candidates(physics: PoolPhysics, targets: List<Int>): List<ShotCandidate> {
        val cue = physics.cueBall ?: return emptyList()
        if (cue.pocketed) return emptyList()
        val result = mutableListOf<ShotCandidate>()
        for (number in targets) {
            val ball = physics.balls.firstOrNull { it.number == number && !it.pocketed } ?: continue
            for (pocket in TableGeometry.pockets) {
                val candidate = evaluate(physics, cue.position, ball, pocket) ?: continue
                result.add(candidate)
            }
        }
        return result.sortedByDescending { it.quality }
    }

    /** Builds a candidate for potting [ball] into [pocket] from [cuePosition], or null if it cannot work. */
    fun evaluate(physics: PoolPhysics, cuePosition: Vec2, ball: Ball, pocket: Pocket): ShotCandidate? {
        val aimPoint = TableGeometry.aimPoint(pocket)
        val toPocket = aimPoint - ball.position
        val objectDistance = toPocket.length()
        if (objectDistance < 1e-4f) return null
        val potLine = toPocket / objectDistance

        val ghost = ball.position - potLine * TWO_R
        val toGhost = ghost - cuePosition
        val cueDistance = toGhost.length()
        if (cueDistance < 1e-4f) return null
        val direction = toGhost / cueDistance

        val cosCut = direction.dot(potLine).coerceIn(-1f, 1f)
        if (cosCut <= 0.02f) return null
        val cutAngle = acos(cosCut)
        if (cutAngle > MAX_CUT) return null

        // The cue ball must not have to travel through the object ball to reach the ghost.
        if ((ball.position - cuePosition).dot(direction) < cueDistance - TableGeometry.BALL_RADIUS * 0.5f &&
            distancePointToSegment(ball.position, cuePosition, ghost) < TableGeometry.BALL_RADIUS
        ) {
            return null
        }

        var clearance = Float.MAX_VALUE
        for (other in physics.balls) {
            if (other.pocketed || other.isCue || other.number == ball.number) continue
            val onCuePath = distancePointToSegment(other.position, cuePosition, ghost) - TWO_R
            val onPotPath = distancePointToSegment(other.position, ball.position, aimPoint) - TWO_R
            clearance = min(clearance, min(onCuePath, onPotPath))
            if (clearance < 0f) return null
        }
        if (clearance == Float.MAX_VALUE) clearance = 0.2f

        // A ghost ball buried in a cushion is not reachable.
        if (!TableGeometry.isInsideCushions(ghost) && ghost.distanceTo(pocket.center) > pocket.radius) {
            val slack = TableGeometry.BALL_RADIUS * 0.35f
            if (abs(ghost.x) > TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS + slack ||
                abs(ghost.y) > TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS + slack
            ) {
                return null
            }
        }

        val power = requiredPower(cueDistance, objectDistance, cutAngle)
        val quality = quality(cutAngle, cueDistance, objectDistance, clearance, pocket)

        return ShotCandidate(
            targetNumber = ball.number,
            pocket = pocket,
            ghostBall = ghost,
            direction = direction,
            cutAngle = cutAngle,
            cueDistance = cueDistance,
            objectDistance = objectDistance,
            clearance = clearance,
            suggestedPower = power,
            quality = quality
        )
    }

    /**
     * How likely the pot is: thin cuts, long distances and tight gaps all hurt, and the
     * error a small aiming mistake produces grows with the distance the object ball runs.
     */
    private fun quality(
        cutAngle: Float,
        cueDistance: Float,
        objectDistance: Float,
        clearance: Float,
        pocket: Pocket
    ): Float {
        val cutFactor = cos(cutAngle).coerceIn(0f, 1f)
        // The angular slack the pocket allows, seen from the object ball.
        val pocketSlack = (pocket.radius - TableGeometry.BALL_RADIUS * 0.6f) /
            max(objectDistance, TableGeometry.BALL_RADIUS)
        val throwSensitivity = 1f / (1f + cueDistance * 0.55f)
        val gapFactor = (clearance / (TableGeometry.BALL_RADIUS * 1.2f)).coerceIn(0.15f, 1f)
        val base = cutFactor * cutFactor * (pocketSlack * 14f).coerceIn(0.05f, 1f)
        return (base * (0.45f + 0.55f * throwSensitivity) * gapFactor).coerceIn(0.001f, 1f)
    }

    /** Speed needed, expressed on the 0..1 power scale the session uses. */
    fun requiredPower(cueDistance: Float, objectDistance: Float, cutAngle: Float): Float {
        val transfer = max(cos(cutAngle), 0.22f)
        val effective = cueDistance + objectDistance / transfer
        // v^2 = 2 * a * s, with a margin so the ball arrives with something left.
        val speed = sqrt(2f * 0.9f * effective) * 1.28f + 0.35f
        val normalised = (speed - GameSession.MIN_SHOT_SPEED) /
            (GameSession.MAX_SHOT_SPEED - GameSession.MIN_SHOT_SPEED)
        return normalised.coerceIn(0.10f, 0.92f)
    }

    /**
     * Where the cue ball ends up if it is not disturbed, used to draw the guide line and
     * for cheap position estimates. Returns the tangent line direction after contact.
     */
    fun tangentAfterContact(direction: Vec2, ghost: Vec2, objectCenter: Vec2): Vec2 {
        val normal = (objectCenter - ghost).normalized()
        val tangent = normal.perpendicular()
        return if (tangent.dot(direction) >= 0f) tangent else -tangent
    }

    /**
     * First thing the cue ball would touch along [direction]: an object ball (returns its
     * number and the ghost position) or a cushion.
     */
    fun firstContactAlong(physics: PoolPhysics, from: Vec2, direction: Vec2): AimHit? {
        val dir = direction.normalized()
        var bestT = Float.MAX_VALUE
        var bestBall: Ball? = null
        for (other in physics.balls) {
            if (other.pocketed || other.isCue) continue
            val rel = other.position - from
            val along = rel.dot(dir)
            if (along <= 0f) continue
            val perp = abs(rel.cross(dir))
            if (perp > TWO_R) continue
            val back = sqrt(max(0f, TWO_R * TWO_R - perp * perp))
            val t = along - back
            if (t in 0f..bestT) {
                bestT = t
                bestBall = other
            }
        }
        val railT = distanceToCushion(from, dir)
        return if (bestBall != null && bestT <= railT) {
            AimHit(from + dir * bestT, bestBall.number, bestBall.position)
        } else if (railT.isFinite()) {
            AimHit(from + dir * railT, null, null)
        } else {
            null
        }
    }

    /** Distance from [from] along [dir] to the first cushion. */
    private fun distanceToCushion(from: Vec2, dir: Vec2): Float {
        val limX = TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS
        val limY = TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS
        var best = Float.MAX_VALUE
        if (dir.x > 1e-5f) best = min(best, (limX - from.x) / dir.x)
        if (dir.x < -1e-5f) best = min(best, (-limX - from.x) / dir.x)
        if (dir.y > 1e-5f) best = min(best, (limY - from.y) / dir.y)
        if (dir.y < -1e-5f) best = min(best, (-limY - from.y) / dir.y)
        return if (best == Float.MAX_VALUE) 0f else max(best, 0f)
    }

    /**
     * Walks a ray from [from] along [direction], bouncing off cushions up to [maxBounces]
     * times, and reports the first ball it reaches.
     *
     * This is the cheap geometric test behind the robot's kick shots: it finds the angles
     * that reach a legal ball when every direct line is blocked, without paying for a full
     * physics rehearsal on all 240 of them.
     */
    fun raycastFirstBall(
        physics: PoolPhysics,
        from: Vec2,
        direction: Vec2,
        maxBounces: Int = 2
    ): RayResult? {
        var origin = from
        var dir = direction.normalized()
        var bounces = 0
        while (bounces <= maxBounces) {
            val hit = firstContactAlong(physics, origin, dir) ?: return null
            val ball = hit.ballNumber
            if (ball != null) return RayResult(ball, bounces)

            val limX = TableGeometry.HALF_LENGTH - TableGeometry.BALL_RADIUS
            val limY = TableGeometry.HALF_WIDTH - TableGeometry.BALL_RADIUS
            var nx = dir.x
            var ny = dir.y
            var reflected = false
            if (abs(abs(hit.point.x) - limX) < 1e-3f) {
                nx = -nx
                reflected = true
            }
            if (abs(abs(hit.point.y) - limY) < 1e-3f) {
                ny = -ny
                reflected = true
            }
            if (!reflected) return null
            dir = Vec2(nx, ny).normalized()
            origin = hit.point + dir * 1e-3f
            bounces++
        }
        return null
    }
}

/** What a bounced ray reached: [ballNumber] after [bounces] cushions. */
data class RayResult(val ballNumber: Int, val bounces: Int)

/** Where an aiming ray lands: [ballNumber] is null when it reaches a cushion first. */
data class AimHit(val point: Vec2, val ballNumber: Int?, val ballCenter: Vec2?)
