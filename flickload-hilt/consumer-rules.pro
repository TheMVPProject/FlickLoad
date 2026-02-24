# FlickLoad Hilt - Consumer ProGuard Rules

# Keep Hilt module and qualifiers
-keep class io.github.mohidsk.flickload.hilt.FlickLoadModule { *; }
-keep @interface io.github.mohidsk.flickload.hilt.FlickLoadMemoryCache
-keep @interface io.github.mohidsk.flickload.hilt.FlickLoadDiskCache

# Keep Dagger-generated factories
-keep class io.github.mohidsk.flickload.hilt.FlickLoadModule_* { *; }
