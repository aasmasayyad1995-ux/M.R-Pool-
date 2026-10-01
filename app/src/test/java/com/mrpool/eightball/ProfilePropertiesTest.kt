package com.mrpool.eightball

import com.mrpool.eightball.data.BonusDay
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.PoolTableSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import kotlin.random.Random

/**
 * Properties the saved profile has to keep, whatever order things happen in.
 *
 * Written to hunt rather than to confirm: thousands of random sequences, each checked
 * against the handful of things that must never stop being true.
 */
class ProfilePropertiesTest {

    private val names = listOf(
        "", "   ", "Asma", "  spaced  ", "a".repeat(40),
        "ਪੂਲ ਖਿਡਾਰੀ", "日本のプレイヤー",
        // One short of the cap in UTF-16 units, so the cap lands inside the last emoji.
        "a" + "😀".repeat(8),
        "🎱".repeat(12),
        "x😀y"
    )

    private fun String.hasLoneSurrogate(): Boolean {
        var i = 0
        while (i < length) {
            val c = this[i]
            if (c.isHighSurrogate()) {
                if (i + 1 >= length || !this[i + 1].isLowSurrogate()) return true
                i += 2
            } else {
                if (c.isLowSurrogate()) return true
                i++
            }
        }
        return false
    }

    @Test
    fun `whatever is done to a profile, these stay true`() {
        val random = Random(20261001)
        var profile = PlayerProfile()

        repeat(20_000) { step ->
            when (random.nextInt(6)) {
                0 -> profile = profile.withName(names.random(random))
                1 -> profile = profile.withSoundToggled()
                2 -> profile = profile.withMatchFinished()
                3 -> profile = profile.withRewardWatched(random.nextLong(-5, 5))
                4 -> profile = profile.copy(equippedCueId = random.nextInt(-3, 40))
                5 -> profile = profile.copy(equippedTableId = random.nextInt(-3, 40))
            }

            val name = profile.playerName
            assertTrue("step $step: blank name", name.isNotBlank())
            assertTrue("step $step: name too long (${name.length}): $name", name.length <= PlayerProfile.MAX_NAME)
            assertFalse(
                "step $step: name ends mid character, which goes to the opponent and into the save file: $name",
                name.hasLoneSurrogate()
            )

            assertTrue("step $step: wearing a cue it does not own", profile.owns(profile.equippedCue))
            assertTrue("step $step: playing on a table it does not own", profile.owns(profile.equippedTable))
        }
    }

    @Test
    fun `a day that is not the reward day has no rewards on it`() {
        val random = Random(7)
        repeat(5_000) {
            val day = random.nextLong(-400, 400)
            val before = PlayerProfile().withRewardWatched(day)
            assertEquals(1, before.rewardsWatchedOn(day))
            val other = day + random.nextLong(1, 50)
            assertEquals("yesterday's count must not leak into today", 0, before.rewardsWatchedOn(other))
            assertEquals(2, before.withRewardWatched(day).rewardsWatchedOn(day))
        }
    }

    @Test
    fun `the free cue and table are always owned`() {
        val bare = PlayerProfile(ownedCueIds = emptySet(), ownedTableIds = emptySet())
        assertTrue(bare.owns(CueStick.ALL.first()))
        assertTrue(bare.owns(PoolTableSkin.ALL.first()))
    }

    /**
     * The day number may never go backwards as real time goes forwards.
     *
     * It is worked out from the clock plus whatever the device's timezone offset is at
     * that moment, and that offset moves twice a year. If a day number ever repeats or
     * steps back, the daily bonus can be claimed twice; if it skips, a day is lost.
     */
    @Test
    fun `the day number only ever goes forwards`() {
        val saved = TimeZone.getDefault()
        try {
            for (zone in listOf("Asia/Kolkata", "Europe/London", "America/New_York", "Australia/Lord_Howe", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                var previous = Long.MIN_VALUE
                var t = 1_700_000_000_000L
                val end = t + 400L * 24 * 60 * 60 * 1000
                while (t < end) {
                    val day = BonusDay.local(t)
                    assertTrue("$zone went backwards at $t: $previous then $day", day >= previous)
                    previous = day
                    t += 30 * 60 * 1000L
                }
            }
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
