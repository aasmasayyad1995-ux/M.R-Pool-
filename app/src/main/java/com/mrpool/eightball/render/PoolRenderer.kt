package com.mrpool.eightball.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.mrpool.eightball.game.GameController
import com.mrpool.eightball.game.TableGeometry
import com.mrpool.eightball.game.Vec2
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the whole 3D scene: cloth, rails, pockets, fifteen numbered balls with their
 * shadows, the cue stick and the aiming guide.
 *
 * Table space (x along the length, y across the width) maps onto world XZ, with world Y up.
 */
class PoolRenderer(
    private val controller: GameController,
    style: SceneStyle
) : GLSurfaceView.Renderer {

    @Volatile
    var style: SceneStyle = style

    /** Camera orbit, controlled by dragging with two fingers. */
    @Volatile
    private var yaw: Float = -HALF_PI
    @Volatile
    private var pitch: Float = DEFAULT_PITCH
    @Volatile
    private var distance: Float = DEFAULT_DISTANCE
    @Volatile
    private var followCueBall: Boolean = true

    private var program: ShaderProgram? = null
    private lateinit var sphere: Mesh
    private lateinit var unitBox: Mesh
    private lateinit var disc: Mesh
    private lateinit var quad: Mesh
    private lateinit var cueShaft: Mesh
    private lateinit var cueButt: Mesh

    private val ballTextures = IntArray(16)
    private var feltTexture = 0
    private var woodTexture = 0
    private var shadowTexture = 0
    private var uploadedStyleKey = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val scratchA = FloatArray(16)
    private val scratchB = FloatArray(16)
    private val normalMatrix = FloatArray(9)

    @Volatile
    private var inverseViewProjection: FloatArray? = null
    @Volatile
    private var viewportWidth = 1
    @Volatile
    private var viewportHeight = 1

    private var cameraX = 0f
    private var cameraY = 2f
    private var cameraZ = 2f
    private var targetX = 0f
    private var targetZ = 0f

    private var lastFrameNanos = 0L

    // ------------------------------------------------------------------ GL lifecycle

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.035f, 0.05f, 0.06f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        program = ShaderProgram(Shaders.VERTEX, Shaders.FRAGMENT)

        sphere = MeshBuilders.sphere().also { it.upload() }
        unitBox = MeshBuilders.box(1f, 1f, 1f).also { it.upload() }
        disc = MeshBuilders.disc().also { it.upload() }
        quad = MeshBuilders.quadXZ().also { it.upload() }
        cueShaft = MeshBuilders.taperedCylinder(0.0062f, 0.0108f).also { it.upload() }
        cueButt = MeshBuilders.taperedCylinder(0.0108f, 0.0172f).also { it.upload() }

        for (number in 0..15) {
            ballTextures[number] = Textures.upload(Textures.ballBitmap(number))
        }
        shadowTexture = Textures.upload(Textures.shadowBitmap())
        uploadSurfaceTextures()
        lastFrameNanos = System.nanoTime()
    }

    private fun uploadSurfaceTextures() {
        val key = style.feltColor * 31 + style.railColor
        if (key == uploadedStyleKey && feltTexture != 0) return
        if (feltTexture != 0) GLES30.glDeleteTextures(2, intArrayOf(feltTexture, woodTexture), 0)
        feltTexture = Textures.upload(Textures.feltBitmap(style.feltColor), repeat = true)
        woodTexture = Textures.upload(Textures.woodBitmap(style.railColor), repeat = true)
        uploadedStyleKey = key
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        val aspect = width.toFloat() / max(height, 1).toFloat()
        // A narrow field of view flattens the perspective. A wide one makes the near rail
        // loom over the far one, which looks dramatic and makes angles hard to read.
        Matrix.perspectiveM(projection, 0, FIELD_OF_VIEW, aspect, 0.05f, 40f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastFrameNanos = now

        controller.update(dt)
        uploadSurfaceTextures()
        updateCamera(dt)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val shader = program ?: return
        shader.use()
        shader.setMatrix("uViewProjection", viewProjection)
        shader.setVec3("uLightDir", -0.32f, -1f, -0.42f)
        shader.setVec3("uCameraPos", cameraX, cameraY, cameraZ)
        shader.setInt("uTexture", 0)

        drawTable(shader)
        drawBalls(shader)
        drawAimAssist(shader)
        drawCueStick(shader)
    }

    // --------------------------------------------------------------------- camera

    private fun updateCamera(dt: Float) {
        val cueBall = controller.session.physics.cueBall
        val desiredX: Float
        val desiredZ: Float
        if (followCueBall && cueBall != null && !cueBall.pocketed) {
            // Drift a little towards the action without ever losing the whole table.
            desiredX = cueBall.position.x * 0.32f
            desiredZ = cueBall.position.y * 0.32f
        } else {
            desiredX = 0f
            desiredZ = 0f
        }
        val lerp = (dt * 3.2f).coerceIn(0f, 1f)
        targetX += (desiredX - targetX) * lerp
        targetZ += (desiredZ - targetZ) * lerp

        val horizontal = distance * cos(pitch)
        cameraX = targetX + horizontal * cos(yaw)
        cameraZ = targetZ + horizontal * sin(yaw)
        cameraY = distance * sin(pitch) + 0.12f

        Matrix.setLookAtM(
            view, 0,
            cameraX, cameraY, cameraZ,
            targetX, 0.02f, targetZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        val inverse = FloatArray(16)
        if (Matrix.invertM(inverse, 0, viewProjection, 0)) {
            inverseViewProjection = inverse
        }
    }

    /** Orbit the camera. Both values are in radians. */
    fun orbit(deltaYaw: Float, deltaPitch: Float) {
        yaw += deltaYaw
        // The upper limit stops just short of straight down, where the view would flip.
        pitch = (pitch + deltaPitch).coerceIn(0.30f, 1.52f)
    }

    /** Pinch zoom, [factor] > 1 moves the camera away. */
    fun zoom(factor: Float) {
        distance = (distance * factor).coerceIn(0.85f, 4.2f)
    }

    /** Drops the camera behind the cue ball, looking down the shot. */
    fun snapBehindCue() {
        val cueBall = controller.session.physics.cueBall ?: return
        val dir = Vec2.fromAngle(controller.aimAngle)
        val eye = cueBall.position - dir * 0.6f
        yaw = atan2(eye.y - targetZ, eye.x - targetX)
        pitch = 0.42f
        distance = 1.25f
    }

    /** Back to the wide table view. */
    fun snapOverhead() {
        pitch = DEFAULT_PITCH
        distance = DEFAULT_DISTANCE
        yaw = -HALF_PI
    }

    val cameraYaw: Float get() = yaw

    /**
     * Converts a touch into a point on the table plane at ball centre height.
     * Returns null before the first frame has been drawn.
     */
    fun unproject(screenX: Float, screenY: Float): Vec2? {
        val inverse = inverseViewProjection ?: return null
        val ndcX = (2f * screenX / viewportWidth) - 1f
        val ndcY = 1f - (2f * screenY / viewportHeight)

        val near = FloatArray(4)
        val far = FloatArray(4)
        Matrix.multiplyMV(near, 0, inverse, 0, floatArrayOf(ndcX, ndcY, -1f, 1f), 0)
        Matrix.multiplyMV(far, 0, inverse, 0, floatArrayOf(ndcX, ndcY, 1f, 1f), 0)
        if (abs(near[3]) < 1e-6f || abs(far[3]) < 1e-6f) return null
        val nx = near[0] / near[3]
        val ny = near[1] / near[3]
        val nz = near[2] / near[3]
        val fx = far[0] / far[3]
        val fy = far[1] / far[3]
        val fz = far[2] / far[3]

        val planeY = TableGeometry.BALL_RADIUS
        val dy = fy - ny
        if (abs(dy) < 1e-6f) return null
        val t = (planeY - ny) / dy
        if (t < 0f) return null
        return Vec2(nx + (fx - nx) * t, nz + (fz - nz) * t)
    }

    // ---------------------------------------------------------------------- drawing

    private fun drawTable(shader: ShaderProgram) {
        val felt = style.feltColor
        val rail = style.railColor
        val trim = style.trimColor

        // Bed.
        identity(model)
        translate(model, 0f, 0f, 0f)
        scale(model, TableGeometry.HALF_LENGTH, 1f, TableGeometry.HALF_WIDTH)
        draw(shader, quad, model, felt, 1f, feltTexture, unlit = false, shininess = 6f, specular = 0.03f)

        // Apron under the bed so the table reads as a solid object from a low camera.
        // It must sit strictly below the cloth: the apron is wider than the playing
        // surface, so a top face level with the cloth would be coplanar with it right
        // across the table, and the two would z-fight into a shimmering patchwork.
        identity(model)
        translate(model, 0f, -APRON_HEIGHT - APRON_GAP, 0f)
        scale(model, frameHalfLength(), APRON_HEIGHT, frameHalfWidth())
        draw(shader, unitBox, model, rail, 1f, woodTexture, unlit = false, shininess = 18f, specular = 0.10f)

        drawCushions(shader)
        drawRailFrame(shader)
        drawPockets(shader, trim)
        drawSights(shader, trim)
        drawSpots(shader)
    }

    /** Cushion segments, cut away where the pocket mouths are. */
    private fun drawCushions(shader: ShaderProgram) {
        val halfL = TableGeometry.HALF_LENGTH
        val halfW = TableGeometry.HALF_WIDTH
        val corner = TableGeometry.CORNER_MOUTH
        val side = TableGeometry.SIDE_MOUTH
        val depth = CUSHION_DEPTH
        val height = CUSHION_HEIGHT
        val color = darken(style.feltColor, 0.82f)

        // Long rails, split either side of the middle pockets.
        for (sign in intArrayOf(-1, 1)) {
            val z = sign * (halfW + depth)
            drawSegment(shader, -halfL + corner, -side, z, depth, height, color)
            drawSegment(shader, side, halfL - corner, z, depth, height, color)
        }

        // Short rails.
        for (sign in intArrayOf(-1, 1)) {
            val x = sign * (halfL + depth)
            val z0 = -halfW + corner
            val z1 = halfW - corner
            identity(model)
            translate(model, x, height, (z0 + z1) / 2f)
            scale(model, depth, height, (z1 - z0) / 2f)
            draw(shader, unitBox, model, color, 1f, feltTexture, false, 8f, 0.05f)
        }
    }

    private fun drawSegment(
        shader: ShaderProgram,
        x0: Float,
        x1: Float,
        z: Float,
        depth: Float,
        height: Float,
        color: Int
    ) {
        identity(model)
        translate(model, (x0 + x1) / 2f, height, z)
        scale(model, (x1 - x0) / 2f, height, depth)
        draw(shader, unitBox, model, color, 1f, feltTexture, false, 8f, 0.05f)
    }

    /** Axis aligned wooden box, sized in half extents. */
    private fun drawBox(
        shader: ShaderProgram,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        color: Int, unlit: Boolean
    ) {
        if (sx <= 0f || sy <= 0f || sz <= 0f) return
        identity(model)
        translate(model, x, y, z)
        scale(model, sx, sy, sz)
        draw(shader, unitBox, model, color, 1f, woodTexture, unlit, 20f, 0.1f)
    }

    /**
     * The wooden frame. It butts up against the outside face of the cushions rather than
     * overlapping them, so the cushion rubber stays visible from a low camera.
     */
    private fun drawRailFrame(shader: ShaderProgram) {
        val cushionOuterL = TableGeometry.HALF_LENGTH + CUSHION_DEPTH * 2f
        val cushionOuterW = TableGeometry.HALF_WIDTH + CUSHION_DEPTH * 2f
        val height = 0.042f
        val color = style.railColor

        // The long rails run the whole length and close the corners.
        drawBox(
            shader, 0f, height, cushionOuterW + RAIL_WIDTH,
            frameHalfLength(), height, RAIL_WIDTH, color, false
        )
        drawBox(
            shader, 0f, height, -(cushionOuterW + RAIL_WIDTH),
            frameHalfLength(), height, RAIL_WIDTH, color, false
        )
        // The short rails fit between them.
        drawBox(
            shader, cushionOuterL + RAIL_WIDTH, height, 0f,
            RAIL_WIDTH, height, cushionOuterW, color, false
        )
        drawBox(
            shader, -(cushionOuterL + RAIL_WIDTH), height, 0f,
            RAIL_WIDTH, height, cushionOuterW, color, false
        )
    }

    private fun frameHalfLength(): Float =
        TableGeometry.HALF_LENGTH + CUSHION_DEPTH * 2f + RAIL_WIDTH * 2f

    private fun frameHalfWidth(): Float =
        TableGeometry.HALF_WIDTH + CUSHION_DEPTH * 2f + RAIL_WIDTH * 2f

    private fun drawPockets(shader: ShaderProgram, trim: Int) {
        for (pocket in TableGeometry.pockets) {
            identity(model)
            translate(model, pocket.center.x, 0.0035f, pocket.center.y)
            val r = pocket.radius * 1.5f
            scale(model, r, 1f, r)
            draw(shader, disc, model, trim, 1f, 0, unlit = false, shininess = 30f, specular = 0.18f)

            identity(model)
            translate(model, pocket.center.x, 0.0055f, pocket.center.y)
            val inner = pocket.radius * 1.12f
            scale(model, inner, 1f, inner)
            draw(shader, disc, model, 0xFF05070A.toInt(), 1f, 0, unlit = true, shininess = 1f, specular = 0f)
        }
    }

    /** The diamonds inlaid into the rails, used by players to aim bank shots. */
    private fun drawSights(shader: ShaderProgram, trim: Int) {
        val halfL = TableGeometry.HALF_LENGTH
        val halfW = TableGeometry.HALF_WIDTH
        val railY = CUSHION_HEIGHT * 2f + 0.042f * 2f + 0.0005f
        val radius = 0.0075f
        val zOffset = halfW + CUSHION_DEPTH * 2f + RAIL_WIDTH
        val xOffset = halfL + CUSHION_DEPTH * 2f + RAIL_WIDTH
        val color = brighten(trim, 1.45f)

        // Eight diamonds along each long rail, skipping the pockets at 0, 4 and 8.
        for (i in 1..7) {
            if (i == 4) continue
            val x = -halfL + (halfL * 2f) * (i / 8f)
            for (sign in intArrayOf(-1, 1)) {
                drawSight(shader, x, railY, sign * zOffset, radius, color)
            }
        }
        // Four along each short rail, skipping the corners.
        for (i in 1..3) {
            val z = -halfW + (halfW * 2f) * (i / 4f)
            for (sign in intArrayOf(-1, 1)) {
                drawSight(shader, sign * xOffset, railY, z, radius, color)
            }
        }
    }

    private fun drawSight(
        shader: ShaderProgram,
        x: Float,
        y: Float,
        z: Float,
        radius: Float,
        color: Int
    ) {
        identity(model)
        translate(model, x, y, z)
        scale(model, radius, 1f, radius)
        draw(shader, disc, model, color, 1f, 0, unlit = true, shininess = 1f, specular = 0f)
    }

    /** Foot spot and head string, printed on the cloth. */
    private fun drawSpots(shader: ShaderProgram) {
        GLES30.glEnable(GLES30.GL_BLEND)
        identity(model)
        translate(model, TableGeometry.FOOT_SPOT_X, MARKING_HEIGHT, 0f)
        scale(model, 0.009f, 1f, 0.009f)
        draw(shader, disc, model, 0xFFE8E2D0.toInt(), 0.75f, 0, true, 1f, 0f)

        identity(model)
        translate(model, TableGeometry.HEAD_STRING_X, MARKING_HEIGHT, 0f)
        scale(model, 0.0016f, 1f, TableGeometry.HALF_WIDTH)
        draw(shader, quad, model, 0xFFE8E2D0.toInt(), 0.28f, 0, true, 1f, 0f)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawBalls(shader: ShaderProgram) {
        val radius = TableGeometry.BALL_RADIUS

        // Shadows first, so they never z-fight with the balls above them.
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)
        for (ball in controller.session.physics.balls) {
            if (ball.pocketed) continue
            identity(model)
            translate(model, ball.position.x, 0.0012f, ball.position.y + radius * 0.35f)
            scale(model, radius * 1.45f, 1f, radius * 1.45f)
            draw(shader, disc, model, 0xFF000000.toInt(), 0.55f, shadowTexture, true, 1f, 0f)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        for (ball in controller.session.physics.balls) {
            if (ball.pocketed) continue
            identity(model)
            translate(model, ball.position.x, radius, ball.position.y)
            applyBallOrientation(model, ball.orientation)
            scale(model, radius, radius, radius)
            draw(
                shader, sphere, model, 0xFFFFFFFF.toInt(), 1f,
                ballTextures[ball.number.coerceIn(0, 15)],
                unlit = false, shininess = 42f, specular = 0.55f
            )
        }

        // The ghost cue ball the player is dragging during ball in hand.
        controller.ballInHandGhost?.let { ghost ->
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glDepthMask(false)
            identity(model)
            translate(model, ghost.x, radius, ghost.y)
            scale(model, radius, radius, radius)
            draw(shader, sphere, model, 0xFFFFFFFF.toInt(), 0.45f, ballTextures[0], false, 20f, 0.2f)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    private fun drawAimAssist(shader: ShaderProgram) {
        if (!controller.canPlayerAct() && !controller.uiState.value.ballInHand) return
        val preview = controller.aimPreview() ?: return

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)

        val maxLength = controller.guideLength()
        val toContact = preview.contactPoint - preview.from
        val drawn = min(toContact.length(), maxLength)
        drawDashedLine(shader, preview.from, preview.direction, drawn, 0xFFFFFFFF.toInt(), 0.75f)

        preview.ghostBall?.let { ghost ->
            if (ghost.distanceTo(preview.from) <= maxLength + 0.02f) {
                identity(model)
                translate(model, ghost.x, TableGeometry.BALL_RADIUS, ghost.y)
                val r = TableGeometry.BALL_RADIUS * 1.01f
                scale(model, r, r, r)
                draw(shader, sphere, model, 0xFFFFFFFF.toInt(), 0.24f, 0, true, 1f, 0f)

                preview.objectDirection?.let { dir ->
                    drawSolidLine(shader, ghost, dir, 0.30f, 0xFFFFD34D.toInt(), 0.85f)
                }
                preview.cueBallAfter?.let { after ->
                    val dir = (after - ghost).normalized()
                    drawSolidLine(shader, ghost, dir, 0.22f, 0xFF7FF0FF.toInt(), 0.6f)
                }
            }
        }

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawDashedLine(
        shader: ShaderProgram,
        from: Vec2,
        direction: Vec2,
        length: Float,
        color: Int,
        alpha: Float
    ) {
        if (length <= 0.01f) return
        val dash = 0.032f
        val gap = 0.022f
        var travelled = TableGeometry.BALL_RADIUS
        while (travelled < length) {
            val segment = min(dash, length - travelled)
            val mid = from + direction * (travelled + segment / 2f)
            identity(model)
            translate(model, mid.x, 0.0022f, mid.y)
            rotateY(model, atan2(direction.y, direction.x))
            scale(model, segment / 2f, 1f, 0.0032f)
            draw(shader, quad, model, color, alpha, 0, true, 1f, 0f)
            travelled += dash + gap
        }
    }

    private fun drawSolidLine(
        shader: ShaderProgram,
        from: Vec2,
        direction: Vec2,
        length: Float,
        color: Int,
        alpha: Float
    ) {
        val mid = from + direction * (length / 2f)
        identity(model)
        translate(model, mid.x, 0.0024f, mid.y)
        rotateY(model, atan2(direction.y, direction.x))
        scale(model, length / 2f, 1f, 0.0028f)
        draw(shader, quad, model, color, alpha, 0, true, 1f, 0f)
    }

    private fun drawCueStick(shader: ShaderProgram) {
        if (controller.cueHidden) return
        val state = controller.uiState.value
        if (state.shotInProgress && controller.cuePullback <= 0.001f) return
        if (state.phase == com.mrpool.eightball.game.GamePhase.GAME_OVER) return
        if (state.ballInHand && controller.ballInHandGhost == null) return
        val cueBall = controller.session.physics.cueBall ?: return
        if (cueBall.pocketed) return

        val dir = Vec2.fromAngle(controller.aimAngle)
        val origin = controller.ballInHandGhost ?: cueBall.position
        val tip = origin - dir * (TableGeometry.BALL_RADIUS + controller.cuePullback)
        // Rotate so the cylinder's +X axis points back down the shot line.
        val heading = atan2(dir.y, -dir.x)
        val tipHeight = TableGeometry.BALL_RADIUS + spinHeightOffset()

        drawCuePiece(shader, cueShaft, tip, heading, tipHeight, 0f, SHAFT_LENGTH, style.cueShaftColor)
        drawCuePiece(
            shader, cueButt, tip, heading, tipHeight, SHAFT_LENGTH, BUTT_LENGTH, style.cueButtColor
        )
        // Collar between the two, in the cue's accent colour.
        drawCuePiece(
            shader, cueShaft, tip, heading, tipHeight,
            SHAFT_LENGTH - 0.012f, 0.014f, style.cueAccentColor
        )
    }

    /** Raising or lowering the tip shows the player which spin they dialled in. */
    private fun spinHeightOffset(): Float = controller.spin.y * TableGeometry.BALL_RADIUS * 0.55f

    private fun drawCuePiece(
        shader: ShaderProgram,
        mesh: Mesh,
        tip: Vec2,
        heading: Float,
        tipHeight: Float,
        startOffset: Float,
        length: Float,
        color: Int
    ) {
        identity(model)
        translate(model, tip.x, tipHeight, tip.y)
        rotateY(model, heading)
        rotateZ(model, CUE_ELEVATION)
        translate(model, startOffset, 0f, 0f)
        scale(model, length, 1f, 1f)
        draw(shader, mesh, model, color, 1f, 0, unlit = false, shininess = 48f, specular = 0.45f)
    }

    // ------------------------------------------------------------------ draw helpers

    private fun draw(
        shader: ShaderProgram,
        mesh: Mesh,
        modelMatrix: FloatArray,
        color: Int,
        alpha: Float,
        texture: Int,
        unlit: Boolean,
        shininess: Float,
        specular: Float
    ) {
        shader.setMatrix("uModel", modelMatrix)
        setNormalMatrix(modelMatrix, normalMatrix)
        shader.setMatrix3("uNormalMatrix", normalMatrix)
        shader.setVec4("uBaseColor", color.redf(), color.greenf(), color.bluef(), alpha)
        shader.setInt("uUnlit", if (unlit) 1 else 0)
        shader.setFloat("uShininess", max(shininess, 1f))
        shader.setFloat("uSpecularStrength", specular)
        if (texture != 0) {
            shader.setInt("uUseTexture", 1)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        } else {
            shader.setInt("uUseTexture", 0)
        }
        mesh.draw()
    }

    private fun identity(m: FloatArray) = Matrix.setIdentityM(m, 0)

    private fun translate(m: FloatArray, x: Float, y: Float, z: Float) =
        Matrix.translateM(m, 0, x, y, z)

    private fun scale(m: FloatArray, x: Float, y: Float, z: Float) =
        Matrix.scaleM(m, 0, x, y, z)

    private fun rotateY(m: FloatArray, radians: Float) =
        Matrix.rotateM(m, 0, radians * RAD_TO_DEG, 0f, 1f, 0f)

    private fun rotateZ(m: FloatArray, radians: Float) =
        Matrix.rotateM(m, 0, radians * RAD_TO_DEG, 0f, 0f, 1f)

    /**
     * Applies a ball's accumulated roll. Physics tracks orientation with Z up, the renderer
     * with Y up, so the matrix is re-based through the axis swap before it is used.
     */
    private fun applyBallOrientation(m: FloatArray, orientation: FloatArray) {
        val perm = intArrayOf(0, 2, 1)
        Matrix.setIdentityM(scratchA, 0)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                // Column major destination, row major source.
                scratchA[col * 4 + row] = orientation[perm[row] * 3 + perm[col]]
            }
        }
        Matrix.multiplyMM(scratchB, 0, m, 0, scratchA, 0)
        scratchB.copyInto(m, 0, 0, 16)
    }

    private fun setNormalMatrix(modelMatrix: FloatArray, out: FloatArray) {
        for (col in 0 until 3) {
            var x = modelMatrix[col * 4]
            var y = modelMatrix[col * 4 + 1]
            var z = modelMatrix[col * 4 + 2]
            val length = kotlin.math.sqrt(x * x + y * y + z * z)
            if (length > 1e-6f) {
                x /= length
                y /= length
                z /= length
            }
            out[col * 3] = x
            out[col * 3 + 1] = y
            out[col * 3 + 2] = z
        }
    }

    private fun darken(color: Int, factor: Float): Int = android.graphics.Color.rgb(
        (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255),
        (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255),
        (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    /** Same maths as [darken] with a factor above one. */
    private fun brighten(color: Int, factor: Float): Int = darken(color, factor)

    companion object {
        private const val HALF_PI = (Math.PI / 2.0).toFloat()
        private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
        /** Nearly overhead: enough tilt to read as 3D, flat enough to judge an angle by eye. */
        private const val DEFAULT_PITCH = 1.34f
        private const val DEFAULT_DISTANCE = 2.36f
        private const val FIELD_OF_VIEW = 32f

        /** Depth of the table body, and how far its top sits below the cloth. */
        private const val APRON_HEIGHT = 0.045f
        private const val APRON_GAP = 0.006f

        /**
         * Spots and lines printed on the cloth. Every flat thing drawn on the bed gets its
         * own height — shadows at 1.2mm, these at 1.8mm, the aiming guide above them — so
         * that no two of them ever share a plane where they cross.
         */
        private const val MARKING_HEIGHT = 0.0018f

        private const val CUSHION_DEPTH = 0.048f
        private const val CUSHION_HEIGHT = 0.019f
        private const val RAIL_WIDTH = 0.062f
        private const val CUE_ELEVATION = 0.11f
        private const val SHAFT_LENGTH = 0.78f
        private const val BUTT_LENGTH = 0.62f
    }
}
