package com.mrpool.eightball

import com.mrpool.eightball.game.TableGeometry
import com.mrpool.eightball.render.TableLayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards the bug that put a diagonal of bare wood across the green cloth.
 *
 * The table body was drawn with its top face at exactly the height of the cloth, and it is
 * wider than the playing surface, so the two were coplanar across the whole bed. Neither
 * surface can win a depth test against the other, and because the two quads are split into
 * triangles along different diagonals, the result was a clean diagonal rather than noise.
 *
 * Coplanar surfaces are invisible while reading the source and obvious on a phone, so they
 * are checked here instead.
 */
class TableLayersTest {

    /** A flat thing drawn on the table: its height, and how far it spreads. */
    private data class Surface(
        val name: String,
        val height: Float,
        val halfLength: Float,
        val halfWidth: Float
    ) {
        /** True when this and [other] cover any of the same ground. */
        fun overlaps(other: Surface): Boolean =
            halfLength > 0f && other.halfLength > 0f &&
                halfWidth > 0f && other.halfWidth > 0f
    }

    /** Every flat surface parallel to the bed, with the footprint it covers. */
    private fun surfaces(): List<Surface> = listOf(
        Surface(
            "table body (top face)",
            TableLayers.APRON_TOP,
            TableLayers.frameHalfLength(),
            TableLayers.frameHalfWidth()
        ),
        Surface(
            "cloth",
            TableLayers.CLOTH,
            TableLayers.clothHalfLength(),
            TableLayers.clothHalfWidth()
        ),
        Surface("ball shadows", TableLayers.BALL_SHADOW, TableGeometry.HALF_LENGTH, TableGeometry.HALF_WIDTH),
        Surface("spot and head string", TableLayers.MARKING, TableGeometry.HALF_LENGTH, TableGeometry.HALF_WIDTH),
        Surface("aiming dashes", TableLayers.GUIDE_DASH, TableGeometry.HALF_LENGTH, TableGeometry.HALF_WIDTH),
        Surface("aiming line", TableLayers.GUIDE_LINE, TableGeometry.HALF_LENGTH, TableGeometry.HALF_WIDTH),
        Surface("pocket trim", TableLayers.POCKET_TRIM, TableGeometry.HALF_LENGTH, TableGeometry.HALF_WIDTH),
        Surface("pocket hole", TableLayers.POCKET_HOLE, TableGeometry.HALF_LENGTH, TableGeometry.HALF_WIDTH)
    )

    @Test
    fun `no two surfaces that overlap each other share a height`() {
        val all = surfaces()
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                val a = all[i]
                val b = all[j]
                if (!a.overlaps(b)) continue
                val gap = abs(a.height - b.height)
                assertTrue(
                    "'${a.name}' and '${b.name}' are ${gap}m apart, which is close enough " +
                        "to coplanar to z-fight. They would tear into each other on screen.",
                    gap >= TableLayers.MINIMUM_SEPARATION
                )
            }
        }
    }

    @Test
    fun `the table body sits below the cloth, not level with it`() {
        // The exact failure that shipped: a top face at 0f, the same as the cloth.
        assertTrue(
            "the table body's top face is at ${TableLayers.APRON_TOP}, level with or above " +
                "the cloth — this is the bug that striped the table with bare wood",
            TableLayers.APRON_TOP <= TableLayers.CLOTH - TableLayers.MINIMUM_SEPARATION
        )
    }

    @Test
    fun `everything drawn on the bed sits above the cloth`() {
        for (surface in surfaces()) {
            if (surface.name == "table body (top face)") continue
            if (surface.name == "cloth") continue
            assertTrue(
                "'${surface.name}' is at ${surface.height}, under the cloth — it would be hidden",
                surface.height > TableLayers.CLOTH
            )
        }
    }

    @Test
    fun `the cloth reaches the rails, so no bare body shows around the green`() {
        // It stops where the rails begin, which is what hides the body's own edge.
        assertEquals(
            "the cloth should run out to the foot of the rails",
            TableLayers.cushionOuterHalfLength(),
            TableLayers.clothHalfLength(),
            1e-5f
        )
        assertEquals(
            TableLayers.cushionOuterHalfWidth(),
            TableLayers.clothHalfWidth(),
            1e-5f
        )
        assertTrue(
            "the cloth must cover the playing surface at the very least",
            TableLayers.clothHalfLength() >= TableGeometry.HALF_LENGTH &&
                TableLayers.clothHalfWidth() >= TableGeometry.HALF_WIDTH
        )
        assertTrue(
            "the rails must cover the body's edge where the cloth stops",
            TableLayers.frameHalfLength() > TableLayers.clothHalfLength()
        )
    }

    @Test
    fun `the rail sights sit on top of the rail, not inside it`() {
        val railTop = TableLayers.CUSHION_HEIGHT * 2f + TableLayers.RAIL_HEIGHT * 2f
        assertTrue(
            "the diamonds are at ${TableLayers.RAIL_SIGHT} but the rail's top is $railTop",
            TableLayers.RAIL_SIGHT > railTop
        )
    }
}
