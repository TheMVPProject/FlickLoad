# FlickLoad Core - Consumer ProGuard Rules

# Keep public API
-keep class io.github.mohidsk.flickload.FlickLoad { *; }
-keep class io.github.mohidsk.flickload.FlickLoad$Builder { *; }
-keep class io.github.mohidsk.flickload.FlickLoadImageLoader { *; }
-keep class io.github.mohidsk.flickload.image.ImageCacheConfiguration { *; }
-keep class io.github.mohidsk.flickload.image.ImageCacheConfiguration$Companion { *; }
-keep class io.github.mohidsk.flickload.image.ImageFormat { *; }
-keep class io.github.mohidsk.flickload.image.ImageLoadResult { *; }
-keep class io.github.mohidsk.flickload.image.ImageLoadResult$* { *; }

# Cronet callback classes must not be obfuscated
-keep class org.chromium.net.** { *; }
-dontwarn org.chromium.net.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
