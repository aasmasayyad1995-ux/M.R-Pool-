package com.mrpool.eightball.game

import kotlin.math.sqrt
import kotlin.random.Random

/** Builds the opening layout of a game of 8-ball. */
object Rack {

    /**
     * Standard triangle: the apex ball on the foot spot, the 8 in the middle of the third
     * row, and one solid and one stripe in the back corners. Everything else is shuffled,
     * which is what makes every break different.
     */
    fun build(random: Random = Random.Default): MutableList<Ball> {
        val d = TableGeometry.BALL_DIAMETER * 1.01f
        val rowStep = d * (sqrt(3f) / 2f)
        val apexX = TableGeometry.FOOT_SPOT_X

        val solids = (1..7).toMutableList().also { it.shuffle(random) }
        val stripes = (9..15).toMutableList().also { it.shuffle(random) }

        // Slot 0 is the apex, slots 1..2 the second row, ... slots 10..14 the back row.
        val layout = arrayOfNulls<Int>(15)
        layout[0] = if (random.nextBoolean()) solids.removeAt(0) else stripes.removeAt(0)
        layout[4] = 8
        // The two back corners must not be from the same group.
        if (random.nextBoolean()) {
            layout[10] = solids.removeAt(0)
            layout[14] = stripes.removeAt(0)
        } else {
            layout[10] = stripes.removeAt(0)
            layout[14] = solids.removeAt(0)
        }
        val rest = (solids + stripes).toMutableList().also { it.shuffle(random) }
        for (i in layout.indices) {
            if (layout[i] == null) layout[i] = rest.removeAt(0)
        }

        val balls = mutableListOf<Ball>()
        balls.add(Ball(0, Vec2(TableGeometry.HEAD_STRING_X, 0f)))

        var slot = 0
        for (row in 0 until 5) {
            val x = apexX + row * rowStep
            val yStart = -row * d * 0.5f
            for (col in 0..row) {
                val y = yStart + col * d
                balls.add(Ball(layout[slot]!!, Vec2(x, y)))
                slot++
            }
        }
        return balls
    }

    /** Where a ball goes when it has to be re-spotted and the foot spot is occupied. */
    fun spotPosition(balls: List<Ball>, preferred: Vec2 = Vec2(TableGeometry.FOOT_SPOT_X, 0f)): Vec2 {
        var candidate = preferred
        var attempt = 0
        while (attempt < 240) {
            val clear = balls.none {
                !it.pocketed && it.position.distanceTo(candidate) < TableGeometry.BALL_DIAMETER * 1.02f
            }
            if (clear && TableGeometry.isInsideCushions(candidate)) return candidate
            attempt++
            // Walk down the long string towards the foot rail, then back up.
            val offset = TableGeometry.BALL_DIAMETER * 0.55f * attempt
            candidate = if (attempt % 2 == 0) {
                Vec2(preferred.x + offset, preferred.y)
            } else {
                Vec2(preferred.x - offset, preferred.y)
            }
            candidate = TableGeometry.clampInsideCushions(candidate)
        }
        return TableGeometry.clampInsideCushions(preferred)
    }
}
