package com.mrpool.eightball.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every texture in the game is painted at runtime with [Canvas], so the APK ships without
 * a single image asset and any table colour in the shop can be rendered.
 */
object Textures {

    /** Standard pool ball colours, indexed by number (1..7 repeat for 9..15). */
    private val BALL_COLORS = intArrayOf(
        Color.WHITE,              // 0 cue
        Color.rgb(248, 200, 32),  // 1 yellow
        Color.rgb(26, 74, 184),   // 2 blue
        Color.rgb(206, 40, 32),   // 3 red
        Color.rgb(96, 40, 140),   // 4 purple
        Color.rgb(232, 118, 26),  // 5 orange
        Color.rgb(20, 122, 62),   // 6 green
        Color.rgb(128, 30, 40),   // 7 maroon
        Color.rgb(20, 20, 22)     // 8 black
    )

    fun ballColor(number: Int): Int = when {
        number <= 8 -> BALL_COLORS[number]
        else -> BALL_COLORS[number - 8]
    }

    /**
     * Paints a ball: a solid or striped body with the number printed inside a white circle
     * on both sides, exactly where the sphere's UV mapping puts the equator.
     */
    fun ballBitmap(number: Int, size: Int = 512): Bitmap {
        val width = size
        val height = size / 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val color = ballColor(number)
        val striped = number in 9..15

        canvas.drawColor(Color.WHITE)

        if (number == 0) {
            paint.color = Color.rgb(252, 250, 244)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.rgb(200, 40, 40)
            canvas.drawCircle(width * 0.25f, height * 0.5f, height * 0.045f, paint)
            canvas.drawCircle(width * 0.75f, height * 0.5f, height * 0.045f, paint)
            addCloudyShading(canvas, width, height, 0x10000000)
            return bitmap
        }

        if (striped) {
            paint.color = Color.rgb(250, 248, 240)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = color
            canvas.drawRect(0f, height * 0.255f, width.toFloat(), height * 0.745f, paint)
        } else {
            paint.color = color
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        // Two number spots, half a turn apart, so the number is visible from either side.
        for (u in floatArrayOf(0.25f, 0.75f)) {
            val cx = width * u
            val cy = height * 0.5f
            val r = height * 0.155f
            paint.color = Color.rgb(252, 251, 246)
            canvas.drawCircle(cx, cy, r, paint)
            paint.color = Color.argb(40, 0, 0, 0)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = height * 0.006f
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(24, 24, 26)
            paint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            paint.textSize = r * 1.25f
            paint.textAlign = Paint.Align.CENTER
            val label = number.toString()
            val bounds = Rect()
            paint.getTextBounds(label, 0, label.length, bounds)
            canvas.drawText(label, cx, cy + bounds.height() / 2f, paint)
        }

        addCloudyShading(canvas, width, height, 0x18000000)
        return bitmap
    }

    /** A hint of unevenness so the phong highlight has something to sit on. */
    private fun addCloudyShading(canvas: Canvas, width: Int, height: Int, tint: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            width * 0.5f, height * 0.85f, height * 0.9f,
            tint, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /** Woven cloth: flat colour with a fine weave and a soft vignette towards the rails. */
    fun feltBitmap(color: Int, size: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint()
        val random = Random(color)
        paint.strokeWidth = 1f
        for (i in 0 until size step 2) {
            val shade = (random.nextInt(14) - 7)
            paint.color = shift(color, shade)
            canvas.drawLine(0f, i.toFloat(), size.toFloat(), i.toFloat(), paint)
            paint.color = shift(color, -shade / 2)
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), paint)
        }
        return bitmap
    }

