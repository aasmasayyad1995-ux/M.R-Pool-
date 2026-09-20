package com.mrpool.eightball.render

import android.content.Context
import android.opengl.GLSurfaceView

/** The GL surface the game is drawn into. */
class PoolSurfaceView(context: Context, val poolRenderer: PoolRenderer) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(poolRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
