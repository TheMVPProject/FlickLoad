# FlickLoad

A high-performance image loading and caching library for Android, built with Kotlin Coroutines.

FlickLoad delivers **zero-flicker image rendering** through a 3-tier caching pipeline — memory, disk, and network — with automatic format detection and intelligent prefetching.

## Features

- **3-Tier Cache Pipeline** — Memory (LRU) → Disk (SQLite-tracked) → Network
- **Dual Network Transport** — Cronet (QUIC/HTTP2/Brotli) with automatic OkHttp fallback
- **Zero-Flicker Compose** — Synchronous memory lookups for instant rendering
- **Smart Prefetching** — Background batch downloading with cancellation support
- **Download Deduplication** — Concurrent requests for the same URL are coalesced
- **Adaptive Batch I/O** — Work-stealing parallelism for high-throughput disk operations
- **Automatic Low-Memory Detection** — Adapts cache sizes for constrained devices
- **Format-Aware Caching** — Magic-byte detection for JPEG, PNG, WebP, GIF, AVIF, HEIC
- **Hilt Integration** — Optional module for dependency injection
- **Jetpack Compose Support** — Ready-to-use `CachedImage` composable

## Setup

### Gradle (Kotlin DSL)

Add the dependencies you need:

```kotlin
dependencies {
    // Core — required
    implementation("io.github.mohidsk:flickload-core:0.1.0")

    // Compose — CachedImage composable (optional)
    implementation("io.github.mohidsk:flickload-compose:0.1.0")

    // Hilt — DI module (optional)
    implementation("io.github.mohidsk:flickload-hilt:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.mohidsk:flickload-core:0.1.0'
    implementation 'io.github.mohidsk:flickload-compose:0.1.0'  // optional
    implementation 'io.github.mohidsk:flickload-hilt:0.1.0'     // optional
}
```

## Quick Start

### 1. Initialize FlickLoad

```kotlin
// Create the image loader (typically in Application.onCreate)
val imageLoader = FlickLoad.Builder(context).build()
```

With custom configuration:

```kotlin
val imageLoader = FlickLoad.Builder(context)
    .configuration(
        ImageCacheConfiguration(
            criticalMemoryMB = 50,
            hotMemoryMB = 75,
            warmMemoryMB = 150,
            maxDiskCacheSizeMB = 500,
            diskExpirationDays = 14
        )
    )
    .build()
```

### 2. Load an Image

```kotlin
// Simple — returns Bitmap?
val bitmap = imageLoader.getImage("https://example.com/photo.jpg")

// With source info — returns ImageLoadResult
when (val result = imageLoader.getImageWithResult(url)) {
    is ImageLoadResult.Success -> {
        // result.bitmap — the loaded bitmap
        // result.source — MEMORY, DISK, or NETWORK
    }
    is ImageLoadResult.Error -> {
        // result.throwable — what went wrong
    }
    is ImageLoadResult.Loading -> { /* still loading */ }
}

// Reactive — Flow<ImageLoadResult>
imageLoader.getImageAsFlow(url).collect { result ->
    // handle Loading → Success/Error
}
```

### 3. Batch Loading

```kotlin
// Load multiple images concurrently
val images: Map<String, Bitmap> = imageLoader.getImages(
    listOf(
        "https://example.com/photo1.jpg",
        "https://example.com/photo2.jpg",
        "https://example.com/photo3.jpg"
    )
)
```

### 4. Prefetching

```kotlin
// Prefetch images in the background
imageLoader.prefetchImages(urls)

// Cancel specific prefetches
imageLoader.cancelPrefetch(urls)

// Wait for prefetch to complete
val didWait = imageLoader.waitForPrefetch(urls)
```

### 5. Cache Management

```kotlin
// Clear memory cache only
imageLoader.clearMemoryCache()

// Clear all caches (memory + disk)
imageLoader.clearAllCaches()

// Cleanup when done
imageLoader.dispose()
```

## Jetpack Compose

The `flickload-compose` module provides a `CachedImage` composable that tries a synchronous memory lookup first (zero flicker), then falls back to an async load with loading/error states.

### Setup

Provide the image loader via `CompositionLocal`:

```kotlin
CompositionLocalProvider(LocalFlickLoadImageLoader provides imageLoader) {
    // Your app content
    MyScreen()
}
```

### Usage

```kotlin
@Composable
fun UserAvatar(avatarUrl: String) {
    CachedImage(
        imageUrl = avatarUrl,
        modifier = Modifier.size(48.dp).clip(CircleShape),
        contentScale = ContentScale.Crop,
        contentDescription = "User avatar"
    )
}
```

#### Custom Loading & Error States

```kotlin
CachedImage(
    imageUrl = url,
    modifier = Modifier.fillMaxWidth().height(200.dp),
    loading = {
        // Your custom loading UI
        ShimmerPlaceholder()
    },
    error = {
        // Your custom error UI
        Icon(Icons.Default.BrokenImage, contentDescription = null)
    }
)
```

## Hilt Integration

The `flickload-hilt` module provides a ready-to-use Dagger Hilt module. Just add the dependency — no manual setup required.

```kotlin
@AndroidEntryPoint
class MyActivity : ComponentActivity() {

    @Inject
    lateinit var imageLoader: FlickLoadImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // imageLoader is ready to use
    }
}
```

Inject into ViewModels:

```kotlin
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageLoader: FlickLoadImageLoader
) : ViewModel() {

    fun loadImages(urls: List<String>) = viewModelScope.launch {
        val images = imageLoader.getImages(urls)
        // ...
    }
}
```

## Architecture

```
┌──────────────────────────────────────────────────┐
│                 Public API                        │
│  FlickLoadImageLoader / FlickLoad.Builder         │
├──────────────────────────────────────────────────┤
│              Repository Layer                     │
│        ImageLoaderRepositoryImpl                  │
├──────────┬───────────────┬───────────────────────┤
│  Memory  │     Disk      │       Network          │
│ LruCache │ File I/O +    │ Cronet (QUIC/H2)       │
│ (Bitmap  │ SQLite meta   │ OkHttp fallback        │
│ + Bytes) │ LRU eviction  │ Download dedup         │
├──────────┴───────────────┴───────────────────────┤
│           Support Utilities                       │
│  CacheKeyGenerator · ImageProcessor · ImageFormat │
└──────────────────────────────────────────────────┘
```

## Configuration Defaults

| Parameter | Default | Low Memory |
|---|---|---|
| Critical Memory | 100 MB | 50 MB |
| Hot Memory | 150 MB | 75 MB |
| Warm Memory | 330 MB | 165 MB |
| Disk Cache Size | 2 GB | 1 GB |
| Disk Expiration | 30 days | 30 days |

Low-memory mode is automatically enabled on devices that report `ActivityManager.isLowRamDevice`.

## Requirements

- **Min SDK**: 26 (Android 8.0)
- **Kotlin**: 2.2+
- **Coroutines**: 1.10+

## ProGuard / R8

Consumer ProGuard rules are bundled with `flickload-core`. No additional configuration needed.

## License

```
Copyright 2024 Mohid SK

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
