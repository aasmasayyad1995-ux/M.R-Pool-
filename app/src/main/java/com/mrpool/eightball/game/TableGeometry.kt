package com.mrpool.eightball.game

/**
 * Geometry of the playing surface, in metres, centred on the origin.
 *
 * +X runs along the length of the table (the head string is at negative X),
 * +Y runs across the width. The renderer maps this plane onto world XZ.
 */
object TableGeometry {

    const val BALL_RADIUS = 0.028575f
    const val BALL_DIAMETER = BALL_RADIUS * 2f
    const val BALL_MASS = 0.17f

    /** Half the playing length / width of a 9ft table (2.24m x 1.12m). */
    const val HALF_LENGTH = 1.12f
    const val HALF_WIDTH = 0.56f

    const val CORNER_POCKET_RADIUS = 0.062f
    const val SIDE_POCKET_RADIUS = 0.067f

    /** Half width of the gap cut into a cushion by a pocket mouth. */
    const val CORNER_MOUTH = 0.075f
    const val SIDE_MOUTH = 0.080f

    /** X of the head string; the cue ball is broken from behind it. */
    const val HEAD_STRING_X = -HALF_LENGTH * 0.5f

    /** X of the foot spot, where the apex ball of the rack sits. */
    const val FOOT_SPOT_X = HALF_LENGTH * 0.5f

    val pockets: List<Pocket> = listOf(
        Pocket(Vec2(-HALF_LENGTH, -HALF_WIDTH), CORNER_POCKET_RADIUS, PocketId.BOTTOM_LEFT),
        Pocket(Vec2(0f, -HALF_WIDTH), SIDE_POCKET_RADIUS, PocketId.BOTTOM_MIDDLE),
        Pocket(Vec2(HALF_LENGTH, -HALF_WIDTH), CORNER_POCKET_RADIUS, PocketId.BOTTOM_RIGHT),
        Pocket(Vec2(-HALF_LENGTH, HALF_WIDTH), CORNER_POCKET_RADIUS, PocketId.TOP_LEFT),
        Pocket(Vec2(0f, HALF_WIDTH), SIDE_POCKET_RADIUS, PocketId.TOP_MIDDLE),
        Pocket(Vec2(HALF_LENGTH, HALF_WIDTH), CORNER_POCKET_RADIUS, PocketId.TOP_RIGHT)
    )

    /**
     * Point a ball should be aimed at for a given pocket. Aiming at the exact pocket
     * centre works for side pockets but corner pockets swallow the ball a little
     * "behind" the rail intersection, so the target is pulled in along the diagonal.
     */
    fun aimPoint(pocket: Pocket): Vec2 = when (pocket.id) {
        PocketId.BOTTOM_MIDDLE -> Vec2(pocket.center.x, pocket.center.y + BALL_RADIUS * 0.4f)
        PocketId.TOP_MIDDLE -> Vec2(pocket.center.x, pocket.center.y - BALL_RADIUS * 0.4f)
        else -> Vec2(
            pocket.center.x - Math.signum(pocket.center.x) * BALL_RADIUS * 0.45f,
            pocket.center.y - Math.signum(pocket.center.y) * BALL_RADIUS * 0.45f
        )
    }

    /** True when [p] is inside the legal playing rectangle (ball centre limits). */
    fun isInsideCushions(p: Vec2): Boolean =
        p.x > -HALF_LENGTH + BALL_RADIUS && p.x < HALF_LENGTH - BALL_RADIUS &&
            p.y > -HALF_WIDTH + BALL_RADIUS && p.y < HALF_WIDTH - BALL_RADIUS

    fun clampInsideCushions(p: Vec2): Vec2 {
        val minX = -HALF_LENGTH + BALL_RADIUS
        val maxX = HALF_LENGTH - BALL_RADIUS
        val minY = -HALF_WIDTH + BALL_RADIUS
        val maxY = HALF_WIDTH - BALL_RADIUS
        return Vec2(p.x.coerceIn(minX, maxX), p.y.coerceIn(minY, maxY))
    }
}

enum class PocketId { BOTTOM_LEFT, BOTTOM_MIDDLE, BOTTOM_RIGHT, TOP_LEFT, TOP_MIDDLE, TOP_RIGHT }

data class Pocket(val center: Vec2, val radius: Float, val id: PocketId) {
    val isSide: Boolean get() = id == PocketId.BOTTOM_MIDDLE || id == PocketId.TOP_MIDDLE
}
