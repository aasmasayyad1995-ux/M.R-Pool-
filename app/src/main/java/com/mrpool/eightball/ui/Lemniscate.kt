package com.mrpool.eightball.ui

import kotlin.math.cos
import kotlin.math.sin

/**
 * The infinity symbol, as a curve rather than a letter.
 *
 * Uses the lemniscate of Bernoulli, whose parametric form sweeps the whole figure of eight
 * in a single continuous pass from t = 0 to 2*PI. That matters for the splash: the logo is
 * drawn by animating one stroke along one contour, and a shape made of two separate loops
 * could not be drawn that way.
 *
 *     x = cos t / (1 + sin^2 t)
 *     y = sin t * cos t / (1 + sin^2 t)
 *
 * Kept free of any Android type so the shape itself can be unit tested.
 */
object Lemniscate {

    /**
     * Samples the curve into [steps] points, each scaled to fit a box of [halfWidth] by
     * [halfHeight] around the origin. The last point closes back onto the first.
     */
    fun points(steps: Int = 240, halfWidth: Float = 1f, halfHeight: Float = 1f): List<Point> {
        require(steps >= 8) { "a lemniscate needs at least 8 samples, asked for $steps" }
        val out = ArrayList<Point>(steps + 1)
        val twoPi = (Math.PI * 2.0).toFloat()
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * twoPi
            val sinT = sin(t)
            val cosT = cos(t)
            val denominator = 1f + sinT * sinT
            // The raw curve spans x in [-1, 1] and y in [-0.5, 0.5], so y is doubled to fill.
            out.add(
                Point(
                    x = (cosT / denominator) * halfWidth,
                    y = (sinT * cosT / denominator) * 2f * halfHeight
                )
            )
        }
        return out
    }

    data class Point(val x: Float, val y: Float)
}
