package io.github.mohidsk.flickload.compose

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mohidsk.flickload.FlickLoadImageLoader

val LocalFlickLoadImageLoader = staticCompositionLocalOf<FlickLoadImageLoader> {
    error("No FlickLoadImageLoader provided. Wrap your content with CompositionLocalProvider(LocalFlickLoadImageLoader provides loader).")
}
