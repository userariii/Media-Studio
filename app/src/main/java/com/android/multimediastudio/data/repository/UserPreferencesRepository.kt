package com.android.multimediastudio.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.multimediastudio.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val hideSocialMediaKey = booleanPreferencesKey("hide_social_media")

    val themeModeStream: Flow<ThemeMode> = dataStore.data.map { prefs ->
        val modeStr = prefs[themeModeKey] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val hideSocialMediaStream: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[hideSocialMediaKey] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setHideSocialMedia(hide: Boolean) {
        dataStore.edit { prefs ->
            prefs[hideSocialMediaKey] = hide
        }
    }
}
