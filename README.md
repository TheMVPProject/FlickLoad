<p align="center">
  <h1 align="center">FlickLoad</h1>
  <p align="center">High-performance image loading and caching for Android</p>
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.mohidsk/flickload-core"><img src="https://img.shields.io/maven-central/v/io.github.mohidsk/flickload-core?color=blue&label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen.svg" alt="API 26+">
  <img src="https://img.shields.io/badge/Kotlin-2.2+-purple.svg" alt="Kotlin">
</p>

---

FlickLoad delivers **zero-flicker image rendering** through a 3-tier caching pipeline — memory, disk, and network — with automatic format detection, intelligent prefetching, and download deduplication.

## Why FlickLoad?

| | FlickLoad | Coil | Glide |
|---|---|---|---|
| **Network Transport** | Cronet (QUIC/HTTP2) + OkHttp fallback | OkHttp only | HttpUrlConnection / OkHttp |
| **Zero-Flicker Compose** | Synchronous memory lookup | Async only | Async only |
| **Download Dedup** | Built-in | Manual | Manual |
| **Format Detection** | Magic bytes (JPEG, PNG, WebP, GIF, AVIF, HEIC) | Content-Type header | Content-Type header |
| **Prefetching** | Batch with cancellation | Single URL | Single URL |
| **Low-Memory Adapt** | Automatic cache reduction | Manual config | Manual config |

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    // Required — core engine
    implementation("io.github.mohidsk:flickload-core:0.1.0")

    // Optional — Jetpack Compose support
    implementation("io.github.mohidsk:flickload-compose:0.1.0")

    // Optional — Hilt dependency injection
    implementation("io.github.mohidsk:flickload-hilt:0.1.0")
}
```

<details>
<summary>Groovy DSL</summary>

```groovy
dependencies {
    implementation 'io.github.mohidsk:flickload-core:0.1.0'
    implementation 'io.github.mohidsk:flickload-compose:0.1.0'  // optional
    implementation 'io.github.mohidsk:flickload-hilt:0.1.0'     // optional
}
```

</details>

## Quick Start

### 1. Initialize

```kotlin
// In your Application.onCreate() or Activity
val imageLoader = FlickLoad.Builder(context).build()
```

### 2. Load an Image

```kotlin
// Simple — returns Bitmap?
val bitmap = imageLoader.getImage("https://example.com/photo.jpg")
```

### 3. Done

That's it. FlickLoad handles memory caching, disk caching, network fetching, and format detection automatically.

## Usage

### Load with Result Info

Know where your image came from — memory, disk, or network:

```kotlin
when (val result = imageLoader.getImageWithResult(url)) {
    is ImageLoadResult.Success -> {
        result.bitmap  // the loaded bitmap
        result.source  // MEMORY, DISK, or NETWORK
    }
    is ImageLoadResult.Error -> {
        result.throwable  // what went wrong
    }
    is ImageLoadResult.Loading -> { /* still loading */ }
}
```

### Reactive Loading with Flow

```kotlin
imageLoader.getImageAsFlow(url).collect { result ->
    // emits Loading → Success or Error
}
```

### Batch Loading

Load multiple images concurrently with a single call:

```kotlin
val images: Map<String, Bitmap> = imageLoader.getImages(
    listOf(
        "https://example.com/1.jpg",
        "https://example.com/2.jpg",
        "https://example.com/3.jpg"
    )
)
```

### Prefetching

Pre-download images before they're needed:

```kotlin
// Start background prefetch
imageLoader.prefetchImages(urls)

// Cancel when no longer needed
imageLoader.cancelPrefetch(urls)

// Wait for completion
imageLoader.waitForPrefetch(urls)
```

### Cache Management

```kotlin
imageLoader.clearMemoryCache()  // memory only
imageLoader.clearAllCaches()    // memory + disk
imageLoader.dispose()           // release all resources
```

### Custom Configuration

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

## Jetpack Compose

### Setup

Provide the loader via `CompositionLocal`:

```kotlin
CompositionLocalProvider(LocalFlickLoadImageLoader provides imageLoader) {
    MyApp()
}
```

### Display an Image

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

### Custom Loading & Error States

```kotlin
CachedImage(
    imageUrl = url,
    modifier = Modifier.fillMaxWidth().height(200.dp),
    loading = {
        ShimmerPlaceholder()
    },
    error = {
        Text("Failed to load")
    }
)
```

## Hilt Integration

Just add the dependency — no manual setup needed:

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

Works in ViewModels too:

```kotlin
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageLoader: FlickLoadImageLoader
) : ViewModel() {

    fun loadImages(urls: List<String>) = viewModelScope.launch {
        val images = imageLoader.getImages(urls)
    }
}
```

## Architecture

```
┌──────────────────────────────────────────────────┐
│                  Public API                       │
│   FlickLoadImageLoader / FlickLoad.Builder        │
├──────────────────────────────────────────────────┤
│               Repository Layer                    │
│         ImageLoaderRepositoryImpl                 │
├──────────┬───────────────┬───────────────────────┤
│  Memory  │     Disk      │       Network          │
│ LruCache │ File I/O +    │ Cronet (QUIC/H2)       │
│ (Bitmap  │ SQLite meta   │ OkHttp fallback        │
│ + Bytes) │ LRU eviction  │ Download dedup         │
├──────────┴───────────────┴───────────────────────┤
│            Support Utilities                      │
│  CacheKeyGenerator · ImageProcessor · ImageFormat │
└──────────────────────────────────────────────────┘
```

## Cache Defaults

| Parameter | Default | Low Memory* |
|---|---|---|
| Critical Memory | 100 MB | 50 MB |
| Hot Memory | 150 MB | 75 MB |
| Warm Memory | 330 MB | 165 MB |
| Disk Cache Size | 2 GB | 1 GB |
| Disk Expiration | 30 days | 30 days |

*Low-memory mode is automatically enabled on devices reporting `ActivityManager.isLowRamDevice`.*

## Requirements

| | Version |
|---|---|
| **Min SDK** | 26 (Android 8.0) |
| **Kotlin** | 2.2+ |
| **Coroutines** | 1.10+ |

## ProGuard / R8

Consumer ProGuard rules are bundled with each module. No additional configuration needed.

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
