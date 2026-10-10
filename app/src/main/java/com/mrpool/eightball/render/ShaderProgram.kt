package com.mrpool.eightball.render

import android.opengl.GLES30
import android.util.Log

/** Thin wrapper around a compiled and linked GLSL program. */
class ShaderProgram(vertexSource: String, fragmentSource: String) {

    val handle: Int = link(vertexSource, fragmentSource)

    private val uniforms = HashMap<String, Int>()
    private val attributes = HashMap<String, Int>()

    /**
     * The last value pushed for each uniform.
     *
     * Most of what a draw says is what the draw before it said: the same lighting model,
     * the same shininess, the same "no texture on this one". Sending it again is a call
     * into the driver that changes nothing, and across a hundred draws a frame there were
     * a few hundred of them. Four floats is enough room for every uniform here; a matrix
     * is compared in full, against its own copy.
     */
    private val scalarCache = HashMap<Int, FloatArray>()
    private val matrixCache = HashMap<Int, FloatArray>()

    fun use() = GLES30.glUseProgram(handle)

    fun uniform(name: String): Int = uniforms.getOrPut(name) {
        GLES30.glGetUniformLocation(handle, name)
    }

    fun attribute(name: String): Int = attributes.getOrPut(name) {
        GLES30.glGetAttribLocation(handle, name)
    }

    /** True when [values] differ from what this uniform was last given. */
    private fun changed(location: Int, vararg values: Float): Boolean {
        val previous = scalarCache[location]
        if (previous != null && previous.size == values.size) {
            var same = true
            for (i in values.indices) if (previous[i] != values[i]) { same = false; break }
            if (same) return false
            values.copyInto(previous)
        } else {
            scalarCache[location] = values.copyOf()
        }
        return true
    }

    private fun changedMatrix(location: Int, matrix: FloatArray): Boolean {
        val previous = matrixCache[location]
        if (previous != null && previous.contentEquals(matrix)) return false
        if (previous != null && previous.size == matrix.size) matrix.copyInto(previous)
        else matrixCache[location] = matrix.copyOf()
        return true
    }

    fun setMatrix(name: String, matrix: FloatArray) {
        val location = uniform(name)
        if (!changedMatrix(location, matrix)) return
        GLES30.glUniformMatrix4fv(location, 1, false, matrix, 0)
    }

    fun setMatrix3(name: String, matrix: FloatArray) {
        val location = uniform(name)
        if (!changedMatrix(location, matrix)) return
        GLES30.glUniformMatrix3fv(location, 1, false, matrix, 0)
    }

    fun setVec3(name: String, x: Float, y: Float, z: Float) {
        val location = uniform(name)
        if (!changed(location, x, y, z)) return
        GLES30.glUniform3f(location, x, y, z)
    }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val location = uniform(name)
        if (!changed(location, x, y, z, w)) return
        GLES30.glUniform4f(location, x, y, z, w)
    }

    fun setFloat(name: String, value: Float) {
        val location = uniform(name)
        if (!changed(location, value)) return
        GLES30.glUniform1f(location, value)
    }

    fun setInt(name: String, value: Int) {
        val location = uniform(name)
        if (!changed(location, value.toFloat())) return
        GLES30.glUniform1i(location, value)
    }

    fun release() {
        GLES30.glDeleteProgram(handle)
    }

    private fun link(vertexSource: String, fragmentSource: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Could not link program: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e("ShaderProgram", "Shader compile failed: $log")
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader: $log")
        }
        return shader
    }
}

/** The single lit shader every piece of table furniture is drawn with. */
object Shaders {

    const val VERTEX = """#version 300 es
        precision highp float;

        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;

        uniform mat4 uModel;
        uniform mat4 uViewProjection;
        uniform mat3 uNormalMatrix;

        out vec3 vWorldPos;
        out vec3 vNormal;
        out vec2 vTexCoord;

        void main() {
            vec4 world = uModel * vec4(aPosition, 1.0);
            vWorldPos = world.xyz;
            vNormal = normalize(uNormalMatrix * aNormal);
            vTexCoord = aTexCoord;
            gl_Position = uViewProjection * world;
        }
    """

    const val FRAGMENT = """#version 300 es
        precision highp float;

        in vec3 vWorldPos;
        in vec3 vNormal;
        in vec2 vTexCoord;

        uniform vec4 uBaseColor;
        uniform sampler2D uTexture;
        uniform int uUseTexture;
        uniform int uUnlit;
        uniform vec3 uLightDir;
        uniform vec3 uCameraPos;
        uniform float uShininess;
        uniform float uSpecularStrength;

        out vec4 fragColor;

        void main() {
            vec4 base = uBaseColor;
            if (uUseTexture == 1) {
                base *= texture(uTexture, vTexCoord);
            }
            if (uUnlit == 1) {
                fragColor = base;
                return;
            }

            vec3 n = normalize(vNormal);
            vec3 l = normalize(-uLightDir);
            vec3 v = normalize(uCameraPos - vWorldPos);
            vec3 h = normalize(l + v);

            float diffuse = max(dot(n, l), 0.0);
            // A soft fill light from the opposite side keeps the far side of balls readable.
            float fill = max(dot(n, normalize(vec3(-l.x, 0.35, -l.z))), 0.0) * 0.22;
            float specular = pow(max(dot(n, h), 0.0), uShininess) * uSpecularStrength;

            vec3 color = base.rgb * (0.30 + 0.78 * diffuse + fill) + vec3(specular);
            fragColor = vec4(color, base.a);
        }
    """
}
