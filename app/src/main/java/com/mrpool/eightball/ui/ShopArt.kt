package com.mrpool.eightball.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The drawings the two shops are built out of.
 *
 * Nothing here is a photograph or a 3D model: it is all flat fills with the light put
 * where the light would be. A table reads as a table because the far rail is shorter than
 * the near one, because the frame has a side you can see, and because it stands on legs
 * that cast a shadow. A cue reads as a cue because it is thinner at the tip than at the
 * butt and brighter along its spine than along its edges.
 *
 * Both take a [detailed] flag. The big preview panel at the top of each shop asks for the
 * full drawing; the thumbnail in each row asks for the same shape with the fiddly parts
 * left out, because detail smaller than a few pixels only turns into mud.
 */

/** The table, seen from behind the near rail and a little above the lamp. */
fun DrawScope.drawPoolTable(
    felt: Color,
    rail: Color,
    trim: Color,
    detailed: Boolean
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f

    val farY = h * 0.11f
    val nearY = h * 0.605f
    val farHalf = w * 0.352f
    val nearHalf = w * 0.468f
    val apron = h * 0.115f
    val floorY = h * 0.90f

    // v runs 0 at the far rail to 1 at the near rail. Everything on the table is placed
    // through these two, so the perspective stays honest wherever it is drawn.
    fun yAt(v: Float) = farY + (nearY - farY) * v
    fun halfAt(v: Float) = farHalf + (nearHalf - farHalf) * v

    // --- the floor it stands on -------------------------------------------------------
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x80000000), Color(0x00000000)),
            center = Offset(cx, floorY),
            radius = w * 0.5f
        ),
        topLeft = Offset(0f, floorY - h * 0.085f),
        size = Size(w, h * 0.17f)
    )

    // --- legs -------------------------------------------------------------------------
    fun leg(x: Float) {
        val top = nearY + apron * 0.5f
        val tHalf = w * 0.042f
        val bHalf = w * 0.031f
        drawPath(
            path = Path().apply {
                moveTo(x - tHalf, top)
                lineTo(x + tHalf, top)
                lineTo(x + bHalf, floorY)
                lineTo(x - bHalf, floorY)
                close()
            },
            brush = Brush.verticalGradient(
                0f to rail.darken(0.28f),
                0.55f to rail.darken(0.50f),
                1f to rail.darken(0.74f),
                startY = top,
                endY = floorY
            )
        )
    }
    leg(cx - nearHalf * 0.78f)
    leg(cx + nearHalf * 0.78f)

    // --- the sides of the frame, which is where the thickness comes from ---------------
    fun sideFace(sign: Float) {
        drawPath(
            path = Path().apply {
                moveTo(cx + sign * farHalf, farY)
                lineTo(cx + sign * nearHalf, nearY)
                lineTo(cx + sign * nearHalf, nearY + apron)
                lineTo(cx + sign * farHalf, farY + apron * 0.45f)
                close()
            },
            color = rail.darken(0.58f)
        )
    }
    sideFace(-1f)
    sideFace(1f)

    drawPath(                                   // the apron facing the player
        path = Path().apply {
            moveTo(cx - nearHalf, nearY)
            lineTo(cx + nearHalf, nearY)
            lineTo(cx + nearHalf, nearY + apron)
            lineTo(cx - nearHalf, nearY + apron)
            close()
        },
        brush = Brush.verticalGradient(
            0f to rail.darken(0.20f),
            1f to rail.darken(0.62f),
            startY = nearY,
            endY = nearY + apron
        )
    )

    // --- the rail top -----------------------------------------------------------------
    drawPath(
        path = Path().apply {
            moveTo(cx - farHalf, farY)
            lineTo(cx + farHalf, farY)
            lineTo(cx + nearHalf, nearY)
            lineTo(cx - nearHalf, nearY)
            close()
        },
        brush = Brush.verticalGradient(
            0f to rail.lighten(0.34f),
            0.5f to rail.lighten(0.04f),
            1f to rail.darken(0.26f),
            startY = farY,
            endY = nearY
        )
    )

    // --- the cloth --------------------------------------------------------------------
    val clothFarV = 0.145f
    val clothNearV = 0.845f
    val clothFarY = yAt(clothFarV)
    val clothNearY = yAt(clothNearV)
    val clothFarHalf = halfAt(clothFarV) - w * 0.052f
    val clothNearHalf = halfAt(clothNearV) - w * 0.068f

    fun clothHalfAt(v: Float) =
        clothFarHalf + (clothNearHalf - clothFarHalf) * ((v - clothFarV) / (clothNearV - clothFarV))
    fun clothX(u: Float, v: Float) = cx + (u - 0.5f) * 2f * clothHalfAt(v)

    drawPath(
        path = Path().apply {
            moveTo(cx - clothFarHalf, clothFarY)
            lineTo(cx + clothFarHalf, clothFarY)
            lineTo(cx + clothNearHalf, clothNearY)
            lineTo(cx - clothNearHalf, clothNearY)
            close()
        },
        brush = Brush.verticalGradient(
            0f to felt.darken(0.34f),       // the far end falls away from the lamp
            0.40f to felt.lighten(0.10f),
            1f to felt.darken(0.14f),
            startY = clothFarY,
            endY = clothNearY
        )
    )

    // The pool of lamplight on the middle of the cloth.
    val lampR = (clothNearY - clothFarY) * 2.0f
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF)),
            center = Offset(cx, (clothFarY + clothNearY) * 0.5f),
            radius = lampR
        ),
        topLeft = Offset(cx - lampR, clothFarY),
        size = Size(lampR * 2f, clothNearY - clothFarY)
    )

    // --- six pockets ------------------------------------------------------------------
    fun pocket(px: Float, py: Float, r: Float) {
        drawOval(                               // leather collar, lit from above
            brush = Brush.verticalGradient(
                0f to trim.lighten(0.16f),
                1f to trim.darken(0.50f),
                startY = py - r * 0.62f,
                endY = py + r * 0.62f
            ),
            topLeft = Offset(px - r * 0.96f, py - r * 0.62f),
            size = Size(r * 1.92f, r * 1.24f)
        )
        drawOval(                               // the drop, darkest at the back
            brush = Brush.verticalGradient(
                0f to Color(0xFF010308),
                1f to Color(0xFF0D1218),
                startY = py - r * 0.46f,
                endY = py + r * 0.46f
            ),
            topLeft = Offset(px - r * 0.80f, py - r * 0.46f),
            size = Size(r * 1.60f, r * 0.92f)
        )
    }
    val rFar = w * 0.040f
    val rNear = w * 0.050f
    pocket(cx - clothFarHalf, clothFarY, rFar)
    pocket(cx + clothFarHalf, clothFarY, rFar)
    pocket(cx, clothFarY, rFar * 0.90f)
    pocket(cx - clothNearHalf, clothNearY, rNear)
    pocket(cx + clothNearHalf, clothNearY, rNear)
    pocket(cx, clothNearY, rNear * 0.90f)

    // --- sight diamonds, the mark of a table somebody looks after ----------------------
    if (detailed) {
        val dot = w * 0.0075f
        for (u in listOf(0.125f, 0.25f, 0.375f, 0.625f, 0.75f, 0.875f)) {
            drawCircle(
                color = trim.lighten(0.55f).copy(alpha = 0.8f),
                radius = dot,
                center = Offset(clothX(u, clothNearV), (clothNearY + nearY) * 0.5f)
            )
            drawCircle(
                color = trim.lighten(0.55f).copy(alpha = 0.6f),
                radius = dot * 0.85f,
                center = Offset(clothX(u, clothFarV), (farY + clothFarY) * 0.5f)
            )
        }
    }

    // --- balls ------------------------------------------------------------------------
    fun ball(u: Float, v: Float, baseR: Float, c: Color) {
        val x = clothX(u, v)
        val y = yAt(v)
        val r = baseR * (0.80f + 0.40f * v)
        drawOval(                               // the shadow welds it to the cloth
            color = Color(0x4D000000),
            topLeft = Offset(x - r * 1.05f, y + r * 0.18f),
            size = Size(r * 2.1f, r * 0.78f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.lighten(0.45f), c, c.darken(0.55f)),
                center = Offset(x - r * 0.34f, y - r * 0.38f),
                radius = r * 1.65f
            ),
            radius = r,
            center = Offset(x, y)
        )
        drawCircle(                             // the specular, which is what makes it round
            color = Color(0x99FFFFFF),
            radius = r * 0.26f,
            center = Offset(x - r * 0.36f, y - r * 0.40f)
        )
    }

    val baseR = if (detailed) w * 0.0255f else w * 0.036f
    if (detailed) {
        // A rack, loosely. Six balls say "eight ball" without turning into porridge.
        ball(0.560f, 0.500f, baseR, Color(0xFFE8C72A))
        ball(0.628f, 0.395f, baseR, Color(0xFF1F5FBF))
        ball(0.628f, 0.605f, baseR, Color(0xFFC8352B))
        ball(0.696f, 0.295f, baseR, Color(0xFF7B3FA8))
        ball(0.696f, 0.500f, baseR, Color(0xFF14171B))   // the eight, in the middle
        ball(0.696f, 0.705f, baseR, Color(0xFFDB7A1E))
        ball(0.230f, 0.520f, baseR, Color(0xFFF0EDE4))   // the cue ball, down the table
    } else {
        ball(0.300f, 0.520f, baseR, Color(0xFFF0EDE4))
        ball(0.560f, 0.410f, baseR, Color(0xFFC8352B))
        ball(0.650f, 0.620f, baseR, Color(0xFF14171B))
    }

    // One bright line along the far rail: the lamp catching the top of the wood.
    drawPath(
        path = Path().apply {
            moveTo(cx - farHalf, farY)
            lineTo(cx + farHalf, farY)
            lineTo(cx + farHalf * 0.985f, farY + h * 0.018f)
            lineTo(cx - farHalf * 0.985f, farY + h * 0.018f)
            close()
        },
        color = rail.lighten(0.62f).copy(alpha = 0.7f)
    )
}

