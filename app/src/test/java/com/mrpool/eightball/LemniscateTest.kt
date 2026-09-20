package com.mrpool.eightball

import com.mrpool.eightball.ui.Lemniscate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The shape of the infinity mark on the studio card. */
class LemniscateTest {

    @Test
    fun `the curve is sampled inclusively and closes on itself`() {
        val points = Lemniscate.points(steps = 120)
        assertEquals(121, points.size)

        val first = points.first()
        val last = points.last()
        assertEquals("the curve must close back on its start", first.x.toDouble(), last.x.toDouble(), 1e-4)
        assertEquals("the curve must close back on its start", first.y.toDouble(), last.y.toDouble(), 1e-4)
    }

    @Test
    fun `the curve fills its box without escaping it`() {
        val halfWidth = 95f
        val halfHeight = 48f
        val points = Lemniscate.points(steps = 400, halfWidth = halfWidth, halfHeight = halfHeight)

        val maxX = points.maxOf { abs(it.x) }
        val maxY = points.maxOf { abs(it.y) }

        assertTrue("the mark overflows its box on x: $maxX", maxX <= halfWidth + 1e-3f)
        assertTrue("the mark overflows its box on y: $maxY", maxY <= halfHeight + 1e-3f)
        // It should actually reach the edges, or the logo would float small in its box.
        assertTrue("the mark does not fill its width: $maxX", maxX > halfWidth * 0.98f)
        assertTrue("the mark does not fill its height: $maxY", maxY > halfHeight * 0.68f)
    }

    @Test
    fun `the figure of eight crosses at the centre and is symmetric`() {
        val points = Lemniscate.points(steps = 360, halfWidth = 100f, halfHeight = 50f)

        // Both lobes: points well to the left and well to the right of centre.
        assertTrue("no right hand lobe", points.any { it.x > 80f })
        assertTrue("no left hand lobe", points.any { it.x < -80f })

        // The crossing: the curve passes through the origin.
        assertTrue(
            "the loops never meet in the middle",
            points.any { abs(it.x) < 1f && abs(it.y) < 1f }
        )

        // Point symmetry through the origin, which is what makes it look balanced.
        for (point in points) {
            val mirrored = points.any {
                abs(it.x + point.x) < 1.5f && abs(it.y + point.y) < 1.5f
            }
            assertTrue("no mirror for (${point.x}, ${point.y})", mirrored)
        }
    }

    @Test
    fun `it is one continuous contour, with no jumps`() {
        // The splash draws the mark by animating a single stroke along one contour. A gap
        // in the samples would show up as a straight line cutting across the logo.
        val points = Lemniscate.points(steps = 360, halfWidth = 100f, halfHeight = 50f)
        var longest = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            val step = kotlin.math.sqrt(dx * dx + dy * dy)
            if (step > longest) longest = step
        }
        assertTrue("the curve jumps by $longest between samples", longest < 6f)
    }

    @Test
    fun `too few samples are rejected rather than drawn as a scribble`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lemniscate.points(steps = 3)
        }
    }
}
