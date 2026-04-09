package com.najmi.corvus.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.najmi.corvus.domain.model.LlmProvider
import com.najmi.corvus.ui.theme.ColorPalette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "corvus_settings")

data class UserPreferences(
    val preferredProvider: LlmProvider = LlmProvider.GEMINI,
    val responseLanguage: String = "auto",
    val prioritizeLocalSources: Boolean = false,
    val darkMode: Boolean? = null,
    val showAnimations: Boolean = true,
    val colorPalette: ColorPalette = ColorPalette.MONOCHROME,
    val isDebugMode: Boolean = false
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val PREFERRED_PROVIDER = stringPreferencesKey("preferred_provider")
        private val RESPONSE_LANGUAGE = stringPreferencesKey("response_language")
        private val PRIORITIZE_LOCAL = booleanPreferencesKey("prioritize_local_sources")
        private val DARK_MODE = stringPreferencesKey("dark_mode")
        private val SHOW_ANIMATIONS = booleanPreferencesKey("show_animations")
        private val COLOR_PALETTE = stringPreferencesKey("color_palette")
        private val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            preferredProvider = prefs[PREFERRED_PROVIDER]?.let {
                try { LlmProvider.valueOf(it) } catch (e: Exception) { LlmProvider.GEMINI }
            } ?: LlmProvider.GEMINI,
            responseLanguage = prefs[RESPONSE_LANGUAGE] ?: "auto",
            prioritizeLocalSources = prefs[PRIORITIZE_LOCAL] ?: false,
            darkMode = prefs[DARK_MODE]?.let {
                when (it) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            },
            showAnimations = prefs[SHOW_ANIMATIONS] ?: true,
            colorPalette = prefs[COLOR_PALETTE]?.let {
                try { ColorPalette.valueOf(it) } catch (e: Exception) { ColorPalette.MONOCHROME }
            } ?: ColorPalette.MONOCHROME,
            isDebugMode = prefs[DEBUG_MODE] ?: false
        )
    }

    suspend fun setPreferredProvider(provider: LlmProvider) {
        context.dataStore.edit { prefs ->
            prefs[PREFERRED_PROVIDER] = provider.name
        }
    }

    suspend fun setResponseLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[RESPONSE_LANGUAGE] = language
        }
    }

    suspend fun setPrioritizeLocalSources(prioritize: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PRIORITIZE_LOCAL] = prioritize
        }
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE] = enabled?.toString() ?: "system"
        }
    }

    suspend fun setShowAnimations(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_ANIMATIONS] = show
        }
    }

    suspend fun setColorPalette(palette: ColorPalette) {
        context.dataStore.edit { prefs ->
            prefs[COLOR_PALETTE] = palette.name
        }
    }

    suspend fun setDebugMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DEBUG_MODE] = enabled
        }
        DebugLogger.setEnabled(enabled)
    }
}
