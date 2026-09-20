package com.mrpool.eightball.game

/** Which of the two 8-ball groups a ball belongs to. */
enum class BallGroup { CUE, SOLIDS, STRIPES, EIGHT }

/**
 * A single ball on the table.
 *
 * Angular velocity is tracked in 3 components so that follow / draw (the horizontal
 * axis, [wx] / [wy]) and english (the vertical axis, [wz]) behave the way players expect.
 */
class Ball(
    val number: Int,
    var position: Vec2,
    var velocity: Vec2 = Vec2.ZERO,
    var wx: Float = 0f,
    var wy: Float = 0f,
    var wz: Float = 0f,
    var pocketed: Boolean = false,
    /** Accumulated orientation as a row-major 3x3 matrix, used for rendering the roll. */
    val orientation: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
) {
    val group: BallGroup
        get() = when {
            number == 0 -> BallGroup.CUE
            number == 8 -> BallGroup.EIGHT
            number < 8 -> BallGroup.SOLIDS
            else -> BallGroup.STRIPES
        }

    val isCue: Boolean get() = number == 0

    val isMoving: Boolean
        get() = !pocketed && (velocity.lengthSq() > STOP_SPEED_SQ ||
            (wx * wx + wy * wy + wz * wz) > STOP_SPIN_SQ)

    fun stop() {
        velocity = Vec2.ZERO
        wx = 0f
        wy = 0f
        wz = 0f
    }

    fun copyState(): Ball = Ball(
        number = number,
        position = position,
        velocity = velocity,
        wx = wx,
        wy = wy,
        wz = wz,
        pocketed = pocketed,
        orientation = orientation.copyOf()
    )

    companion object {
        const val STOP_SPEED_SQ = 1e-4f
        const val STOP_SPIN_SQ = 1e-2f
    }
}
