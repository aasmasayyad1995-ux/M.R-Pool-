package com.mrpool.eightball.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Plays the game's sounds.
 *
 * On first use every [Sound] is synthesised by [SoundSynth], written into the cache
 * directory as a WAV and handed to a [SoundPool], which then does the mixing, the pitch
 * shifting and the polyphony. Generation happens on a background thread; calls to [play]
 * before a sound is ready are simply dropped rather than blocking the caller.
 *
 * [play] is called from the GL thread on every impact, so it must stay cheap — which is why
 * the per sound throttle lives here rather than in the physics.
 */
class GameAudio(context: Context) : SoundPlayer {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = ConcurrentHashMap<Sound, Int>()
    private val ready = ConcurrentHashMap<Int, Boolean>()
    private val lastPlayedAt = ConcurrentHashMap<Sound, Long>()

    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var released = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready[sampleId] = true
        }
        thread(name = "mr-pool-audio-gen", isDaemon = true) { generateAll() }
    }

    private fun generateAll() {
        val directory = File(appContext.cacheDir, CACHE_DIR).apply { mkdirs() }
        for (sound in Sound.entries) {
            if (released) return
            try {
                // The version is in the name, so changing how a sound is made actually
                // changes what the player hears. Without it the file from the old build
                // is still sitting in the cache, and it is the one that gets loaded.
                val file = File(directory, "${sound.name.lowercase()}-v$SYNTH_VERSION.wav")
                if (!file.exists() || file.length() < 64) write(file, sound)
                val id = soundPool.load(file.absolutePath, 1)
                soundIds[sound] = id
            } catch (t: Throwable) {
                // A sound that cannot be built must never take the game down with it.
                Log.w(TAG, "could not prepare $sound", t)
            }
        }
        // Whatever an older build left behind is only taking up the player's storage.
        runCatching {
            directory.listFiles()?.forEach {
                if (!it.name.endsWith("-v$SYNTH_VERSION.wav")) it.delete()
            }
        }
    }

    /**
     * Writes one sound beside its real name and moves it into place.
     *
     * Writing straight to the real file means the app being killed part way through — a
     * call arriving, the player swiping the game away — leaves a half written WAV that is
     * long enough to pass the length check above. It is loaded on every launch after
     * that, fails every time, and that one sound is gone for good on that phone.
     */
    private fun write(file: File, sound: Sound) {
        val partial = File(file.parentFile, "${file.name}.part")
        try {
            partial.writeBytes(SoundSynth.toWav(SoundSynth.render(sound, sound.ordinal)))
            file.delete()
            if (!partial.renameTo(file)) partial.copyTo(file, overwrite = true)
        } finally {
            partial.delete()
        }
    }

    /**
     * Plays [sound] if it is loaded and not throttled.
     *
     * @param volume 0..1
     * @param rate playback rate, 0.5..2.0, used to vary the pitch of repeated impacts
     */
    override fun play(sound: Sound, volume: Float, rate: Float) {
        if (!enabled || released) return
        val level = volume.coerceIn(0f, 1f)
        if (level < MIN_AUDIBLE) return

        val now = System.nanoTime() / 1_000_000L
        val gap = throttleMillis(sound)
        val previous = lastPlayedAt[sound]
        if (previous != null && now - previous < gap) return

        val id = soundIds[sound] ?: return
        if (ready[id] != true) return

        lastPlayedAt[sound] = now
        try {
            soundPool.play(id, level, level, 1, 0, rate.coerceIn(0.5f, 2f))
        } catch (t: Throwable) {
            Log.w(TAG, "could not play $sound", t)
        }
    }

    /**
     * Impacts come in bursts — a break can produce twenty collisions inside two frames.
     * Without a floor on the gap between them they smear into noise and starve the stream
     * pool, so the busy sounds get one and the rare ones do not.
     */
    private fun throttleMillis(sound: Sound): Long = when (sound) {
        Sound.BALL_CLICK, Sound.BALL_KISS -> 26L
        Sound.CUSHION -> 40L
        Sound.POCKET -> 60L
        Sound.TAP -> 45L
        else -> 0L
    }

    fun release() {
        if (released) return
        released = true
        try {
            soundPool.release()
        } catch (t: Throwable) {
            Log.w(TAG, "could not release the sound pool", t)
        }
    }

    private companion object {
        const val TAG = "GameAudio"
        const val MAX_STREAMS = 10
        const val MIN_AUDIBLE = 0.04f
        const val CACHE_DIR = "mr-pool-sounds"

        /** Bump this whenever [SoundSynth] changes what it produces. */
        const val SYNTH_VERSION = 1
    }
}
