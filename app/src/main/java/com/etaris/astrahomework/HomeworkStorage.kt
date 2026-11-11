package com.etaris.astrahomework

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

// Injected from the ignored local.properties during development; empty in public builds.
const val DEFAULT_URL = BuildConfig.DEFAULT_BASE_URL

private val Context.settingsStore by preferencesDataStore(name = "homework_settings")
private val folderKey = stringPreferencesKey("folder")
private val urlKey = stringPreferencesKey("url")
private val tokenKey = stringPreferencesKey("token")
private val seenKey = stringPreferencesKey("seen")
private val discoveryKey = stringPreferencesKey("first_discovery_json")
private val checkPrereleaseKey = stringPreferencesKey("check_prerelease")
private val useProxyKey = stringPreferencesKey("use_proxy")

data class Settings(
    val folder: String = "",
    val baseUrl: String = DEFAULT_URL,
    val token: String = "",
    val checkPrerelease: Boolean = false,
    val useProxy: Boolean = false,
) {
    override fun toString() = "Settings(credentials=redacted)"
}
data class HomeworkFile(
    val name: String, val uri: Uri, val modified: Long, val size: Long, val isNew: Boolean,
    val isUploaded: Boolean = false, val firstDiscoveredAt: Long? = null,
)

data class FileHistory(val seenUris: Set<String>, val firstDiscovery: Map<String, Long>)

class HomeworkStorage(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<Settings> = store.data.map { preferences ->
        Settings(
            preferences[folderKey].orEmpty(),
            preferences[urlKey] ?: DEFAULT_URL,
            preferences[tokenKey].orEmpty(),
            preferences[checkPrereleaseKey]?.toBooleanStrictOrNull() ?: false,
            preferences[useProxyKey]?.toBooleanStrictOrNull() ?: false,
        )
    }

    suspend fun save(settings: Settings) {
        store.edit { preferences ->
            preferences[folderKey] = settings.folder
            preferences[urlKey] = settings.baseUrl.trim().trimEnd('/')
            preferences[tokenKey] = settings.token.trim()
            // Update preferences are persisted as well, so the settings flow always
            // carries the latest toggles and a connection save can never erase them.
            preferences[checkPrereleaseKey] = settings.checkPrerelease.toString()
            preferences[useProxyKey] = settings.useProxy.toString()
        }
    }

    /** Persists only the update-check preferences; credentials are untouched. */
    suspend fun saveUpdatePreferences(checkPrerelease: Boolean, useProxy: Boolean) {
        store.edit { preferences ->
            preferences[checkPrereleaseKey] = checkPrerelease.toString()
            preferences[useProxyKey] = useProxy.toString()
        }
    }

    suspend fun history(): FileHistory = store.data.map { preferences ->
        FileHistory(
            preferences[seenKey].orEmpty().split('|').filter(String::isNotBlank).toSet(),
            discoveryMap(preferences[discoveryKey]),
        )
    }.first()

    /** Atomic insert-only merge: refreshes and uploads never reset first discovery. */
    suspend fun recordDiscovery(uris: List<String>, now: Long) {
        store.edit { preferences ->
            val times = discoveryMap(preferences[discoveryKey]).toMutableMap()
            uris.forEach { times.putIfAbsent(it, now) }
            preferences[discoveryKey] = JSONObject(times as Map<*, *>).toString()
        }
    }

    private fun discoveryMap(raw: String?): Map<String, Long> {
        val json = try { JSONObject(raw ?: "{}") } catch (_: Exception) { JSONObject() }
        return buildMap {
            json.keys().forEach { uri ->
                val time = json.optLong(uri, -1)
                if (time >= 0) put(uri, time)
            }
        }
    }

    suspend fun markSeen(uri: Uri) {
        store.edit { preferences ->
            val seen = preferences[seenKey].orEmpty().split('|').filter(String::isNotBlank).toMutableSet()
            seen.add(uri.toString())
            preferences[seenKey] = seen.joinToString("|")
        }
    }
}
