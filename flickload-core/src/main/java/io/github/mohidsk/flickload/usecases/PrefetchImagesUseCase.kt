package io.github.mohidsk.flickload.usecases

import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.FlickLoadImageLoader

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PrefetchImagesUseCase(
    private val imageLoader: FlickLoadImageLoader
) {
    fun execute(urls: List<String>) {
        imageLoader.prefetchImages(urls)
    }
}
