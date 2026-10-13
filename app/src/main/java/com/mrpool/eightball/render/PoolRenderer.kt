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
import kotlin.math.roundToInt
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
    private lateinit var ring: Mesh
    private lateinit var cueShaft: Mesh
    private lateinit var cueButt: Mesh
    private lateinit var cueBand: Mesh

    private val ballTextures = IntArray(16)
    private var feltTexture = 0
    private var woodTexture = 0
    private var railTexture = 0
    private var shadowTexture = 0
    private var uploadedStyleKey = 0
    /** What is bound to texture unit 0, so the same bind is not sent twice. */
    private var boundTexture = -1

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
        // Getting here a second time means the EGL context was lost and remade, which is
        // what happens when the phone takes the app's graphics memory back while the
        // player is elsewhere. Every handle in these fields belongs to the context that
        // is gone. The ball textures below are rebuilt unconditionally so they survived
        // it; the cloth and the wood did not, because uploadSurfaceTextures saw its own
        // cached key still matching and skipped the work, leaving the table drawn with a
        // texture name that no longer refers to anything.
        releaseGpuResources()

        GLES30.glClearColor(0.035f, 0.05f, 0.06f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        program = ShaderProgram(Shaders.VERTEX, Shaders.FRAGMENT)

        // A ball is sixty or so pixels across on a phone. Twenty eight by forty put
        // better than two thousand triangles into that, most of them smaller than a
        // pixel, which is the one thing a mobile GPU is worst at.
        sphere = MeshBuilders.sphere(stacks = 20, slices = 32).also { it.upload() }
        unitBox = MeshBuilders.box(1f, 1f, 1f).also { it.upload() }
        disc = MeshBuilders.disc().also { it.upload() }
        quad = MeshBuilders.quadXZ().also { it.upload() }
        ring = MeshBuilders.ring().also { it.upload() }
        cueShaft = MeshBuilders.taperedCylinder(0.0062f, 0.0108f).also { it.upload() }
        cueButt = MeshBuilders.taperedCylinder(0.0108f, 0.0172f).also { it.upload() }
        // A plain cylinder of radius one, scaled to whatever ring, tip or cap needs
        // it. The tapered meshes carry their own radii, so a short length cut from
        // one comes out as a cone flaring to the width of the whole cue.
        cueBand = MeshBuilders.taperedCylinder(1f, 1f, segments = 20).also { it.upload() }

        for (number in 0..15) {
            ballTextures[number] = Textures.upload(Textures.ballBitmap(number))
        }
        shadowTexture = Textures.upload(Textures.shadowBitmap())
        uploadSurfaceTextures()
        boundTexture = -1
        lastFrameNanos = System.nanoTime()
    }

    /**
     * Drops every GPU handle this renderer is holding, and the bookkeeping that goes with
     * them, so the next upload starts from nothing.
     *
     * The deletes are belt and braces: a context that has already been destroyed took its
     * objects with it, and GL ignores a name it does not know. What actually matters is
     * the fields going back to zero, because that is what makes the cloth, the wood and
     * the texture bind cache be built again for the context that now needs them.
     */
    private fun releaseGpuResources() {
        program?.release()
        program = null

        if (::sphere.isInitialized) sphere.release()
        if (::unitBox.isInitialized) unitBox.release()
        if (::disc.isInitialized) disc.release()
        if (::quad.isInitialized) quad.release()
        if (::ring.isInitialized) ring.release()
        if (::cueShaft.isInitialized) cueShaft.release()
        if (::cueButt.isInitialized) cueButt.release()
        if (::cueBand.isInitialized) cueBand.release()

        val handles = ballTextures.toMutableList()
        handles += feltTexture
        handles += woodTexture
        handles += railTexture
        handles += shadowTexture
        val live = handles.filter { it != 0 }.toIntArray()
        if (live.isNotEmpty()) GLES30.glDeleteTextures(live.size, live, 0)

        ballTextures.fill(0)
        feltTexture = 0
        woodTexture = 0
        railTexture = 0
        shadowTexture = 0
        // Without this the cached key still matches the new style and the cloth and wood
        // are never re-made for the context that now needs them.
        uploadedStyleKey = 0
        boundTexture = -1
    }

    private fun uploadSurfaceTextures() {
        val key = (style.feltColor * 31 + style.railColor) * 31 + style.trimColor
        if (key == uploadedStyleKey && feltTexture != 0) return
        if (feltTexture != 0) {
            GLES30.glDeleteTextures(3, intArrayOf(feltTexture, woodTexture, railTexture), 0)
        }
        feltTexture = Textures.upload(Textures.feltBitmap(style.feltColor), repeat = true)
        woodTexture = Textures.upload(Textures.woodBitmap(style.railColor), repeat = true)
        railTexture = Textures.upload(
            Textures.woodBitmap(style.railColor, trim = style.trimColor, inlay = true),
            repeat = true
        )
        uploadedStyleKey = key
        // Uploading binds and then unbinds unit 0, and a freed texture name gets handed
        // straight back out, so what draw() believes is bound is no longer true.
        boundTexture = -1
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

        // Bed. Run the cloth out under the cushions and up to the rails, the way it is
        // really fitted: stopping it exactly at the playing edge leaves the ambiguous seam
        // between it and whatever is drawn beneath, which shows as a ragged border.
        identity(model)
        translate(model, 0f, 0f, 0f)
        scale(model, TableLayers.clothHalfLength(), 1f, TableLayers.clothHalfWidth())
        draw(
            shader, quad, model, felt, 1f, feltTexture,
            unlit = false, shininess = 6f, specular = 0.03f,
            uvU = 2f * TableLayers.clothHalfLength() / FELT_TILE,
            uvV = 2f * TableLayers.clothHalfWidth() / FELT_TILE
        )

        // Apron under the bed so the table reads as a solid object from a low camera.
        // It must sit strictly below the cloth: the apron is wider than the playing
        // surface, so a top face level with the cloth would be coplanar with it right
        // across the table, and the two would z-fight into a shimmering patchwork.
        identity(model)
        translate(model, 0f, TableLayers.APRON_TOP - TableLayers.APRON_HEIGHT, 0f)
        scale(model, frameHalfLength(), TableLayers.APRON_HEIGHT, frameHalfWidth())
        draw(
            shader, unitBox, model, rail, 1f, woodTexture,
            unlit = false, shininess = 18f, specular = 0.10f,
            uvU = 2f * frameHalfLength() / WOOD_TILE,
            uvV = 2f * frameHalfWidth() / WOOD_TILE
        )

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
            draw(
                shader, unitBox, model, color, 1f, feltTexture, false, 8f, 0.05f,
                uvU = 2f * depth / FELT_TILE, uvV = (z1 - z0) / FELT_TILE
            )
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
        draw(
            shader, unitBox, model, color, 1f, feltTexture, false, 8f, 0.05f,
            uvU = (x1 - x0) / FELT_TILE, uvV = 2f * depth / FELT_TILE
        )
    }

    /**
     * The wooden frame. It butts up against the outside face of the cushions rather than
     * overlapping them, so the cushion rubber stays visible from a low camera.
     */
    private fun drawRailFrame(shader: ShaderProgram) {
        val cushionOuterL = TableGeometry.HALF_LENGTH + CUSHION_DEPTH * 2f
        val cushionOuterW = TableGeometry.HALF_WIDTH + CUSHION_DEPTH * 2f

        // The long rails run the whole length and close the corners.
        drawRail(shader, 0f, cushionOuterW + RAIL_WIDTH, frameHalfLength(), 0f)
        drawRail(shader, 0f, -(cushionOuterW + RAIL_WIDTH), frameHalfLength(), 0f)
        // The short rails fit between them, turned a quarter turn.
        drawRail(shader, cushionOuterL + RAIL_WIDTH, 0f, cushionOuterW, HALF_PI)
        drawRail(shader, -(cushionOuterL + RAIL_WIDTH), 0f, cushionOuterW, HALF_PI)
    }

    /**
     * One rail, lying along its own length whichever way round it goes.
     *
     * The short rails are built long and then turned, rather than built short and wide,
     * so that the texture's U axis runs down the rail in both cases. That is what lets
     * the grain follow the rail and the inlay run along it instead of across it.
     */
    private fun drawRail(shader: ShaderProgram, x: Float, z: Float, halfLength: Float, yaw: Float) {
        val height = TableLayers.RAIL_HEIGHT
        identity(model)
        translate(model, x, height, z)
        rotateY(model, yaw)
        scale(model, halfLength, height, RAIL_WIDTH)
        // A whole number of figures, so the pattern closes instead of being cut through
        // the middle at the corner where two rails meet.
        val repeats = max(1, (2f * halfLength / RAIL_TILE).roundToInt()).toFloat()
        draw(
            shader, unitBox, model, style.railColor, 1f, railTexture,
            unlit = false, shininess = 20f, specular = 0.10f,
            uvU = repeats, uvV = 1f
        )
    }

    private fun frameHalfLength(): Float = TableLayers.frameHalfLength()

    private fun frameHalfWidth(): Float = TableLayers.frameHalfWidth()

    private fun drawPockets(shader: ShaderProgram, trim: Int) {
        for (pocket in TableGeometry.pockets) {
            identity(model)
            translate(model, pocket.center.x, TableLayers.POCKET_TRIM, pocket.center.y)
            val r = pocket.radius * 1.5f
            scale(model, r, 1f, r)
            draw(shader, disc, model, trim, 1f, 0, unlit = false, shininess = 30f, specular = 0.18f)

            identity(model)
            translate(model, pocket.center.x, TableLayers.POCKET_HOLE, pocket.center.y)
            val inner = pocket.radius * 1.12f
            scale(model, inner, 1f, inner)
            draw(shader, disc, model, 0xFF05070A.toInt(), 1f, 0, unlit = true, shininess = 1f, specular = 0f)
        }
    }

    /** The diamonds inlaid into the rails, used by players to aim bank shots. */
    private fun drawSights(shader: ShaderProgram, trim: Int) {
        val halfL = TableGeometry.HALF_LENGTH
        val halfW = TableGeometry.HALF_WIDTH
        val railY = TableLayers.RAIL_SIGHT
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
        translate(model, TableGeometry.FOOT_SPOT_X, TableLayers.MARKING, 0f)
        scale(model, 0.009f, 1f, 0.009f)
        draw(shader, disc, model, 0xFFE8E2D0.toInt(), 0.75f, 0, true, 1f, 0f)

        identity(model)
        translate(model, TableGeometry.HEAD_STRING_X, TableLayers.MARKING, 0f)
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
            // The shadow lands where the light is going, which is down and away from the
            // player. It used to be pushed the other way, into the side of the ball the
            // camera cannot see, so most of it was hidden under the ball that cast it.
            translate(
                model,
                ball.position.x + SHADOW_OFFSET_X * radius,
                TableLayers.BALL_SHADOW,
                ball.position.y + SHADOW_OFFSET_Z * radius
            )
            scale(model, radius * 1.38f, 1f, radius * 1.38f)
            draw(shader, disc, model, 0xFF000000.toInt(), 0.62f, shadowTexture, true, 1f, 0f)
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
        // The guide is flat on the cloth and is only ever looked at from above, so which
        // way round its triangles face is of no interest and getting it wrong would mean
        // a ring that is simply not there. Two calls to not have to care.
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        val maxLength = controller.guideLength()
        val toContact = preview.contactPoint - preview.from
        val drawn = min(toContact.length(), maxLength)
        // One clean line out of the cue ball. It used to be cut into dashes, which is a
        // fussier thing to read and cost a draw call per dash.
        drawSolidLine(
            shader,
            preview.from + preview.direction * TableGeometry.BALL_RADIUS,
            preview.direction,
            drawn - TableGeometry.BALL_RADIUS,
            0xFFFFFFFF.toInt(),
            0.85f,
            GUIDE_HALF_WIDTH
        )

        preview.ghostBall?.let { ghost ->
            if (ghost.distanceTo(preview.from) <= maxLength + 0.02f) {
                // Where the cue ball will be standing when it arrives, drawn as the
                // outline of a ball rather than a see-through ball. An outline sits on
                // the cloth and does not argue with whatever is behind it.
                identity(model)
                translate(model, ghost.x, TableLayers.GHOST_RING, ghost.y)
                val r = TableGeometry.BALL_RADIUS
                scale(model, r, 1f, r)
                draw(shader, ring, model, 0xFFFFFFFF.toInt(), 0.92f, 0, true, 1f, 0f)

                // And one line from the ball that is about to be hit, to wherever it
                // gets to. It starts at that ball's edge rather than its centre, so it
                // reads as the ball setting off and not as a line drawn through it.
                val objectBall = preview.objectBall
                val objectDir = preview.objectDirection
                if (objectBall != null && objectDir != null) {
                    val start = TableGeometry.BALL_RADIUS
                    val run = preview.objectTravel - start
                    if (run > 0.01f) {
                        drawSolidLine(
                            shader,
                            objectBall + objectDir * start,
                            objectDir,
                            run,
                            0xFFFFFFFF.toInt(),
                            0.85f,
                            GUIDE_HALF_WIDTH
                        )
                    }
                }
            }
        }

        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawSolidLine(
        shader: ShaderProgram,
        from: Vec2,
        direction: Vec2,
        length: Float,
        color: Int,
        alpha: Float,
        halfWidth: Float
    ) {
        if (length <= 0.005f) return
        val mid = from + direction * (length / 2f)
        identity(model)
        translate(model, mid.x, TableLayers.GUIDE_LINE, mid.y)
        rotateY(model, atan2(direction.y, direction.x))
        scale(model, length / 2f, 1f, halfWidth)
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
        drawCueBand(
            shader, tip, heading, tipHeight,
            SHAFT_LENGTH - 0.012f, 0.014f, 0.0112f, style.cueAccentColor
        )
        // The business end. The cue had no tip at all: it ran to a point in the shaft's
        // own colour, and the tip is the one part of a cue a player is looking at while
        // they line a shot up. The shop draws it on every cue in the rack.
        drawCueBand(shader, tip, heading, tipHeight, 0f, TIP_LENGTH, 0.0063f, TIP_COLOR)
        drawCueBand(
            shader, tip, heading, tipHeight,
            TIP_LENGTH, FERRULE_LENGTH, 0.0064f, FERRULE_COLOR
        )
        // And a cap on the butt, so the far end stops rather than simply running out.
        drawCueBand(
            shader, tip, heading, tipHeight,
            SHAFT_LENGTH + BUTT_LENGTH - 0.022f, 0.022f, 0.0174f, style.cueAccentColor
        )
    }

    /** A short cylinder of [radius] set into the cue: a tip, a ferrule, a ring, a cap. */
    private fun drawCueBand(
        shader: ShaderProgram,
        tip: Vec2,
        heading: Float,
        tipHeight: Float,
        startOffset: Float,
        length: Float,
        radius: Float,
        color: Int
    ) {
        identity(model)
        translate(model, tip.x, tipHeight, tip.y)
        rotateY(model, heading)
        rotateZ(model, CUE_ELEVATION)
        translate(model, startOffset, 0f, 0f)
        scale(model, length, radius, radius)
        draw(shader, cueBand, model, color, 1f, 0, unlit = false, shininess = 48f, specular = 0.45f)
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
        specular: Float,
        uvU: Float = 1f,
        uvV: Float = 1f
    ) {
        shader.setVec2("uUvScale", uvU, uvV)
        shader.setMatrix("uModel", modelMatrix)
        setNormalMatrix(modelMatrix, normalMatrix)
        shader.setMatrix3("uNormalMatrix", normalMatrix)
        shader.setVec4("uBaseColor", color.redf(), color.greenf(), color.bluef(), alpha)
        shader.setInt("uUnlit", if (unlit) 1 else 0)
        shader.setFloat("uShininess", max(shininess, 1f))
        shader.setFloat("uSpecularStrength", specular)
        if (texture != 0) {
            shader.setInt("uUseTexture", 1)
            if (texture != boundTexture) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
                boundTexture = texture
            }
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

        // How much table one tile of each texture covers, in metres. Everything used to
        // be stretched to the size of the piece it was on, so a square of cloth painted
        // with a fine nap was smeared the length of the bed and the nap came out as
        // bands a hand wide.
        private const val FELT_TILE = 0.16f
        private const val WOOD_TILE = 0.30f
        /** One figure of rail inlay every twelve centimetres. */
        private const val RAIL_TILE = 0.12f

        /** Half the thickness of the aiming lines, in metres. A ball is 57mm across. */
        private const val GUIDE_HALF_WIDTH = 0.0026f

        // Where a ball's shadow sits, in ball radii. Taken from the light direction the
        // shader is given, flattened onto the cloth: a shadow that disagrees with the
        // highlight on the ball above it is the sort of thing nobody can name and
        // everybody can see.
        private const val SHADOW_OFFSET_X = -0.255f
        private const val SHADOW_OFFSET_Z = -0.334f

        // Heights and footprints live in TableLayers, where a unit test can check that no
        // two surfaces which overlap each other ever share one.
        private const val CUSHION_DEPTH = TableLayers.CUSHION_DEPTH
        private const val CUSHION_HEIGHT = TableLayers.CUSHION_HEIGHT
        private const val RAIL_WIDTH = TableLayers.RAIL_WIDTH
        private const val CUE_ELEVATION = 0.11f
        private const val SHAFT_LENGTH = 0.78f
        private const val BUTT_LENGTH = 0.62f

        /** Leather tip and the bone coloured ferrule behind it. */
        private const val TIP_LENGTH = 0.011f
        private const val FERRULE_LENGTH = 0.011f
        private const val TIP_COLOR = 0xFF5B86C4.toInt()
        private const val FERRULE_COLOR = 0xFFF2EDE2.toInt()
    }
}
