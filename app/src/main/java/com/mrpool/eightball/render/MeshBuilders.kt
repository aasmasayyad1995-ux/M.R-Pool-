package com.mrpool.eightball.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Procedural geometry: everything on screen is generated, so the app ships with no models. */
object MeshBuilders {

    /** UV sphere of radius 1 centred on the origin, mapped so ball textures wrap correctly. */
    fun sphere(stacks: Int = 28, slices: Int = 40): Mesh {
        val vertices = ArrayList<Float>((stacks + 1) * (slices + 1) * 8)
        val indices = ArrayList<Int>(stacks * slices * 6)

        for (stack in 0..stacks) {
            val v = stack.toFloat() / stacks
            val phi = v * PI.toFloat()
            val y = cos(phi)
            val r = sin(phi)
            for (slice in 0..slices) {
                val u = slice.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                val x = r * cos(theta)
                val z = r * sin(theta)
                vertices.add(x); vertices.add(y); vertices.add(z)
                vertices.add(x); vertices.add(y); vertices.add(z)
                vertices.add(u); vertices.add(v)
            }
        }
        for (stack in 0 until stacks) {
            for (slice in 0 until slices) {
                val a = stack * (slices + 1) + slice
                val b = a + slices + 1
                indices.add(a); indices.add(b); indices.add(a + 1)
                indices.add(a + 1); indices.add(b); indices.add(b + 1)
            }
        }
        return Mesh(vertices.toFloatArray(), indices.toIntArray())
    }

    /** Axis aligned box spanning [-half] to [+half] on each axis. */
    fun box(halfX: Float, halfY: Float, halfZ: Float): Mesh {
        val v = ArrayList<Float>(6 * 4 * 8)
        val i = ArrayList<Int>(36)

        fun face(
            nx: Float, ny: Float, nz: Float,
            p0: Triple<Float, Float, Float>, p1: Triple<Float, Float, Float>,
            p2: Triple<Float, Float, Float>, p3: Triple<Float, Float, Float>
        ) {
            val base = v.size / 8
            val uvs = arrayOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            listOf(p0, p1, p2, p3).forEachIndexed { index, p ->
                v.add(p.first); v.add(p.second); v.add(p.third)
                v.add(nx); v.add(ny); v.add(nz)
                v.add(uvs[index].first); v.add(uvs[index].second)
            }
            i.add(base); i.add(base + 1); i.add(base + 2)
            i.add(base); i.add(base + 2); i.add(base + 3)
        }

        val x = halfX
        val y = halfY
        val z = halfZ
        face(0f, 1f, 0f, Triple(-x, y, z), Triple(x, y, z), Triple(x, y, -z), Triple(-x, y, -z))
        face(0f, -1f, 0f, Triple(-x, -y, -z), Triple(x, -y, -z), Triple(x, -y, z), Triple(-x, -y, z))
        face(0f, 0f, 1f, Triple(-x, -y, z), Triple(x, -y, z), Triple(x, y, z), Triple(-x, y, z))
        face(0f, 0f, -1f, Triple(x, -y, -z), Triple(-x, -y, -z), Triple(-x, y, -z), Triple(x, y, -z))
        face(1f, 0f, 0f, Triple(x, -y, z), Triple(x, -y, -z), Triple(x, y, -z), Triple(x, y, z))
        face(-1f, 0f, 0f, Triple(-x, -y, -z), Triple(-x, -y, z), Triple(-x, y, z), Triple(-x, y, -z))

        return Mesh(v.toFloatArray(), i.toIntArray())
    }

    /**
     * Tapered cylinder along +X, from x = 0 (radius [radiusStart]) to x = 1
     * (radius [radiusEnd]). Used for the cue stick.
     */
    fun taperedCylinder(radiusStart: Float, radiusEnd: Float, segments: Int = 24): Mesh {
        val v = ArrayList<Float>((segments + 1) * 2 * 8 + 16)
        val i = ArrayList<Int>(segments * 6 + segments * 6)

        for (seg in 0..segments) {
            val t = seg.toFloat() / segments
            val angle = t * 2f * PI.toFloat()
            val c = cos(angle)
            val s = sin(angle)
            // Start ring
            v.add(0f); v.add(c * radiusStart); v.add(s * radiusStart)
            v.add(0f); v.add(c); v.add(s)
            v.add(t); v.add(0f)
            // End ring
            v.add(1f); v.add(c * radiusEnd); v.add(s * radiusEnd)
            v.add(0f); v.add(c); v.add(s)
            v.add(t); v.add(1f)
        }
        for (seg in 0 until segments) {
            val a = seg * 2
            val b = a + 1
            val c = a + 2
            val d = a + 3
            i.add(a); i.add(c); i.add(b)
            i.add(b); i.add(c); i.add(d)
        }
        return Mesh(v.toFloatArray(), i.toIntArray())
    }

    /**
     * Flat disc on the XZ plane, radius 1, facing +Y. Used for pockets, shadows and the
     * spots printed on the cloth.
     */
    fun disc(segments: Int = 40): Mesh {
        val v = ArrayList<Float>((segments + 2) * 8)
        val i = ArrayList<Int>(segments * 3)
        v.add(0f); v.add(0f); v.add(0f)
        v.add(0f); v.add(1f); v.add(0f)
        v.add(0.5f); v.add(0.5f)
        for (seg in 0..segments) {
            val angle = (seg.toFloat() / segments) * 2f * PI.toFloat()
            val x = cos(angle)
            val z = sin(angle)
            v.add(x); v.add(0f); v.add(z)
            v.add(0f); v.add(1f); v.add(0f)
            v.add(x * 0.5f + 0.5f); v.add(z * 0.5f + 0.5f)
        }
        for (seg in 1..segments) {
            i.add(0); i.add(seg); i.add(seg + 1)
        }
        return Mesh(v.toFloatArray(), i.toIntArray())
    }

    /** Unit quad on the XZ plane facing +Y, spanning -1..1, used for cloth and guide lines. */
    fun quadXZ(): Mesh {
        val v = floatArrayOf(
            -1f, 0f, -1f, 0f, 1f, 0f, 0f, 0f,
            1f, 0f, -1f, 0f, 1f, 0f, 1f, 0f,
            1f, 0f, 1f, 0f, 1f, 0f, 1f, 1f,
            -1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f
        )
        val i = intArrayOf(0, 2, 1, 0, 3, 2)
        return Mesh(v, i)
    }
}
