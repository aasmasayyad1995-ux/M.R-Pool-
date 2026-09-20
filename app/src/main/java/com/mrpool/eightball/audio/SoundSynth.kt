package com.mrpool.eightball.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Every noise the game makes. */
enum class Sound {
    /** The tip striking the cue ball. */
    CUE_STRIKE,

    /** A hard ball on ball click. */
    BALL_CLICK,

    /** A soft ball on ball kiss, for gentle contacts. */
    BALL_KISS,

    /** A ball thumping into a cushion. */
    CUSHION,

    /** A ball dropping into a pocket. */
    POCKET,

    /** A button press. */
    TAP,

    /** Coins spent or won. */
    COINS,

    /** Winning the game. */
    WIN,

    /** Losing the game. */
    LOSE
}

/**
 * Every sound in the game is synthesised from scratch as 16 bit PCM.
 *
 * There is deliberately no canned "break" sound: the break is loud because the physics
 * really does drive a dozen collisions through [Sound.BALL_CLICK] in a quarter of a second.
 *
 * A pool click is a struck resonator: a couple of high partials that decay in a few tens of
 * milliseconds over a very short noise transient. That is cheap to generate and sounds far
 * more like a real ball than any sample small enough to be worth shipping — and it keeps the
 * APK asset free, exactly like the textures.
 *
 * Pure Kotlin with no Android dependency, so it can be unit tested.
 */
object SoundSynth {

    const val SAMPLE_RATE = 44100

    /** Builds the PCM for [sound]. [seed] varies the noise so repeats are not identical. */
    fun render(sound: Sound, seed: Int = 0): ShortArray = when (sound) {
        Sound.CUE_STRIKE -> cueStrike(seed)
        Sound.BALL_CLICK -> ballClick(seed, hard = true)
        Sound.BALL_KISS -> ballClick(seed, hard = false)
        Sound.CUSHION -> cushion(seed)
        Sound.POCKET -> pocket(seed)
        Sound.TAP -> tap()
        Sound.COINS -> coins()
        Sound.WIN -> win()
        Sound.LOSE -> lose()
    }

    /**
     * Phenolic resin balls ring at a few kHz and die away in about 30ms. Two partials plus
     * a one millisecond noise transient is enough to fool the ear.
     */
    private fun ballClick(seed: Int, hard: Boolean): ShortArray {
        val seconds = if (hard) 0.055f else 0.040f
        val random = Random(seed + 17)
        val partials = if (hard) {
            listOf(2350f to 1.0f, 4100f to 0.55f, 6300f to 0.22f)
        } else {
            listOf(2600f to 0.8f, 4500f to 0.3f)
        }
        val decay = if (hard) 78f else 110f
        return build(seconds) { t ->
            var value = 0f
            for ((frequency, amplitude) in partials) {
                value += amplitude * sin(TWO_PI * frequency * t)
            }
            // The transient: the first millisecond of any impact is broadband.
            if (t < 0.0016f) {
                value += (random.nextFloat() * 2f - 1f) * 1.4f * (1f - t / 0.0016f)
            }
            value * exp(-decay * t)
        }
    }

    private fun cueStrike(seed: Int): ShortArray {
        val random = Random(seed + 91)
        // A leather tip is soft: lower and duller than ball on ball, with more noise in it.
        return build(0.075f) { t ->
            val tone = 0.7f * sin(TWO_PI * 720f * t) + 0.35f * sin(TWO_PI * 1150f * t)
            val noise = (random.nextFloat() * 2f - 1f) * 0.9f * exp(-220f * t)
            (tone + noise) * exp(-52f * t)
        }
    }

    /** Cloth over rubber: a low thud with the ring damped out of it. */
    private fun cushion(seed: Int): ShortArray {
        val random = Random(seed + 43)
        var lowpass = 0f
        return build(0.115f) { t ->
            val body = 0.9f * sin(TWO_PI * 176f * t) + 0.45f * sin(TWO_PI * 305f * t)
            val raw = (random.nextFloat() * 2f - 1f)
            // One pole low pass, which is what the cloth does to the noise.
            lowpass += (raw - lowpass) * 0.18f
            (body + lowpass * 1.1f * exp(-90f * t)) * exp(-34f * t)
        }
    }

    /** The drop: a couple of rattles against the jaws, then a rumble into the net. */
    private fun pocket(seed: Int): ShortArray {
        val random = Random(seed + 7)
        val rattleAt = floatArrayOf(0f, 0.045f, 0.088f)
        return build(0.44f) { t ->
            var value = 0f
            for ((index, start) in rattleAt.withIndex()) {
                if (t >= start) {
                    val local = t - start
                    val gain = 0.85f - index * 0.22f
                    value += gain * sin(TWO_PI * (1900f - index * 240f) * local) *
                        exp(-95f * local)
                }
            }
            // The ball settling in the net, an octave below the rattles.
            if (t > 0.10f) {
                val local = t - 0.10f
                val rumble = 0.55f * sin(TWO_PI * (132f - 44f * local) * local)
                val grit = (random.nextFloat() * 2f - 1f) * 0.18f
                value += (rumble + grit * exp(-24f * local)) * exp(-9.5f * local)
            }
            value
        }
    }

