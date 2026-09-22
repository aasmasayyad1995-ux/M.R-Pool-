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
 * What happened when a picture was saved.
 *
 * A reason rather than a plain false, because the last time this failed it did so
 * silently, and working out which of five steps had gone wrong took a guess and a round
 * trip. Whatever the player sees on the screen should be enough to say where it stopped.
 */
sealed interface AvatarResult {
    data object Saved : AvatarResult
    data class Failed(val reason: String) : AvatarResult
}

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
    fun save(source: Uri): AvatarResult {
        val prepared = when (val decoded = decode(source)) {
            is Decode.Failed -> return AvatarResult.Failed(decoded.reason)
            is Decode.Ready -> decoded.bitmap
        }
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
                return AvatarResult.Failed("the picture could not be encoded")
            }
            // renameTo will not replace an existing file on every filesystem, and a
            // silent false there means the second picture a player chooses never appears.
            // Deleting first makes the move the same on all of them.
            file.delete()
            if (temporary.renameTo(file)) return AvatarResult.Saved

            // Some devices still refuse the move. Copying is slower and always works, and
            // a picture that arrives slowly beats one that never arrives.
            temporary.copyTo(file, overwrite = true)
            if (file.length() > 0L) {
                AvatarResult.Saved
            } else {
                AvatarResult.Failed("the picture could not be written to storage")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "could not write the avatar", t)
            AvatarResult.Failed("writing the picture failed: ${t.javaClass.simpleName}")
        } finally {
            temporary.delete()
            prepared.recycle()
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    // ---------------------------------------------------------------------- decoding

    /**
     * What came back from reading the photo: a bitmap, or why there is not one.
     *
     * Its own type rather than reusing [AvatarResult]. Sharing that one meant decoding
     * could in principle return Saved, which it never does, and a `when` over it was
     * either not exhaustive or carried a branch that could not happen.
     */
    private sealed interface Decode {
        class Ready(val bitmap: Bitmap) : Decode
        class Failed(val reason: String) : Decode
    }

    private fun decode(source: Uri): Decode = runCatching { decodeOrThrow(source) }
        .getOrElse {
            Log.w(TAG, "could not read the picture", it)
            Decode.Failed("the photo could not be read: ${it.javaClass.simpleName}")
        }

    private fun decodeOrThrow(source: Uri): Decode {
        // First pass reads only the size, so a forty megapixel photo is never in memory.
        //
        // With inJustDecodeBounds set, decodeStream fills the size in and returns null BY
        // DESIGN — there is no bitmap to give back. Treating that null as a failure meant
        // giving up on the first step of every photo ever chosen, so nothing was ever
        // saved. The only thing worth failing on here is not being able to open the photo
        // at all, so the check belongs on the stream and the size, never on this decode.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val sizing = appContext.contentResolver.openInputStream(source)
            ?: return Decode.Failed("the photo could not be opened")
        sizing.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return Decode.Failed("the photo's size could not be read")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = AvatarImage.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val pixels = appContext.contentResolver.openInputStream(source)
            ?: return Decode.Failed("the photo could not be opened a second time")
        // This pass does hand back a bitmap, so here a null really is a failure.
        val decoded = pixels.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return Decode.Failed("the photo could not be decoded")

        val upright = turnUpright(source, decoded)
        val crop = AvatarImage.centreSquare(upright.width, upright.height)
        val square = Bitmap.createBitmap(upright, crop.x, crop.y, crop.size, crop.size)
        val scaled = Bitmap.createScaledBitmap(
            square, AvatarImage.SIZE, AvatarImage.SIZE, true
        )
        if (square !== scaled) square.recycle()
        if (upright !== decoded) decoded.recycle()
        return Decode.Ready(scaled)
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
