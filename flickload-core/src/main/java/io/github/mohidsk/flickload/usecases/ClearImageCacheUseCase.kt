package io.github.mohidsk.flickload.usecases

import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.FlickLoadImageLoader

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ClearImageCacheUseCase(
    private val imageLoader: FlickLoadImageLoader
) {
    suspend fun executeMemoryOnly() {
        imageLoader.clearMemoryCache()
    }

    suspend fun executeAll() {
        imageLoader.clearAllCaches()
    }
}
