package io.github.mohidsk.flickload.usecases

import android.graphics.Bitmap
import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.FlickLoadImageLoader

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class GetBatchImagesUseCase(
    private val imageLoader: FlickLoadImageLoader
) {
    suspend fun execute(urls: List<String>): Map<String, Bitmap> {
        return imageLoader.getImages(urls)
    }
}
