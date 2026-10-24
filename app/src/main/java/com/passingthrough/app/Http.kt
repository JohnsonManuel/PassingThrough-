package com.passingthrough.app

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Thrown for non-2xx responses so callers can tell "not found" apart from a network failure. */
class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

/**
 * Minimal HTTP GET with an offline fallback: every successful body is written to
 * cacheDir/http/<sha1(url)>, and on any network error the saved copy is returned instead.
 */
object Http {
    const val USER_AGENT =
        "PassingThrough/1.0 (Android; contact: https://github.com/johnsonmanuel/passingthrough-)"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    @Volatile
    private var cacheDir: File? = null

    fun init(baseCacheDir: File) {
        cacheDir = File(baseCacheDir, "http").apply { mkdirs() }
    }

    fun get(url: String): String {
        val cached = cacheFile(url)
        try {
            val body = download(url)
            if (cached != null) {
                try {
                    val tmp = File(cached.parentFile, cached.name + ".tmp")
                    tmp.writeText(body, Charsets.UTF_8)
                    if (!tmp.renameTo(cached)) {
                        cached.writeText(body, Charsets.UTF_8)
                        tmp.delete()
                    }
                } catch (_: IOException) {
                    // A full disk must not break the live response.
                }
            }
            return body
        } catch (e: IOException) {
            if (cached != null && cached.isFile) return cached.readText(Charsets.UTF_8)
            throw e
        }
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code, url)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun cacheFile(url: String): File? {
        val dir = cacheDir ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray(Charsets.UTF_8))
        return File(dir, digest.joinToString("") { "%02x".format(it) })
    }
}
