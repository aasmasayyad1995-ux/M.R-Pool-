package com.mrpool.eightball

import com.mrpool.eightball.data.PlayerProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Muting must be undoable.
 *
 * The speaker button used to work out the new value from whatever profile its last
 * composition had captured. During a match that can be a frame or two behind, and asking
 * a stale profile for the opposite of its value hands back the value already stored — a
 * write the store discards as a no-op. The sound stayed off, the button still read muted,
 * and every further tap repeated the same arithmetic.
 */
class SoundToggleTest {

    @Test
    fun `toggling twice comes back to where it started`() {
        val on = PlayerProfile()
        assertTrue("sound should start on", on.soundEnabled)

        val muted = on.withSoundToggled()
        assertFalse(muted.soundEnabled)

        val unmuted = muted.withSoundToggled()
        assertTrue("a mute that cannot be undone is the whole bug", unmuted.soundEnabled)
    }

    @Test
    fun `a flip never depends on what the caller thought the value was`() {
        // Whatever the tap handler is holding, the flip is of the real current value.
        for (start in listOf(true, false)) {
            val current = PlayerProfile(soundEnabled = start)
            assertNotEquals(
                "flipping must always change it, however stale the caller is",
                current.soundEnabled,
                current.withSoundToggled().soundEnabled
            )
        }
    }

    @Test
    fun `flipping changes nothing else about the player`() {
        val before = PlayerProfile(
            coins = 4321,
            ownedCueIds = setOf(0, 3),
            equippedCueId = 3,
            wins = 7,
            playerName = "Asad"
        )
        val after = before.withSoundToggled()
        assertNotEquals(before.soundEnabled, after.soundEnabled)
        assertTrue(
            "muting must not touch anything the player has earned or paid for",
            before.copy(soundEnabled = after.soundEnabled) == after
        )
    }

    /**
     * The old shape, written out, so the failure is a fact rather than a claim: deriving
     * the new value from a stale snapshot and then dropping no-op writes leaves the sound
     * off for good.
     */
    @Test
    fun `working the new value out from a stale copy is what got stuck`() {
        var stored = PlayerProfile()

        fun setSoundEnabled(enabled: Boolean) {
            if (stored.soundEnabled == enabled) return   // the store's no-op guard
            stored = stored.copy(soundEnabled = enabled)
        }

        // Tap one: the handler's copy is current, so this works.
        val firstCapture = stored
        setSoundEnabled(!firstCapture.soundEnabled)
        assertFalse("muting worked", stored.soundEnabled)

        // Tap two: the handler is still holding the profile from before tap one.
        setSoundEnabled(!firstCapture.soundEnabled)
        assertFalse(
            "this is the bug: the second tap asks for mute again and is dropped",
            stored.soundEnabled
        )

        // The flip does not care what anybody captured.
        stored = stored.withSoundToggled()
        assertTrue("the fix brings it back", stored.soundEnabled)
    }
}
