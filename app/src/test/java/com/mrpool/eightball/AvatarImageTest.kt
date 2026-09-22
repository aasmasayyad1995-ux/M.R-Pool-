package com.mrpool.eightball

import com.mrpool.eightball.data.AvatarImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning a photo off a phone into a small round avatar.
 *
 * The two things worth getting right: not decoding a forty megapixel photo to end up with
 * 256 pixels, and not putting somebody's face on its side.
 */
class AvatarImageTest {

    @Test
    fun `a huge photo is decoded small enough not to hurt`() {
        // A 48MP phone camera photo.
        val sample = AvatarImage.sampleSize(8000, 6000)
        val decodedShortSide = 6000 / sample
        assertTrue(
            "decoding at 1/$sample leaves $decodedShortSide px, which is still enormous",
            decodedShortSide < AvatarImage.SIZE * 2
        )
        assertTrue(
            "but it must not go under the avatar size, or the picture is blurred",
            decodedShortSide >= AvatarImage.SIZE
        )
    }

    @Test
    fun `the sample size is always a power of two`() {
        for ((w, h) in listOf(8000 to 6000, 4032 to 3024, 1920 to 1080, 700 to 700, 300 to 900)) {
            val sample = AvatarImage.sampleSize(w, h)
            assertTrue(
                "$w x $h gave $sample, which BitmapFactory would round down anyway",
                sample > 0 && (sample and (sample - 1)) == 0
            )
        }
    }

    @Test
    fun `an image already smaller than the avatar is decoded whole`() {
        assertEquals(1, AvatarImage.sampleSize(200, 150))
        assertEquals(1, AvatarImage.sampleSize(256, 256))
        assertEquals(1, AvatarImage.sampleSize(0, 0))
        assertEquals(1, AvatarImage.sampleSize(-4, 10))
    }

    @Test
    fun `the crop is a centred square`() {
        val wide = AvatarImage.centreSquare(1000, 400)
        assertEquals(400, wide.size)
        assertEquals(300, wide.x)
        assertEquals(0, wide.y)

        val tall = AvatarImage.centreSquare(400, 1000)
        assertEquals(400, tall.size)
        assertEquals(0, tall.x)
        assertEquals(300, tall.y)

        val square = AvatarImage.centreSquare(512, 512)
        assertEquals(0, square.x)
        assertEquals(0, square.y)
        assertEquals(512, square.size)
    }

    @Test
    fun `the crop always fits inside the image`() {
        for ((w, h) in listOf(1000 to 400, 401 to 1000, 3 to 7, 1 to 1, 999 to 1000)) {
            val crop = AvatarImage.centreSquare(w, h)
            assertTrue("$w x $h: crop runs off the right", crop.x + crop.size <= w)
            assertTrue("$w x $h: crop runs off the bottom", crop.y + crop.size <= h)
            assertTrue("$w x $h: crop starts outside", crop.x >= 0 && crop.y >= 0)
        }
    }

    @Test
    fun `a photo taken sideways is turned the right way up`() {
        assertEquals(0, AvatarImage.rotationFor(AvatarImage.EXIF_NORMAL))
        assertEquals(90, AvatarImage.rotationFor(AvatarImage.EXIF_ROTATE_90))
        assertEquals(180, AvatarImage.rotationFor(AvatarImage.EXIF_ROTATE_180))
        assertEquals(270, AvatarImage.rotationFor(AvatarImage.EXIF_ROTATE_270))
    }

    @Test
    fun `an unreadable orientation leaves the picture alone`() {
        // Better a picture the right way up than one turned on a guess.
        assertEquals(0, AvatarImage.rotationFor(0))
        assertEquals(0, AvatarImage.rotationFor(-1))
        assertEquals(0, AvatarImage.rotationFor(99))
        assertFalse(AvatarImage.isMirrored(0))
    }

    @Test
    fun `a mirrored selfie is recognised as mirrored`() {
        assertTrue(AvatarImage.isMirrored(AvatarImage.EXIF_FLIP_HORIZONTAL))
        assertTrue(AvatarImage.isMirrored(AvatarImage.EXIF_TRANSPOSE))
        assertFalse(AvatarImage.isMirrored(AvatarImage.EXIF_NORMAL))
        assertFalse(AvatarImage.isMirrored(AvatarImage.EXIF_ROTATE_90))
    }
}

/** The player's name and picture, as the profile screen sets them. */
class ProfileDetailsTest {

    @Test
    fun `a new player has no picture`() {
        assertEquals(0L, com.mrpool.eightball.data.PlayerProfile().avatarStamp)
    }

    @Test
    fun `a name is trimmed and cut to what fits on a plate`() {
        val profile = com.mrpool.eightball.data.PlayerProfile()
        assertEquals("Asad", profile.withName("  Asad  ").playerName)
        assertEquals(
            com.mrpool.eightball.data.PlayerProfile.MAX_NAME,
            profile.withName("A".repeat(60)).playerName.length
        )
    }

    @Test
    fun `a blank name falls back rather than leaving an empty plate`() {
        val profile = com.mrpool.eightball.data.PlayerProfile(playerName = "Asad")
        assertEquals(
            com.mrpool.eightball.data.PlayerProfile.DEFAULT_NAME,
            profile.withName("").playerName
        )
        assertEquals(
            com.mrpool.eightball.data.PlayerProfile.DEFAULT_NAME,
            profile.withName("      ").playerName
        )
    }

    @Test
    fun `renaming changes nothing else`() {
        val before = com.mrpool.eightball.data.PlayerProfile(
            coins = 900, wins = 4, avatarStamp = 17L
        )
        val after = before.withName("Imran")
        assertEquals("Imran", after.playerName)
        assertTrue(before.copy(playerName = "Imran") == after)
    }
}
