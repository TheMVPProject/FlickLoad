package io.github.mohidsk.flickload.usecases

import android.graphics.Bitmap
import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.FlickLoadImageLoader

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class GetImageUseCase(
    private val imageLoader: FlickLoadImageLoader
) {
    suspend fun execute(url: String): Bitmap? {
        return imageLoader.getImage(url)
    }
}
