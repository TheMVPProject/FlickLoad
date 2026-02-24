package io.github.mohidsk.flickload.hilt

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FlickLoadMemoryCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FlickLoadDiskCache
