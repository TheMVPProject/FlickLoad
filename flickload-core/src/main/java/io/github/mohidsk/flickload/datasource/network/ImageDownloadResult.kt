package io.github.mohidsk.flickload.datasource.network

import android.graphics.Bitmap
import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.image.ImageFormat

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class ImageDownloadResult(
    val bitmap: Bitmap,
    val format: ImageFormat,
    val rawBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageDownloadResult) return false
        return bitmap == other.bitmap && format == other.format && rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = bitmap.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}
