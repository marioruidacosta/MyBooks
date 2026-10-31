package com.mcosta.mybooks.data.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoShrinkerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun photo(width: Int, height: Int): File {
        val file = File(tmp.root, "photo.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    private fun size(bytes: ByteArray) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
    }.let { it.outWidth to it.outHeight }

    @Test
    fun largePhotoIsShrunkToMaxSideKeepingAspect() {
        val (w, h) = size(AndroidPhotoShrinker.shrink(photo(3000, 2000).readBytes(), maxSide = 1000))
        assertEquals(1000, w)
        assertEquals(667, h)
    }

    @Test
    fun smallPhotoKeepsItsSize() {
        val (w, h) = size(AndroidPhotoShrinker.shrink(photo(400, 600).readBytes(), maxSide = 1000))
        assertEquals(400, w)
        assertEquals(600, h)
    }

    @Test
    fun sampleSizeIsLargestPowerOfTwoKeepingAtLeastMaxSide() {
        assertEquals(4, AndroidPhotoShrinker.sampleSize(4000, 3000, 1000))
        assertEquals(2, AndroidPhotoShrinker.sampleSize(3999, 3000, 1000))
        assertEquals(1, AndroidPhotoShrinker.sampleSize(1500, 1000, 1000))
        assertEquals(8, AndroidPhotoShrinker.sampleSize(8000, 6000, 1000))
        assertTrue(AndroidPhotoShrinker.sampleSize(500, 500, 1000) == 1)
    }
}
