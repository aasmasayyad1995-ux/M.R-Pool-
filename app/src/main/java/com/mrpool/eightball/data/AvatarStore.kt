package com.mrpool.eightball.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * The player's profile picture: one small square PNG in the app's own storage.
 *
 * The picked photo is copied rather than remembered by URI. A URI handed over by the photo
 * picker is a temporary grant on somebody else's file — the app can lose the right to read
 * it, and the owner can delete the photo, either of which would leave the avatar as a
 * broken space. What is copied is [AvatarImage.SIZE] square, which is a few kilobytes.
 */
class AvatarStore(context: Context) {

    private val appContext = context.applicationContext
    private val file: File get() = File(appContext.filesDir, FILE_NAME)

    fun exists(): Boolean = file.exists() && file.length() > 0

    /** The saved avatar, or null when there is none or it cannot be read. */
    fun load(): Bitmap? {
        if (!exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /**
     * Copies the picture at [source] in, cropped square, turned the right way up and
     * scaled down. Returns false when it could not be read, leaving any existing avatar
     * alone rather than replacing it with nothing.
     */
    fun save(source: Uri): Boolean {
        val prepared = runCatching { decode(source) }.getOrNull() ?: return false
        val temporary = File(appContext.filesDir, "$FILE_NAME.tmp")
        return try {
            // Written beside the real file and moved into place, so a failure part way
            // through leaves the old picture rather than half of a new one.
            val written = temporary.outputStream().use {
                prepared.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            // compress returns false rather than throwing. Taking that as success wrote
            // nothing, reported a picture, and left the screen showing the plain eight
            // ball with no hint as to why.
            if (!written || temporary.length() <= 0L) {
                Log.w(TAG, "the picture could not be encoded")
                return false
            }
            // renameTo will not replace an existing file on every filesystem, and a
            // silent false there means the second picture a player chooses never appears.
            // Deleting first makes the move the same on all of them.
            file.delete()
            if (temporary.renameTo(file)) return true

            // Some devices still refuse the move. Copying is slower and always works, and
            // a picture that arrives slowly beats one that never arrives.
            temporary.copyTo(file, overwrite = true)
            file.length() > 0L
        } catch (t: Throwable) {
            Log.w(TAG, "could not write the avatar", t)
            false
        } finally {
            temporary.delete()
            prepared.recycle()
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    // ---------------------------------------------------------------------- decoding

    private fun decode(source: Uri): Bitmap? {
        // First pass reads only the size, so a forty megapixel photo is never in memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = AvatarImage.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = appContext.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val upright = turnUpright(source, decoded)
        val crop = AvatarImage.centreSquare(upright.width, upright.height)
        val square = Bitmap.createBitmap(upright, crop.x, crop.y, crop.size, crop.size)
        val scaled = Bitmap.createScaledBitmap(
            square, AvatarImage.SIZE, AvatarImage.SIZE, true
        )
        if (square !== scaled) square.recycle()
        if (upright !== decoded) decoded.recycle()
        return scaled
    }

    /** Applies the photo's EXIF orientation, which phones write instead of rotating pixels. */
    private fun turnUpright(source: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            appContext.contentResolver.openInputStream(source)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    AvatarImage.EXIF_NORMAL
                )
            } ?: AvatarImage.EXIF_NORMAL
        }.getOrDefault(AvatarImage.EXIF_NORMAL)

        val rotation = AvatarImage.rotationFor(orientation)
        val mirrored = AvatarImage.isMirrored(orientation)
        if (rotation == 0 && !mirrored) return bitmap

        val matrix = Matrix().apply {
            if (rotation != 0) postRotate(rotation.toFloat())
            if (mirrored) postScale(-1f, 1f)
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private companion object {
        const val TAG = "AvatarStore"
        const val FILE_NAME = "avatar.png"
    }
}
