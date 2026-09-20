package com.mrpool.eightball

import com.mrpool.eightball.audio.Sound
import com.mrpool.eightball.audio.SoundSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SoundSynthTest {

    @Test
    fun `every sound renders audible, non clipping audio`() {
        for (sound in Sound.entries) {
            val pcm = SoundSynth.render(sound)
            assertTrue("$sound produced no samples", pcm.isNotEmpty())

            var peak = 0
            for (sample in pcm) {
                val magnitude = abs(sample.toInt())
                if (magnitude > peak) peak = magnitude
            }
            assertTrue("$sound is silent", peak > 4000)
            assertTrue("$sound clips at $peak", peak <= 32767)
        }
    }

    @Test
    fun `sounds start and end at silence so they do not click`() {
        for (sound in Sound.entries) {
            val pcm = SoundSynth.render(sound)
            assertTrue("$sound starts on a step: ${pcm.first()}", abs(pcm.first().toInt()) < 400)
            assertTrue("$sound ends on a step: ${pcm.last()}", abs(pcm.last().toInt()) < 400)
        }
    }

    @Test
    fun `impacts decay instead of droning on`() {
        // A pool click is over in a few tens of milliseconds; the tail must be far quieter
        // than the attack or it will sound like a tone, not a hit.
        for (sound in listOf(Sound.BALL_CLICK, Sound.BALL_KISS, Sound.CUSHION, Sound.CUE_STRIKE)) {
            val pcm = SoundSynth.render(sound)
            val attack = peakOf(pcm, 0, pcm.size / 5)
            val tail = peakOf(pcm, pcm.size * 4 / 5, pcm.size)
            assertTrue("$sound does not decay: attack $attack, tail $tail", tail < attack / 4)
        }
    }

    @Test
    fun `sound lengths are sensible`() {
        val click = SoundSynth.render(Sound.BALL_CLICK).size.toFloat() / SoundSynth.SAMPLE_RATE
        assertTrue("a ball click should be short, was ${click}s", click in 0.02f..0.12f)

        val win = SoundSynth.render(Sound.WIN).size.toFloat() / SoundSynth.SAMPLE_RATE
        assertTrue("the win jingle should be about a second, was ${win}s", win in 0.4f..1.5f)
    }

    @Test
    fun `the seed changes the noise but not the length`() {
        val a = SoundSynth.render(Sound.BALL_CLICK, seed = 1)
        val b = SoundSynth.render(Sound.BALL_CLICK, seed = 2)
        assertEquals(a.size, b.size)
        assertTrue("two seeds produced identical audio", !a.contentEquals(b))
    }

    @Test
    fun `the wav header describes the payload`() {
        val pcm = SoundSynth.render(Sound.TAP)
        val wav = SoundSynth.toWav(pcm)

        assertEquals(44 + pcm.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(1, readShort(wav, 20))               // PCM
        assertEquals(1, readShort(wav, 22))               // mono
        assertEquals(SoundSynth.SAMPLE_RATE, readInt(wav, 24))
        assertEquals(16, readShort(wav, 34))              // bits per sample
        assertEquals(pcm.size * 2, readInt(wav, 40))      // payload length

        // The first sample must survive the round trip, little endian.
        assertEquals(pcm[0].toInt(), readShort(wav, 44))
    }

    @Test
    fun `impact volume and pitch rise with speed and stay in range`() {
        val soft = SoundSynth.volumeForImpact(0.3f)
        val hard = SoundSynth.volumeForImpact(9f)
        assertTrue("a soft kiss should be quiet, was $soft", soft < 0.4f)
        assertEquals(1f, hard, 1e-4f)
        assertTrue(SoundSynth.volumeForImpact(2f) < SoundSynth.volumeForImpact(4f))

        for (speed in floatArrayOf(0f, 0.5f, 3f, 9f, 40f)) {
            val rate = SoundSynth.rateForImpact(speed)
            assertTrue("rate $rate out of SoundPool's range", rate in 0.5f..2f)
        }
        assertTrue(SoundSynth.rateForImpact(1f) < SoundSynth.rateForImpact(8f))
    }

    private fun peakOf(pcm: ShortArray, from: Int, to: Int): Int {
        var peak = 0
        for (i in from until to) {
            val magnitude = abs(pcm[i].toInt())
            if (magnitude > peak) peak = magnitude
        }
        return peak
    }

    private fun readShort(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun readInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
}
