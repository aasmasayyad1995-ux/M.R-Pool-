package com.mrpool.eightball.data

/** The part of a source image that becomes the avatar: a square, taken from the middle. */
data class Crop(val x: Int, val y: Int, val size: Int)

/**
 * The arithmetic behind turning a photo off somebody's phone into a small round avatar.
 *
 * Kept apart from the decoding so it can be tested without a device. A phone camera photo
 * is tens of megapixels; decoding one at full size to end up with 256 pixels is how an
 * innocent looking avatar picker runs a phone out of memory.
 */
object AvatarImage {

    /** The stored avatar is a square of this many pixels. */
    const val SIZE = 256

    /**
     * What to give `BitmapFactory.Options.inSampleSize`.
     *
     * It only understands powers of two, and it rounds down, so the answer is the largest
     * power of two that still leaves the shorter side at or above [target]. Overshooting
     * by one step would decode something smaller than the avatar and blur it.
     */
    fun sampleSize(width: Int, height: Int, target: Int = SIZE): Int {
        if (width <= 0 || height <= 0) return 1
        val shorter = minOf(width, height)
        var sample = 1
        while (shorter / (sample * 2) >= target) sample *= 2
        return sample
    }

    /**
     * The largest square in the middle of a [width] by [height] image.
     *
     * Middle rather than top left because faces are usually near the centre, and a
     * portrait photo cropped from the corner is mostly ceiling.
     */
    fun centreSquare(width: Int, height: Int): Crop {
        val size = minOf(width, height)
        return Crop(x = (width - size) / 2, y = (height - size) / 2, size = size)
    }

    /**
     * How far to turn an image to put it the right way up, from its EXIF orientation.
     *
     * Phones do not rotate the pixels when you turn the phone; they write down which way
     * it was held. Ignoring that is why an avatar picked from the camera roll so often
     * arrives lying on its side.
     */
    fun rotationFor(exifOrientation: Int): Int = when (exifOrientation) {
        EXIF_ROTATE_90, EXIF_TRANSPOSE -> 90
        EXIF_ROTATE_180, EXIF_FLIP_VERTICAL -> 180
        EXIF_ROTATE_270, EXIF_TRANSVERSE -> 270
        else -> 0
    }

    /** True when the image is also mirrored, which a front camera selfie often is. */
    fun isMirrored(exifOrientation: Int): Boolean = when (exifOrientation) {
        EXIF_FLIP_HORIZONTAL, EXIF_FLIP_VERTICAL, EXIF_TRANSPOSE, EXIF_TRANSVERSE -> true
        else -> false
    }

    // The EXIF orientation constants, spelled out rather than depended on, so this file
    // stays free of Android and testable off a device.
    const val EXIF_NORMAL = 1
    const val EXIF_FLIP_HORIZONTAL = 2
    const val EXIF_ROTATE_180 = 3
    const val EXIF_FLIP_VERTICAL = 4
    const val EXIF_TRANSPOSE = 5
    const val EXIF_ROTATE_90 = 6
    const val EXIF_TRANSVERSE = 7
    const val EXIF_ROTATE_270 = 8
}