    /**
     * Polished wood with a grain that follows the rail.
     *
     * [inlay] adds the decoration the rails carry: two lines running the length of the
     * rail and a repeating figure between them, in the table's own trim colour. It is
     * what separates a table somebody built from a brown box around some green. The
     * apron under the bed asks for the same wood without it, because a band of
     * marquetry running round the side of the carcass is not a thing furniture does.
     *
     * The grain runs along U, which the renderer lines up with the length of whatever it
     * is drawing, so a rail is grained down its length whichever way round it lies.
     */
    fun woodBitmap(color: Int, size: Int = 256, trim: Int = color, inlay: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val random = Random(color * 31L + 7L)
        for (i in 0 until size) {
            val wave = sin(i * 0.19f) * 6f + sin(i * 0.051f) * 10f
            val shade = (wave + random.nextInt(8) - 4).toInt()
            paint.color = shift(color, shade)
            paint.strokeWidth = 1.4f
            canvas.drawLine(0f, i.toFloat(), size.toFloat(), i + abs(wave) * 0.02f, paint)
        }

        if (inlay) {
            val light = shift(trim, 60)
            val dark = shift(trim, -40)
            val banded = Paint(Paint.ANTI_ALIAS_FLAG)
            // Two pairs of lines, a thick dark one shadowed by a thin bright one, which
            // is how an inlay set into wood catches the light.
            for ((v, c, w) in listOf(
                Triple(0.265f, dark, 3f), Triple(0.300f, light, 2f),
                Triple(0.700f, light, 2f), Triple(0.735f, dark, 3f)
            )) {
                banded.color = c
                banded.strokeWidth = w
                canvas.drawLine(0f, v * size, size.toFloat(), v * size, banded)
            }
            // The figure between them. Drawn at both ends of the tile as well as the
            // middle so that it carries across the seam where the texture repeats.
            val cy = size * 0.5f
            val r = size * 0.115f
            for (cx in floatArrayOf(0f, size * 0.5f, size.toFloat())) {
                banded.color = light
                canvas.drawPath(diamond(cx, cy, r * 0.62f, r), banded)
                banded.color = dark
                canvas.drawPath(diamond(cx, cy, r * 0.32f, r * 0.52f), banded)
            }
            banded.color = light
            for (cx in floatArrayOf(size * 0.25f, size * 0.75f)) {
                canvas.drawCircle(cx, cy, size * 0.019f, banded)
            }
        }

        paint.shader = LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            0x30FFFFFF, 0x30000000, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bitmap
    }

    /** A lozenge on its point, [halfWidth] across and [halfHeight] tall. */
    private fun diamond(cx: Float, cy: Float, halfWidth: Float, halfHeight: Float): Path {
        val path = Path()
        path.moveTo(cx, cy - halfHeight)
        path.lineTo(cx + halfWidth, cy)
        path.lineTo(cx, cy + halfHeight)
        path.lineTo(cx - halfWidth, cy)
        path.close()
        return path
    }

    /** A soft round shadow blob dropped under every ball. */
    fun shadowBitmap(size: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Dark and tight where the ball touches the cloth, then gone quickly. The old
        // falloff was gentle the whole way out, which reads as a grey smudge under the
        // ball rather than as a ball sitting on something.
        paint.shader = RadialGradient(
            size / 2f, size / 2f, size / 2f,
            intArrayOf(0xC8000000.toInt(), 0x8A000000.toInt(), 0x30000000, 0x00000000),
            floatArrayOf(0f, 0.34f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun shift(color: Int, delta: Int): Int = Color.rgb(
        (Color.red(color) + delta).coerceIn(0, 255),
        (Color.green(color) + delta).coerceIn(0, 255),
        (Color.blue(color) + delta).coerceIn(0, 255)
    )

    /** Uploads [bitmap] and returns the GL texture handle. Call on the GL thread. */
    fun upload(bitmap: Bitmap, repeat: Boolean = false, recycle: Boolean = true): Int {
        val handles = IntArray(1)
        GLES30.glGenTextures(1, handles, 0)
        val handle = handles[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, handle)
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR
        )
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        if (recycle) bitmap.recycle()
        return handle
    }

    /** Converts an 0xAARRGGBB colour stored in the shop catalogue to an Android colour int. */
    fun colorOf(argb: Long): Int = argb.toInt()
}
