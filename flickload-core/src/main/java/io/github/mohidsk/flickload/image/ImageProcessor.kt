package io.github.mohidsk.flickload.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ImageProcessor {

    fun decompressSync(bytes: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inMutable = true }
            )
        } catch (_: Exception) {
            null
        }
    }
}
