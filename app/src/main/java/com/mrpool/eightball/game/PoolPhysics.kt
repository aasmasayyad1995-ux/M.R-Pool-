package com.mrpool.eightball.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The billiard simulation.
 *
 * Balls roll on a plane. Linear velocity and a full 3D angular velocity are integrated so
 * follow, draw and english all behave; the vertical spin axis ([Ball.wz]) survives cushion
 * and ball collisions, which is what makes position play possible for the robot.
 *
 * The same class powers on screen play and the head-less look ahead the hard robot uses,
 * so a shot the robot rehearses is the shot the player sees.
 */
class PoolPhysics(
    val balls: MutableList<Ball>,
    var cloth: ClothProperties = ClothProperties.TOURNAMENT
) {

    /** Set to false for the robot's look ahead: orientation is only needed for rendering. */
    var trackOrientation: Boolean = true

    /**
     * Notified of every impact as a shot plays out. Only the live table has one: [copy]
     * deliberately leaves it null so the robot's rehearsals make no noise.
     */
    var collisionListener: CollisionListener? = null

    private val radius = TableGeometry.BALL_RADIUS

    /** Left over frame time, so the fixed step never depends on the display refresh rate. */
    private var accumulator = 0f

    fun ball(number: Int): Ball? = balls.firstOrNull { it.number == number }

    val cueBall: Ball? get() = ball(0)

    fun anyBallMoving(): Boolean = balls.any { it.isMoving }

    /**
     * Deep copy, so a candidate shot can be rehearsed without touching the live table.
     *
     * The clone gets no [collisionListener]: a rehearsal that played sounds would fire
     * thousands of them before the player had even taken the shot.
     */
    fun copy(): PoolPhysics {
        val clone = PoolPhysics(balls.map { it.copyState() }.toMutableList(), cloth)
        clone.trackOrientation = false
        return clone
    }

    /**
     * Strikes the cue ball.
     *
     * @param direction unit vector the cue points along
     * @param speed muzzle speed in m/s (roughly 0.8 for a soft tap, 9 for a full break)
     * @param sideSpin english, -1 (left) .. 1 (right)
     * @param topSpin follow / draw, -1 (full draw) .. 1 (full follow)
     */
    fun strike(direction: Vec2, speed: Float, sideSpin: Float, topSpin: Float) {
        val cue = cueBall ?: return
        val dir = direction.normalized()
        cue.velocity = dir * speed
        // Follow / draw lives on the axis perpendicular to the shot line.
        val rollAxis = dir.perpendicular()
        val spinRate = topSpin * speed / radius * 0.72f
        cue.wx = rollAxis.x * spinRate
        cue.wy = rollAxis.y * spinRate
        cue.wz = -sideSpin * speed / radius * 0.55f
        collisionListener?.onCueStrike(speed)
    }

    /**
     * Runs the table until every ball has stopped.
     *
     * @return the events the rules engine needs, or the partial events if [maxSeconds] ran out.
     */
    fun simulateUntilRest(maxSeconds: Float = 25f, events: ShotEvents = ShotEvents()): ShotEvents {
        var elapsed = 0f
        val dt = FIXED_STEP
        while (elapsed < maxSeconds && anyBallMoving()) {
            step(dt, events)
            elapsed += dt
        }
        balls.forEach { if (!it.isMoving) it.stop() }
        return events
    }

    /**
     * Advances the table by a frame of [dt] seconds using a fixed internal step.
     *
     * The step size must not depend on the frame rate: the robot rehearses its shots with
     * [simulateUntilRest], which steps at [FIXED_STEP], and a table that stepped at the
     * display's frame time instead would drift away from that rehearsal and hit a different
     * ball. Leftover time is carried over to the next frame.
     */
    fun advance(dt: Float, events: ShotEvents? = null) {
        accumulator += dt.coerceIn(0f, MAX_FRAME_SECONDS)
        var steps = 0
        while (accumulator >= FIXED_STEP && steps < MAX_STEPS_PER_FRAME) {
            step(FIXED_STEP, events)
            accumulator -= FIXED_STEP
            steps++
        }
        if (steps == MAX_STEPS_PER_FRAME) accumulator = 0f
    }

    /** Drops any partial step, so a new shot always starts from a clean clock. */
    fun resetClock() {
        accumulator = 0f
    }

    /**
     * Advances the table by [dt] seconds, splitting it into sub steps small enough that a
     * fast ball can never tunnel through another ball or a cushion.
     */
    fun step(dt: Float, events: ShotEvents? = null) {
        var remaining = dt
        var guard = 0
        while (remaining > 1e-6f && guard < MAX_SUBSTEPS) {
            guard++
            val fastest = balls.filter { !it.pocketed }.maxOfOrNull { it.velocity.length() } ?: 0f
            val safe = if (fastest > 1e-4f) (radius * 0.45f) / fastest else remaining
            val h = min(remaining, max(MIN_STEP, safe))
            substep(h, events)
            remaining -= h
        }
    }

    private fun substep(h: Float, events: ShotEvents?) {
        for (b in balls) {
            if (b.pocketed) continue
            applyClothFriction(b, h)
            b.position = b.position + b.velocity * h
            if (trackOrientation) integrateOrientation(b, h)
        }
        resolveBallCollisions(events)
        for (b in balls) {
            if (b.pocketed) continue
            resolveCushions(b, events)
        }
        checkPockets(events)
    }

    // ------------------------------------------------------------------ friction

    private fun applyClothFriction(b: Ball, h: Float) {
        // Velocity of the contact patch relative to the cloth: v + R * (z x w).
        val contactX = b.velocity.x + radius * -b.wy
        val contactY = b.velocity.y + radius * b.wx
        val slip = sqrt(contactX * contactX + contactY * contactY)

        if (slip > SLIP_EPSILON) {
            // Sliding: friction opposes the contact patch and spins the ball towards rolling.
            // Sliding friction removes slip at 3.5x its own rate once the induced spin is
            // taken into account, so the step is clipped at the moment the ball starts to
            // roll. Without that clip a slow ball overshoots every frame and never settles.
            val slipDecay = 3.5f * cloth.slidingFriction
            val slideTime = min(h, slip / slipDecay)
            val ux = contactX / slip
            val uy = contactY / slip
            val dv = cloth.slidingFriction * slideTime
            b.velocity = Vec2(b.velocity.x - ux * dv, b.velocity.y - uy * dv)
            val dw = (5f * cloth.slidingFriction * slideTime) / (2f * radius)
            // z x u = (-uy, ux, 0)
            b.wx -= uy * dw
            b.wy += ux * dw
            val leftOver = h - slideTime
            if (leftOver > 0f) applyRolling(b, leftOver)
        } else {
            applyRolling(b, h)
        }

        // English bleeds away on its own.
        b.wz *= exp(-cloth.spinDecay * h)
        if (abs(b.wz) < 0.05f) b.wz = 0f
        if (b.velocity.lengthSq() < Ball.STOP_SPEED_SQ &&
            (b.wx * b.wx + b.wy * b.wy) < Ball.STOP_SPIN_SQ
        ) {
            b.velocity = Vec2.ZERO
            b.wx = 0f
            b.wy = 0f
        }
    }

    /** Natural roll: only rolling resistance, with the spin locked to the velocity. */
    private fun applyRolling(b: Ball, h: Float) {
        val speed = b.velocity.length()
        if (speed > 1e-5f) {
            val dv = min(speed, cloth.rollingFriction * h)
            b.velocity = b.velocity - b.velocity.normalized() * dv
        } else {
            b.velocity = Vec2.ZERO
        }
        b.wx = -b.velocity.y / radius
        b.wy = b.velocity.x / radius
    }

    // --------------------------------------------------------------- collisions

    private fun resolveBallCollisions(events: ShotEvents?) {
        val n = balls.size
        for (i in 0 until n) {
            val a = balls[i]
            if (a.pocketed) continue
            for (j in i + 1 until n) {
                val b = balls[j]
                if (b.pocketed) continue
                val delta = b.position - a.position
                val distSq = delta.lengthSq()
                val minDist = radius * 2f
                if (distSq >= minDist * minDist || distSq < 1e-12f) continue

                val dist = sqrt(distSq)
                val normal = delta / dist

                // Separate the overlap so balls never stick together.
                val overlap = minDist - dist
                a.position = a.position - normal * (overlap * 0.5f)
                b.position = b.position + normal * (overlap * 0.5f)

                val relative = a.velocity - b.velocity
                val approach = relative.dot(normal)
                if (approach <= 0f) continue

                if (events != null && (a.isCue || b.isCue)) {
                    val other = if (a.isCue) b else a
                    if (events.firstContact == null) events.firstContact = other.number
                    events.contactHappened = true
                }

                collisionListener?.onBallCollision(
                    approach,
                    a.position + normal * radius,
                    a.isCue || b.isCue
                )

                // Equal masses: exchange the normal component.
                val impulse = (1f + cloth.ballRestitution) * approach * 0.5f
                a.velocity = a.velocity - normal * impulse
                b.velocity = b.velocity + normal * impulse

                applyThrow(a, b, normal, impulse)
            }
        }
    }

    /**
     * Cut induced throw: the rubbing of two ball surfaces drags the object ball slightly
     * off the line of centres. Small, but it is the difference between potting a thin cut
     * and rattling it, so the robot's look ahead sees it too.
     */
    private fun applyThrow(a: Ball, b: Ball, normal: Vec2, impulse: Float) {
        val tangent = normal.perpendicular()
        val relative = a.velocity - b.velocity
        val surface = relative.dot(tangent) + radius * (a.wz + b.wz)
        if (abs(surface) < 1e-5f) return
        val maxFriction = cloth.ballFriction * impulse
        val jt = (-surface * 0.2f).coerceIn(-maxFriction, maxFriction)
        a.velocity = a.velocity + tangent * jt
        b.velocity = b.velocity - tangent * jt
        val dw = (5f * jt) / (2f * radius)
        a.wz -= dw * 0.5f
        b.wz -= dw * 0.5f
    }

    private fun resolveCushions(b: Ball, events: ShotEvents?) {
        val limX = TableGeometry.HALF_LENGTH - radius
        val limY = TableGeometry.HALF_WIDTH - radius
        var hit = false

        if (b.position.x < -limX && !inShortRailMouth(b.position)) {
            bounce(b, Vec2(1f, 0f), Vec2(-limX, b.position.y))
            hit = true
        } else if (b.position.x > limX && !inShortRailMouth(b.position)) {
            bounce(b, Vec2(-1f, 0f), Vec2(limX, b.position.y))
            hit = true
        }

        if (b.position.y < -limY && !inLongRailMouth(b.position)) {
            bounce(b, Vec2(0f, 1f), Vec2(b.position.x, -limY))
            hit = true
        } else if (b.position.y > limY && !inLongRailMouth(b.position)) {
            bounce(b, Vec2(0f, -1f), Vec2(b.position.x, limY))
            hit = true
        }

        if (hit && events != null) {
            events.railContactBalls.add(b.number)
            if (events.contactHappened) events.cushionAfterContact = true
        }

        rattleInJaws(b)
    }

    /** The short rails are cut away where the corner pockets are. */
    private fun inShortRailMouth(p: Vec2): Boolean =
        abs(p.y) > TableGeometry.HALF_WIDTH - TableGeometry.CORNER_MOUTH

    /** The long rails are cut away at both corners and at the side pocket. */
    private fun inLongRailMouth(p: Vec2): Boolean =
        abs(p.x) > TableGeometry.HALF_LENGTH - TableGeometry.CORNER_MOUTH ||
            abs(p.x) < TableGeometry.SIDE_MOUTH

    private fun bounce(b: Ball, normal: Vec2, contactPoint: Vec2) {
        b.position = contactPoint
        val vn = b.velocity.dot(normal)
        if (vn >= 0f) return
        collisionListener?.onCushionCollision(-vn, contactPoint)
        val tangent = normal.perpendicular()
        var vt = b.velocity.dot(tangent)

        // English grabs the cloth covered rubber and kicks the ball sideways.
        vt += cloth.cushionSpinTransfer * radius * b.wz
        vt *= 0.92f
        b.wz *= 0.55f

        val newVn = -vn * cloth.cushionRestitution
        b.velocity = normal * newVn + tangent * vt

        // A cushion kills most of the follow / draw on the ball.
        b.wx *= 0.4f
        b.wy *= 0.4f
    }

    /**
     * A ball that enters a pocket mouth but is not swallowed rattles around in the jaws
     * instead of flying off the table.
     */
    private fun rattleInJaws(b: Ball) {
        val outerX = TableGeometry.HALF_LENGTH + JAW_DEPTH
        val outerY = TableGeometry.HALF_WIDTH + JAW_DEPTH
        var px = b.position.x
        var py = b.position.y
        var vx = b.velocity.x
        var vy = b.velocity.y
        var changed = false
        if (px < -outerX) { px = -outerX; vx = abs(vx) * JAW_RESTITUTION; changed = true }
        if (px > outerX) { px = outerX; vx = -abs(vx) * JAW_RESTITUTION; changed = true }
        if (py < -outerY) { py = -outerY; vy = abs(vy) * JAW_RESTITUTION; changed = true }
        if (py > outerY) { py = outerY; vy = -abs(vy) * JAW_RESTITUTION; changed = true }
        if (changed) {
            b.position = Vec2(px, py)
            b.velocity = Vec2(vx, vy)
        }
    }

    private fun checkPockets(events: ShotEvents?) {
        for (b in balls) {
            if (b.pocketed) continue
            for (pocket in TableGeometry.pockets) {
                if (b.position.distanceTo(pocket.center) < pocket.radius) {
                    collisionListener?.onPocketed(b.number, b.velocity.length())
                    b.pocketed = true
                    b.stop()
                    b.position = pocket.center
                    events?.pocketed?.add(b.number)
                    break
                }
            }
        }
    }

    // -------------------------------------------------------------- orientation

    /** Rodrigues rotation of the ball's orientation matrix, so the numbers roll correctly. */
    private fun integrateOrientation(b: Ball, h: Float) {
        val wLen = sqrt(b.wx * b.wx + b.wy * b.wy + b.wz * b.wz)
        if (wLen < 1e-4f) return
        val angle = wLen * h
        val ax = b.wx / wLen
        val ay = b.wy / wLen
        val az = b.wz / wLen
        val c = cos(angle)
        val s = sin(angle)
        val t = 1f - c

        val r = floatArrayOf(
            t * ax * ax + c, t * ax * ay - s * az, t * ax * az + s * ay,
            t * ax * ay + s * az, t * ay * ay + c, t * ay * az - s * ax,
            t * ax * az - s * ay, t * ay * az + s * ax, t * az * az + c
        )

        val m = b.orientation
        val out = FloatArray(9)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                var sum = 0f
                for (k in 0 until 3) {
                    sum += r[row * 3 + k] * m[k * 3 + col]
                }
                out[row * 3 + col] = sum
            }
        }
        out.copyInto(m)
    }

    companion object {
        const val FIXED_STEP = 1f / 240f
        private const val MAX_FRAME_SECONDS = 0.1f
        private const val MAX_STEPS_PER_FRAME = 32
        private const val MIN_STEP = 1f / 2000f
        private const val MAX_SUBSTEPS = 64
        private const val SLIP_EPSILON = 0.012f
        private const val JAW_DEPTH = 0.055f
        private const val JAW_RESTITUTION = 0.35f
    }
}
