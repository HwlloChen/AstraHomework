package com.etaris.astrahomework

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A published GitHub release that can be offered to the user. */
data class ReleaseInfo(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val notes: String,
) {
    val displayVersion get() = version.removePrefix("v").removePrefix("V")
}

/**
 * Compares version numbers tolerantly, ignoring decoration such as "v" or "-beta".
 *
 * Numeric segments are compared in order and a missing segment counts as zero, so
 * "1.2" equals "1.2.0". A build or pre-release suffix ranks below the same numbers.
 *
 * @param current Version of the running app, for example "1.2".
 * @param candidate Version tag from the release, for example "v1.3".
 * @return True only when [candidate] is strictly newer than [current].
 */
fun isNewerVersion(current: String, candidate: String): Boolean {
    fun split(value: String): Pair<List<Int>, Boolean> {
        val body = value.trim().removePrefix("v").removePrefix("V")
        val numeric = body.takeWhile { it.isDigit() || it == '.' }
        val numbers = numeric.split('.').filter(String::isNotEmpty).map { it.toIntOrNull() ?: 0 }
        return numbers.ifEmpty { listOf(0) } to (numeric.length < body.length)
    }
    val (left, leftSuffix) = split(current)
    val (right, rightSuffix) = split(candidate)
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { 0 }
        val b = right.getOrElse(index) { 0 }
        if (a != b) return b > a
    }
    return leftSuffix && !rightSuffix
}

/**
 * Fetches the newest compatible GitHub release without any credentials.
 *
 * Only HTTPS GitHub URLs are accepted, redirects are not followed, and every
 * failure is reported as "no update known" so the check can never block the app.
 *
 * @param repository GitHub "owner/name" that publishes this app.
 * @param currentVersion Version string of the running build.
 */
class UpdateChecker(private val repository: String, private val currentVersion: String) {
    /**
     * Reads the latest release.
     *
     * @return The newer release, or null when the app is current or offline.
     */
    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$repository/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub requires a User-Agent; no token is ever sent from the app.
            connection.setRequestProperty("User-Agent", "AstraHomework")
            if (connection.responseCode != 200) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRelease(JSONObject(body))?.takeIf { isNewerVersion(currentVersion, it.version) }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo? {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val tag = json.optString("tag_name").trim()
        val page = json.optString("html_url").trim()
        if (tag.isBlank() || !page.startsWith("https://github.com/")) return null
        return ReleaseInfo(
            version = tag,
            pageUrl = page,
            apkUrl = pickApk(json.optJSONArray("assets")),
            notes = json.optString("body").trim().take(4000),
        )
    }

    /** Prefers a non-debug APK asset so users can install without opening the browser. */
    private fun pickApk(assets: JSONArray?): String? {
        if (assets == null) return null
        val urls = (0 until assets.length()).mapNotNull { index ->
            val asset = assets.optJSONObject(index) ?: return@mapNotNull null
            val url = asset.optString("browser_download_url").trim()
            url.takeIf { it.startsWith("https://github.com/") && it.endsWith(".apk") }
        }
        return urls.firstOrNull { !it.contains("debug", ignoreCase = true) } ?: urls.firstOrNull()
    }
}
