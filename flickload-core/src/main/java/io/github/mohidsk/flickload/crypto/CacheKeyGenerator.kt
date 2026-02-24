package io.github.mohidsk.flickload.crypto

import androidx.annotation.RestrictTo
import java.security.MessageDigest

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object CacheKeyGenerator {

    private val threadLocalDigest = ThreadLocal.withInitial {
        MessageDigest.getInstance("SHA-256")
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /**
     * Generate a SHA256-based cache key from a URL, preserving the file extension.
     *
     * Example: "https://cdn.example.com/images/avatar.png" -> "a1b2c3...f4.png"
     */
    fun generateKey(url: String): String {
        val hash = sha256Hex(url)
        val extension = url.substringAfterLast('.', "")
            .substringBefore('?')
            .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }

        return if (extension != null) "$hash.$extension" else hash
    }

    /**
     * Batch key generation — avoids repeated ThreadLocal lookups.
     */
    fun generateKeys(urls: List<String>): List<String> {
        val digest = threadLocalDigest.get()!!
        return urls.map { url ->
            val hash = sha256HexFast(digest, url)
            val extension = url.substringAfterLast('.', "")
                .substringBefore('?')
                .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }
            if (extension != null) "$hash.$extension" else hash
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = threadLocalDigest.get()!!
        return sha256HexFast(digest, input)
    }

    private fun sha256HexFast(digest: MessageDigest, input: String): String {
        digest.reset()
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val chars = CharArray(hashBytes.size * 2)
        for (i in hashBytes.indices) {
            val v = hashBytes[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(chars)
    }
}
