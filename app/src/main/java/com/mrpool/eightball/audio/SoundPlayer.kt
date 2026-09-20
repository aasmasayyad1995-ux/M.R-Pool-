package com.mrpool.eightball.audio

/**
 * Anything that can make a noise.
 *
 * The game logic depends on this rather than on [GameAudio] so that it carries no Android
 * dependency: the rules, the physics and the controller all build and run in a plain JVM
 * unit test, with a recording player standing in for the speaker.
 */
interface SoundPlayer {

    /**
     * @param volume 0..1
     * @param rate playback rate, 0.5..2.0, used to vary the pitch of repeated impacts
     */
    fun play(sound: Sound, volume: Float = 1f, rate: Float = 1f)
}
