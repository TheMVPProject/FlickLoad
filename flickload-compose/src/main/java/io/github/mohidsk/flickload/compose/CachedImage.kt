package io.github.mohidsk.flickload.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.mohidsk.flickload.image.ImageLoadResult

/**
 * A composable that displays an image with FlickLoad's 3-tier caching.
 *
 * When the image is in memory, it renders instantly with zero flicker.
 * Otherwise falls back to an async load via FlickLoad's core engine,
 * displaying loading/error states as appropriate.
 *
 * @param imageUrl The URL of the image to load.
 * @param modifier Modifier for the image container.
 * @param contentScale How the image should be scaled.
 * @param colorFilter Optional color filter for the image.
 * @param contentDescription Accessibility description.
 * @param loadingIndicatorColor Color of the loading spinner.
 * @param errorIconTint Tint color for the error placeholder.
 * @param loading Custom loading composable slot. If null, uses default spinner.
 * @param error Custom error composable slot. If null, uses default placeholder.
 */
@Composable
fun CachedImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    contentDescription: String = "Image",
    loadingIndicatorColor: Color = FlickLoadImageDefaults.loadingIndicatorColor,
    errorIconTint: Color = FlickLoadImageDefaults.errorIconTint,
    loading: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
) {
    val imageLoader = LocalFlickLoadImageLoader.current

    val cachedBitmap = remember(imageUrl) {
        if (imageUrl.isNotEmpty()) imageLoader.getImageFromMemorySync(imageUrl) else null
    }

    if (cachedBitmap != null) {
        Image(
            bitmap = cachedBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
        )
    } else {
        var loadResult by remember(imageUrl) {
            mutableStateOf<ImageLoadResult>(ImageLoadResult.Loading)
        }

        LaunchedEffect(imageUrl) {
            if (imageUrl.isNotEmpty()) {
                loadResult = imageLoader.getImageWithResult(imageUrl)
            }
        }

        when (val result = loadResult) {
            is ImageLoadResult.Loading -> {
                Box(modifier = modifier) {
                    loading?.invoke() ?: DefaultLoadingIndicator(loadingIndicatorColor)
                }
            }
            is ImageLoadResult.Success -> {
                Image(
                    bitmap = result.bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                )
            }
            is ImageLoadResult.Error -> {
                Box(modifier = modifier) {
                    error?.invoke() ?: DefaultErrorPlaceholder(errorIconTint)
                }
            }
        }
    }
}

@Composable
private fun DefaultLoadingIndicator(color: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = color,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun DefaultErrorPlaceholder(tint: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val strokeWidth = 2.dp.toPx()
            drawRoundRect(
                color = tint.copy(alpha = 0.3f),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
            drawLine(
                color = tint.copy(alpha = 0.5f),
                start = Offset(strokeWidth * 2, size.height - strokeWidth * 2),
                end = Offset(size.width - strokeWidth * 2, strokeWidth * 2),
                strokeWidth = strokeWidth
            )
        }
    }
}
