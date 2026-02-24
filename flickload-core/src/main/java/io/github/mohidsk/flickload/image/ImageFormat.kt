package io.github.mohidsk.flickload.image

/**
 * Supported image formats with magic-byte detection, MIME type mapping,
 * and metadata (transparency, lossless).
 */
enum class ImageFormat(val extension: String) {
    JPEG("jpg"),
    PNG("png"),
    WEBP("webp"),
    AVIF("avif"),
    GIF("gif"),
    HEIC("heic"),
    UNKNOWN("unknown");

    /** MIME content type for this format. */
    val contentType: String
        get() = when (this) {
            JPEG -> "image/jpeg"
            PNG -> "image/png"
            WEBP -> "image/webp"
            AVIF -> "image/avif"
            GIF -> "image/gif"
            HEIC -> "image/heic"
            UNKNOWN -> "application/octet-stream"
        }

    /** Whether this format supports an alpha channel. */
    val supportsTransparency: Boolean
        get() = when (this) {
            PNG, WEBP, AVIF, GIF -> true
            JPEG, HEIC, UNKNOWN -> false
        }

    /** Whether this format is always lossless. */
    val isLossless: Boolean
        get() = this == PNG

    companion object {
        /** Resolve format from a file extension string (e.g. "jpg", "png"). */
        fun fromExtension(ext: String): ImageFormat {
            return entries.find {
                it.extension.equals(ext, ignoreCase = true)
            } ?: UNKNOWN
        }

        /** Resolve format from a full file name (e.g. "photo.jpg"). */
        fun fromFileName(fileName: String): ImageFormat {
            val extension = fileName.substringAfterLast('.', "")
            return fromExtension(extension)
        }

        /** Resolve format from a MIME content-type header (e.g. "image/jpeg"). */
        fun fromContentType(contentType: String): ImageFormat {
            val mimeType = contentType.lowercase().trim().substringBefore(';').trim()
            return entries.find { it.contentType == mimeType } ?: UNKNOWN
        }
    }
}
