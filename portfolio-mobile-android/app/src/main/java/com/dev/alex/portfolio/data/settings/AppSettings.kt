package com.dev.alex.portfolio.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "app_settings")

enum class ThemeMode { System, Light, Dark }

data class AppPrefs(
    val serverUrl: String = "",
    val lastUsername: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
)

/**
 * Device-local preferences. Nothing secret lives here — the password only exists
 * keystore-encrypted in CredentialStore.
 */
class AppSettings(private val context: Context) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val lastUsername = stringPreferencesKey("last_username")
        val themeMode = stringPreferencesKey("theme_mode")
    }

    val prefs: Flow<AppPrefs> = context.settingsStore.data.map { it.toPrefs() }

    suspend fun current(): AppPrefs = prefs.first()

    suspend fun rememberSignIn(serverUrl: String, username: String) {
        context.settingsStore.edit {
            it[Keys.serverUrl] = serverUrl
            it[Keys.lastUsername] = username
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[Keys.themeMode] = mode.name }
    }

    private fun Preferences.toPrefs() = AppPrefs(
        serverUrl = this[Keys.serverUrl].orEmpty(),
        lastUsername = this[Keys.lastUsername].orEmpty(),
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.themeMode] } ?: ThemeMode.System,
    )
}
