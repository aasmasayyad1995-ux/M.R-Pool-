package com.mrpool.eightball.render

import com.mrpool.eightball.game.TableGeometry

/**
 * The height of every flat surface drawn on or under the table, and how far each one
 * spreads across it.
 *
 * This exists because of a real bug. The table body was drawn with its top face at exactly
 * the height of the cloth, and it is wider than the playing surface, so the two were
 * coplanar right across the bed. Neither can win a depth test against the other, and
 * because the two quads are split into triangles along different diagonals, the result was
 * not noise but a clean diagonal of bare wood across the green.
 *
 * Coplanar surfaces are invisible in the source and obvious on the screen, so the numbers
 * live here, in a file with no Android in it, and a unit test asserts that no two surfaces
 * that overlap each other ever share a height.
 */
object TableLayers {

    /** Depth of the cushion rubber, measured out from the playing edge. */
    const val CUSHION_DEPTH = 0.048f

    /** Height of the cushion above the bed. */
    const val CUSHION_HEIGHT = 0.019f

    /** Width of the wooden rail that caps the table. */
    const val RAIL_WIDTH = 0.062f

    /** Height of that rail above the bed. */
    const val RAIL_HEIGHT = 0.042f

    /** Thickness of the table body below the bed. */
    const val APRON_HEIGHT = 0.045f

    // ------------------------------------------------------------------------ heights

    /** The cloth. Everything on the table is measured from here. */
    const val CLOTH = 0f

    /**
     * Top of the table body. Strictly below the cloth: the body is wider than the playing
     * surface, so a top face level with the cloth would be coplanar with it across the
     * whole bed.
     */
    const val APRON_TOP = -0.006f

    // A millimetre apart each, in the order they stack. Six millimetres from the cloth to
    // the top of a pocket is a fifth of a ball radius: flat to look at, and far enough
    // apart that no two of them can ever be mistaken for the same plane.
    const val BALL_SHADOW = 0.001f
    const val MARKING = 0.002f
    const val GUIDE_DASH = 0.003f
    const val GUIDE_LINE = 0.004f
    const val POCKET_TRIM = 0.005f
    const val POCKET_HOLE = 0.006f

    /** Diamonds inlaid into the rail, which sit on top of it. */
    const val RAIL_SIGHT = CUSHION_HEIGHT * 2f + RAIL_HEIGHT * 2f + 0.0005f

    // ---------------------------------------------------------------------- footprints

    /** Outer edge of the cushions. */
    fun cushionOuterHalfLength(): Float = TableGeometry.HALF_LENGTH + CUSHION_DEPTH * 2f

    fun cushionOuterHalfWidth(): Float = TableGeometry.HALF_WIDTH + CUSHION_DEPTH * 2f

    /** Outer edge of the whole table, rails included. */
    fun frameHalfLength(): Float = cushionOuterHalfLength() + RAIL_WIDTH * 2f

    fun frameHalfWidth(): Float = cushionOuterHalfWidth() + RAIL_WIDTH * 2f

    /**
     * The cloth runs out under the cushions and up to the rails, the way it is really
     * fitted. Stopping it at the playing edge would leave the table body showing as a bare
     * border around the green.
     */
    fun clothHalfLength(): Float = cushionOuterHalfLength()

    fun clothHalfWidth(): Float = cushionOuterHalfWidth()

    /**
     * The closest two overlapping surfaces may sit without risking a depth test that cannot
     * be decided. At the distance this table is viewed from, the depth buffer resolves
     * something like a hundredth of a millimetre, so this is a wide margin.
     */
    const val MINIMUM_SEPARATION = 0.0005f
}
