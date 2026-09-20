package com.mrpool.eightball.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable 2D vector used by the whole physics / aiming stack. */
data class Vec2(val x: Float = 0f, val y: Float = 0f) {

    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    operator fun div(s: Float) = Vec2(x / s, y / s)
    operator fun unaryMinus() = Vec2(-x, -y)

    fun dot(o: Vec2): Float = x * o.x + y * o.y

    /** 2D cross product (z component of the 3D cross product). */
    fun cross(o: Vec2): Float = x * o.y - y * o.x

    fun length(): Float = hypot(x, y)

    fun lengthSq(): Float = x * x + y * y

    fun normalized(): Vec2 {
        val len = length()
        return if (len < 1e-7f) ZERO else Vec2(x / len, y / len)
    }

    /** Rotates the vector 90 degrees counter clockwise. */
    fun perpendicular(): Vec2 = Vec2(-y, x)

    fun rotated(radians: Float): Vec2 {
        val c = cos(radians)
        val s = sin(radians)
        return Vec2(x * c - y * s, x * s + y * c)
    }

    fun angle(): Float = atan2(y, x)

    fun distanceTo(o: Vec2): Float = hypot(x - o.x, y - o.y)

    fun isNearlyZero(eps: Float = 1e-6f): Boolean = abs(x) < eps && abs(y) < eps

    companion object {
        val ZERO = Vec2(0f, 0f)

        fun fromAngle(radians: Float, length: Float = 1f) =
            Vec2(cos(radians) * length, sin(radians) * length)
    }
}

/** Shortest distance from [point] to the segment [a]..[b]. */
fun distancePointToSegment(point: Vec2, a: Vec2, b: Vec2): Float {
    val ab = b - a
    val lenSq = ab.lengthSq()
    if (lenSq < 1e-9f) return point.distanceTo(a)
    var t = (point - a).dot(ab) / lenSq
    if (t < 0f) t = 0f
    if (t > 1f) t = 1f
    val closest = a + ab * t
    return point.distanceTo(closest)
}

/** Normalises an angle into (-PI, PI]. */
fun normalizeAngle(angle: Float): Float {
    var a = angle
    val twoPi = (Math.PI * 2.0).toFloat()
    while (a <= -Math.PI.toFloat()) a += twoPi
    while (a > Math.PI.toFloat()) a -= twoPi
    return a
}

internal fun fastSqrt(v: Float): Float = sqrt(v)
