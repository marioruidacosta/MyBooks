package com.mcosta.mybooks.data.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

object AndroidPhotoShrinker : PhotoShrinker {
    private const val QUALITY = 85

    override fun shrink(photo: ByteArray, maxSide: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(photo, 0, photo.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        val sampled = requireNotNull(BitmapFactory.decodeByteArray(photo, 0, photo.size, options)) { "Couldn't read the photo" }

        val scale = minOf(1f, maxSide.toFloat() / max(sampled.width, sampled.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationDegrees(photo))
        }
        val result = if (scale == 1f && matrix.isIdentity) {
            sampled
        } else {
            Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
        }
        return ByteArrayOutputStream().use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
    }

    /** Largest power of two that still leaves the longer side at least [maxSide] (cheap first reduction). */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var size = 1
        while (max(width, height) / (size * 2) >= maxSide) size *= 2
        return size
    }

    /** Cameras often save photos sideways with an EXIF orientation tag instead of rotating the pixels. */
    private fun rotationDegrees(photo: ByteArray): Float = try {
        when (ExifInterface(photo.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
}
