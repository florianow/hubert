package com.hubert.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.globalSettingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "hubert_global_settings")

data class AppSettings(
    val geminiKey: String = "",
    val azureKey: String = "",
    val azureRegion: String = ""
) {
    val hasGemini: Boolean get() = geminiKey.isNotBlank()
    val hasAzure: Boolean get() = azureKey.isNotBlank() && azureRegion.isNotBlank()
    val hasAll: Boolean get() = hasGemini && hasAzure
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_GEMINI_KEY   = stringPreferencesKey("gemini_api_key")
        private val KEY_AZURE_KEY    = stringPreferencesKey("azure_speech_key")
        private val KEY_AZURE_REGION = stringPreferencesKey("azure_speech_region")
    }

    val settings: Flow<AppSettings> = context.globalSettingsDataStore.data.map { prefs ->
        AppSettings(
            geminiKey   = prefs[KEY_GEMINI_KEY]   ?: "",
            azureKey    = prefs[KEY_AZURE_KEY]    ?: "",
            azureRegion = prefs[KEY_AZURE_REGION] ?: ""
        )
    }

    suspend fun save(geminiKey: String, azureKey: String, azureRegion: String) {
        context.globalSettingsDataStore.edit { prefs ->
            prefs[KEY_GEMINI_KEY]   = geminiKey
            prefs[KEY_AZURE_KEY]    = azureKey
            prefs[KEY_AZURE_REGION] = azureRegion
        }
    }
}
