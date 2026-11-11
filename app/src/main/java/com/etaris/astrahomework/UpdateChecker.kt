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

/** Outcome of one update check, distinguishing "nothing newer" from "network failed". */
sealed interface UpdateCheckResult {
    /** GitHub answered, but no published release is newer than the running build. */
    data object UpToDate : UpdateCheckResult

    /** The request never completed; the cause is shown to the user as a transient error. */
    data object Unreachable : UpdateCheckResult

    /** A strictly newer release exists and can be offered. */
    data class NewRelease(val info: ReleaseInfo) : UpdateCheckResult
}

private class VersionParts(numbers: List<Int>, val suffix: String) {
    val numbers = numbers.ifEmpty { listOf(0) }

    /** Build number of a "-ci.N" suffix, or null for stable / plain pre-releases. */
    val ciNumber: Int? =
        suffix.takeIf { it.startsWith("-ci.") }?.removePrefix("-ci.")?.toIntOrNull()
}

private fun splitVersion(value: String): VersionParts {
    val body = value.trim().removePrefix("v").removePrefix("V")
    val numeric = body.takeWhile { it.isDigit() || it == '.' }
    val numbers = numeric.split('.').filter(String::isNotEmpty).map { it.toIntOrNull() ?: 0 }
    return VersionParts(numbers, body.substring(numeric.length))
}

/**
 * Compares version numbers tolerantly, ignoring decoration such as "v" or "-beta".
 *
 * Numeric segments are compared in order and a missing segment counts as zero, so
 * "1.2" equals "1.2.0". A build or pre-release suffix ranks below the same numbers.
 *
 * With [ciAware] the "-ci.N" suffix used by CI builds ranks above the equal plain
 * version and CI builds are ordered by their build number, so a CI install is
 * offered the next CI build and stable releases still outrank same-number CIs.
 *
 * @param current Version of the running app, for example "1.2" or "1.2-ci.43".
 * @param candidate Version tag from the release, for example "v1.3" or "v1.2-ci.44".
 * @return True only when [candidate] is strictly newer than [current].
 */
fun isNewerVersion(current: String, candidate: String, ciAware: Boolean = false): Boolean {
    val left = splitVersion(current)
    val right = splitVersion(candidate)
    for (index in 0 until maxOf(left.numbers.size, right.numbers.size)) {
        val a = left.numbers.getOrElse(index) { 0 }
        val b = right.numbers.getOrElse(index) { 0 }
        if (a != b) return b > a
    }
    if (ciAware) {
        if (left.ciNumber == null && right.ciNumber != null) return true
        if (left.ciNumber != null && right.ciNumber != null) return right.ciNumber > left.ciNumber
        if (left.ciNumber != null && right.ciNumber == null) return false
    }
    return left.suffix.isNotEmpty() && right.suffix.isEmpty()
}

/**
 * Fetches the newest compatible GitHub release without any credentials.
 *
 * Only HTTPS GitHub URLs are accepted, redirects are not followed, and every
 * failure degrades to [UpdateCheckResult.Unreachable] so the check can never
 * block the app. A 404 (repository without releases) is "up to date".
 *
 * @param repository GitHub "owner/name" that publishes this app.
 * @param currentVersion Version string of the running build.
 */
class UpdateChecker(private val repository: String, private val currentVersion: String) {

    /**
     * Reads the latest stable release.
     *
     * @param useProxy When true, routes the request through gh-proxy.com.
     */
    suspend fun stable(useProxy: Boolean = false): UpdateCheckResult = fetch(
        url = "https://api.github.com/repos/$repository/releases/latest",
        useProxy = useProxy,
        allowPrerelease = false,
    )

    /**
     * Reads the newest release including pre-releases and CI builds.
     *
     * The GitHub "latest" endpoint never returns pre-releases, so this queries
     * the full list and picks the newest entry the version comparison accepts.
     *
     * @param useProxy When true, routes the request through gh-proxy.com.
     */
    suspend fun prerelease(useProxy: Boolean = false): UpdateCheckResult = fetch(
        url = "https://api.github.com/repos/$repository/releases",
        useProxy = useProxy,
        allowPrerelease = true,
    )

    private suspend fun fetch(
        url: String,
        useProxy: Boolean,
        allowPrerelease: Boolean,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val effectiveUrl = if (useProxy) "https://gh-proxy.com/$url" else url
        val connection = URL(effectiveUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub requires a User-Agent; no token is ever sent from the app.
            connection.setRequestProperty("User-Agent", "AstraHomework")
            when (connection.responseCode) {
                200 -> Unit
                // "No releases published yet" is a valid, reachable answer.
                404 -> return@withContext UpdateCheckResult.UpToDate
                else -> return@withContext UpdateCheckResult.Unreachable
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val compare = { candidate: String -> isNewerVersion(currentVersion, candidate, allowPrerelease) }
            val release = parseReleases(body, allowPrerelease).firstOrNull { compare(it.version) }
                ?: return@withContext UpdateCheckResult.UpToDate
            // The proxy is also applied to the APK so downloads work where GitHub is slow.
            val apk = release.apkUrl?.takeIf { useProxy }?.let { "https://gh-proxy.com/$it" } ?: release.apkUrl
            UpdateCheckResult.NewRelease(release.copy(apkUrl = apk))
        } catch (_: Exception) {
            UpdateCheckResult.Unreachable
        } finally {
            connection.disconnect()
        }
    }

    /** Parses either a single release object or a release array. */
    private fun parseReleases(body: String, allowPrerelease: Boolean): List<ReleaseInfo> {
        return if (body.trimStart().startsWith('[')) {
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { parseRelease(it, allowPrerelease) }
            }
        } else {
            listOfNotNull(parseRelease(JSONObject(body), allowPrerelease))
        }
    }

    private fun parseRelease(json: JSONObject, allowPrerelease: Boolean): ReleaseInfo? {
        if (json.optBoolean("draft")) return null
        if (!allowPrerelease && json.optBoolean("prerelease")) return null
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
