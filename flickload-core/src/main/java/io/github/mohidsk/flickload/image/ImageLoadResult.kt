package io.github.mohidsk.flickload.image

import android.graphics.Bitmap

/**
 * Result of an image load operation.
 *
 * Use with [io.github.mohidsk.flickload.FlickLoadImageLoader.getImageWithResult]
 * or [io.github.mohidsk.flickload.FlickLoadImageLoader.getImageAsFlow].
 */
sealed class ImageLoadResult {
    /** The image was loaded successfully from the given [source] tier. */
    data class Success(val bitmap: Bitmap, val source: Source) : ImageLoadResult()

    /** The image load failed with [throwable]. */
    data class Error(val throwable: Throwable) : ImageLoadResult()

    /** The image is currently being loaded. */
    data object Loading : ImageLoadResult()

    /** Cache tier from which the image was served. */
    enum class Source {
        MEMORY,
        DISK,
        NETWORK
    }
}
