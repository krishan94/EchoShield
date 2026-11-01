package com.echoshield.core.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "echoshield_theme_preferences")

enum class AppThemeMode(val title: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System Default");

    companion object {
        fun fromString(value: String?): AppThemeMode = when (value?.uppercase()) {
            "LIGHT" -> LIGHT
            "DARK" -> DARK
            else -> SYSTEM
        }
    }
}

class ThemePreferences(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }

    val themeMode: Flow<AppThemeMode> = context.themeDataStore.data
        .map { preferences ->
            AppThemeMode.fromString(preferences[THEME_MODE_KEY])
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
}

