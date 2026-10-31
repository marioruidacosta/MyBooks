package com.mcosta.mybooks.platform

import com.mcosta.mybooks.data.covers.PhotoShrinker
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.max

/** Scales with UIKit; drawing a UIImage applies its EXIF orientation, so the result is upright. */
@OptIn(ExperimentalForeignApi::class)
object IosPhotoShrinker : PhotoShrinker {
    override fun shrink(photo: ByteArray, maxSide: Int): ByteArray {
        val image = UIImage.imageWithData(photo.toNSData()) ?: throw IllegalArgumentException("Not an image")
        val (width, height) = image.size.useContents { width to height }
        val scale = minOf(1.0, maxSide / max(width, height))
        val target = CGSizeMake(width * scale, height * scale)
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(1.0) }
        val resized = UIGraphicsImageRenderer(size = target, format = format).imageWithActions { _ ->
            image.drawInRect(target.useContents { CGRectMake(0.0, 0.0, this.width, this.height) })
        }
        val jpeg = UIImageJPEGRepresentation(resized, 0.85) ?: throw IllegalStateException("Couldn't encode the photo")
        return jpeg.toByteArray()
    }
}
