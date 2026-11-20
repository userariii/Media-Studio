package com.android.multimediastudio.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.multimediastudio.data.model.AppThemeColor
import com.android.multimediastudio.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val dataStore = context.applicationContext.dataStore
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val appThemeColorKey = stringPreferencesKey("app_theme_color")
    private val hideSocialMediaKey = booleanPreferencesKey("hide_social_media")

    private val syncPrefs by lazy {
        context.applicationContext.getSharedPreferences("user_preferences_sync", Context.MODE_PRIVATE)
    }

    fun getInitialThemeMode(): ThemeMode {
        val modeStr = syncPrefs.getString("theme_mode", null) ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun getInitialAppThemeColor(): AppThemeColor {
        val colorStr = syncPrefs.getString("app_theme_color", null) ?: return AppThemeColor.DEFAULT
        return try {
            AppThemeColor.valueOf(colorStr)
        } catch (_: Exception) {
            AppThemeColor.DEFAULT
        }
    }

    fun getInitialHideSocialMedia(): Boolean {
        return syncPrefs.getBoolean("hide_social_media", true)
    }

    val themeModeStream: Flow<ThemeMode> = dataStore.data.map { prefs ->
        val modeStr = prefs[themeModeKey] ?: syncPrefs.getString("theme_mode", null) ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val appThemeColorStream: Flow<AppThemeColor> = dataStore.data.map { prefs ->
        val colorStr = prefs[appThemeColorKey] ?: syncPrefs.getString("app_theme_color", null) ?: AppThemeColor.DEFAULT.name
        try {
            AppThemeColor.valueOf(colorStr)
        } catch (_: Exception) {
            AppThemeColor.DEFAULT
        }
    }

    val hideSocialMediaStream: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[hideSocialMediaKey] ?: syncPrefs.getBoolean("hide_social_media", true)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        syncPrefs.edit().putString("theme_mode", mode.name).apply()
        dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setAppThemeColor(color: AppThemeColor) {
        syncPrefs.edit().putString("app_theme_color", color.name).apply()
        dataStore.edit { prefs ->
            prefs[appThemeColorKey] = color.name
        }
    }

    suspend fun setHideSocialMedia(hide: Boolean) {
        syncPrefs.edit().putBoolean("hide_social_media", hide).apply()
        dataStore.edit { prefs ->
            prefs[hideSocialMediaKey] = hide
        }
    }
}