/** The cue, laid on a diagonal across its panel, tip at the left. */
fun DrawScope.drawCueStick(
    shaft: Color,
    butt: Color,
    accent: Color,
    detailed: Boolean
) {
    val w = size.width
    val h = size.height

    val tipX = w * 0.015f
    val buttX = w * 0.985f
    val tipY = h * 0.64f
    val buttY = h * 0.36f
    val tipR = h * 0.058f        // a cue tip is about 13mm across
    val buttR = h * 0.140f       // the butt is a bit over twice that

    fun yAt(t: Float) = tipY + (buttY - tipY) * t
    fun rAt(t: Float) = tipR + (buttR - tipR) * t
    fun xAt(t: Float) = tipX + (buttX - tipX) * t

    /** One tapered length of the cue, as a four cornered sliver. */
    fun segment(t0: Float, t1: Float): Path = Path().apply {
        moveTo(xAt(t0), yAt(t0) - rAt(t0))
        lineTo(xAt(t1), yAt(t1) - rAt(t1))
        lineTo(xAt(t1), yAt(t1) + rAt(t1))
        lineTo(xAt(t0), yAt(t0) + rAt(t0))
        close()
    }

    /** Rounded across its width, so it reads as a rod and not a plank. */
    fun shade(t0: Float, t1: Float, c: Color) {
        drawPath(
            path = segment(t0, t1),
            brush = Brush.verticalGradient(
                0f to c.darken(0.50f),
                0.28f to c.lighten(0.34f),
                0.52f to c,
                1f to c.darken(0.58f),
                startY = yAt(t0) - rAt(t1),
                endY = yAt(t0) + rAt(t1)
            )
        )
    }

    // The shadow it casts on the panel behind it.
    drawPath(
        path = Path().apply {
            moveTo(tipX, tipY + tipR * 2.8f)
            lineTo(buttX, buttY + buttR * 2.3f)
            lineTo(buttX, buttY + buttR * 3.6f)
            lineTo(tipX, tipY + tipR * 4.6f)
            close()
        },
        color = Color(0x33000000)
    )

    shade(0.050f, 0.520f, shaft)                // shaft
    shade(0.520f, 0.585f, accent)               // joint collar
    shade(0.585f, 0.780f, butt.lighten(0.12f))  // forearm
    shade(0.780f, 0.935f, butt.darken(0.20f))   // wrap, darker where it is held
    shade(0.935f, 1f, accent.darken(0.28f))     // butt cap

    // Ferrule and tip: the two small things that say "cue" at a glance.
    shade(0.020f, 0.050f, Color(0xFFF2EDE2))
    drawPath(segment(0f, 0.020f), color = Color(0xFF5B86C4))

    if (detailed) {
        // Inlay points up the forearm.
        for (i in 0..3) {
            val t = 0.610f + i * 0.040f
            drawPath(
                path = Path().apply {
                    moveTo(xAt(t), yAt(t) - rAt(t) * 0.82f)
                    lineTo(xAt(t + 0.028f), yAt(t) - rAt(t) * 0.04f)
                    lineTo(xAt(t), yAt(t) + rAt(t) * 0.72f)
                    close()
                },
                color = accent.lighten(0.28f)
            )
        }
        // Irish linen wrap: fine rings, which is how a wrap actually looks.
        var t = 0.790f
        while (t < 0.930f) {
            drawPath(segment(t, t + 0.006f), color = butt.darken(0.52f).copy(alpha = 0.55f))
            t += 0.016f
        }
        // Thin bright rings either side of the joint and above the cap.
        for (r in listOf(0.516f, 0.589f, 0.931f)) {
            drawPath(segment(r, r + 0.008f), color = accent.lighten(0.55f))
        }
    }

    // One long highlight along the spine. This line does most of the work of making a
    // flat sliver look like something you could pick up.
    drawPath(
        path = Path().apply {
            moveTo(xAt(0.055f), yAt(0.055f) - rAt(0.055f) * 0.54f)
            lineTo(xAt(1f), yAt(1f) - rAt(1f) * 0.60f)
            lineTo(xAt(1f), yAt(1f) - rAt(1f) * 0.24f)
            lineTo(xAt(0.055f), yAt(0.055f) - rAt(0.055f) * 0.16f)
            close()
        },
        color = Color(0x3DFFFFFF)
    )
}
