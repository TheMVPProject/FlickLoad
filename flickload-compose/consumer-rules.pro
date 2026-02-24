# FlickLoad Compose - Consumer ProGuard Rules

# Keep Compose public API
-keep class io.github.mohidsk.flickload.compose.CachedImageKt { *; }
-keep class io.github.mohidsk.flickload.compose.LocalFlickLoadImageLoaderKt { *; }
-keep class io.github.mohidsk.flickload.compose.FlickLoadImageDefaults { *; }
