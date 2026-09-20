package com.mrpool.eightball.render

import android.graphics.Color
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PoolTableSkin

/** Colours pulled out of the shop catalogue and handed to the renderer. */
data class SceneStyle(
    val feltColor: Int,
    val railColor: Int,
    val trimColor: Int,
    val cueButtColor: Int,
    val cueShaftColor: Int,
    val cueAccentColor: Int
) {
    companion object {
        fun from(table: PoolTableSkin, cue: CueStick): SceneStyle = SceneStyle(
            feltColor = Textures.colorOf(table.feltColor),
            railColor = Textures.colorOf(table.railColor),
            trimColor = Textures.colorOf(table.trimColor),
            cueButtColor = Textures.colorOf(cue.buttColor),
            cueShaftColor = Textures.colorOf(cue.shaftColor),
            cueAccentColor = Textures.colorOf(cue.accentColor)
        )

        val DEFAULT = from(PoolTableSkin.ALL.first(), CueStick.ALL.first())
    }
}

internal fun Int.redf(): Float = Color.red(this) / 255f
internal fun Int.greenf(): Float = Color.green(this) / 255f
internal fun Int.bluef(): Float = Color.blue(this) / 255f