    private fun tap(): ShortArray = build(0.040f) { t ->
        (0.8f * sin(TWO_PI * 880f * t) + 0.25f * sin(TWO_PI * 1760f * t)) * exp(-70f * t)
    }

    /** Three rising notes, the usual language for money. */
    private fun coins(): ShortArray {
        val notes = floatArrayOf(1046.5f, 1318.5f, 1568f)
        val spacing = 0.055f
        return build(0.34f) { t ->
            var value = 0f
            for ((index, frequency) in notes.withIndex()) {
                val start = index * spacing
                if (t >= start) {
                    val local = t - start
                    value += 0.55f * (sin(TWO_PI * frequency * local) +
                        0.3f * sin(TWO_PI * frequency * 2f * local)) * exp(-11f * local)
                }
            }
            value
        }
    }

    /** A major arpeggio that lands on the octave. */
    private fun win(): ShortArray {
        val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
        val spacing = 0.10f
        return build(0.85f) { t ->
            var value = 0f
            for ((index, frequency) in notes.withIndex()) {
                val start = index * spacing
                if (t >= start) {
                    val local = t - start
                    val decay = if (index == notes.lastIndex) 3.4f else 7.5f
                    value += 0.5f * (sin(TWO_PI * frequency * local) +
                        0.35f * sin(TWO_PI * frequency * 2f * local) +
                        0.16f * sin(TWO_PI * frequency * 3f * local)) * exp(-decay * local)
                }
            }
            value * 0.75f
        }
    }

    /** Two notes falling a fourth, the mirror of the win. */
    private fun lose(): ShortArray {
        val notes = floatArrayOf(392f, 293.66f)
        val spacing = 0.16f
        return build(0.72f) { t ->
            var value = 0f
            for ((index, frequency) in notes.withIndex()) {
                val start = index * spacing
                if (t >= start) {
                    val local = t - start
                    value += 0.6f * (sin(TWO_PI * frequency * local) +
                        0.28f * sin(TWO_PI * frequency * 0.5f * local)) * exp(-4.6f * local)
                }
            }
            value * 0.8f
        }
    }

    /**
     * Runs [wave] over [seconds], normalises the result and fades the very start and end so
     * no sound begins or ends on a step, which would be heard as a click of its own.
     */
    private inline fun build(seconds: Float, wave: (Float) -> Float): ShortArray {
        val count = (seconds * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val raw = FloatArray(count)
        var peak = 0f
        for (i in 0 until count) {
            val value = wave(i.toFloat() / SAMPLE_RATE)
            raw[i] = value
            val magnitude = if (value < 0f) -value else value
            if (magnitude > peak) peak = magnitude
        }
        val scale = if (peak > 1e-6f) (PEAK_LEVEL / peak) else 0f

        val fadeIn = min(24, count / 4)
        val fadeOut = min(160, count / 3)
        val out = ShortArray(count)
        for (i in 0 until count) {
            var value = raw[i] * scale
            if (i < fadeIn) value *= i.toFloat() / fadeIn
            val fromEnd = count - 1 - i
            if (fromEnd < fadeOut) value *= fromEnd.toFloat() / fadeOut
            out[i] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    /** Leaves headroom so several sounds at once do not clip against each other. */
    private const val PEAK_LEVEL = 26000f
    private const val TWO_PI = (2.0 * PI).toFloat()

    /** Wraps PCM in a RIFF header so it can be handed to the platform as a file. */
    fun toWav(pcm: ShortArray): ByteArray {
        val dataBytes = pcm.size * 2
        val out = ByteArray(44 + dataBytes)
        var p = 0

        fun ascii(text: String) {
            for (c in text) out[p++] = c.code.toByte()
        }

        fun int32(value: Int) {
            out[p++] = (value and 0xFF).toByte()
            out[p++] = ((value shr 8) and 0xFF).toByte()
            out[p++] = ((value shr 16) and 0xFF).toByte()
            out[p++] = ((value shr 24) and 0xFF).toByte()
        }

        fun int16(value: Int) {
            out[p++] = (value and 0xFF).toByte()
            out[p++] = ((value shr 8) and 0xFF).toByte()
        }

        ascii("RIFF")
        int32(36 + dataBytes)
        ascii("WAVE")
        ascii("fmt ")
        int32(16)            // PCM header size
        int16(1)             // PCM, uncompressed
        int16(1)             // mono
        int32(SAMPLE_RATE)
        int32(SAMPLE_RATE * 2)   // byte rate: mono, 2 bytes per sample
        int16(2)             // block align
        int16(16)            // bits per sample
        ascii("data")
        int32(dataBytes)
        for (sample in pcm) {
            int16(sample.toInt())
        }
        return out
    }

    /** Maps an impact speed onto a playback volume, so a gentle roll is not a bang. */
    fun volumeForImpact(speed: Float, reference: Float = 4.5f): Float =
        (speed / reference).coerceIn(0f, 1f).pow(0.65f)

    /** Faster impacts ring slightly higher, which is what a harder hit really does. */
    fun rateForImpact(speed: Float, reference: Float = 4.5f): Float =
        (0.88f + 0.30f * (speed / reference).coerceIn(0f, 1f))
}
