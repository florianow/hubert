package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.repository.AppSettings
import android.content.Context
import com.hubert.data.repository.AnkiRepository
import com.hubert.data.repository.AnkiSync
import com.hubert.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.hubert.utils.DailyReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnkiUiState(
    val installed: Boolean = false,
    val hasPermission: Boolean = false,
    val decks: List<String> = emptyList(),
    val isSyncing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ankiRepository: AnkiRepository,
    private val ankiSync: AnkiSync,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _anki = MutableStateFlow(AnkiUiState())
    val anki: StateFlow<AnkiUiState> = _anki.asStateFlow()

    val ankiLastSync: StateFlow<String> = settingsRepository.ankiLastSync.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )

    /** Called when the settings screen opens and after the permission dialog. */
    fun refreshAnki() {
        viewModelScope.launch {
            val installed = ankiRepository.isInstalled()
            val permitted = installed && ankiRepository.hasPermission()
            val decks = if (permitted) runCatching { ankiRepository.decks().map { it.name } }.getOrDefault(emptyList()) else emptyList()
            _anki.update { it.copy(installed = installed, hasPermission = permitted, decks = decks) }
        }
    }

    /** Switch the read-only sync on (picking the French deck automatically) or off. */
    fun setAnkiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                settingsRepository.saveAnki(false, settings.value.ankiDeck)
                return@launch
            }
            val decks = runCatching { ankiRepository.decks() }.getOrDefault(emptyList())
            val deck = settings.value.ankiDeck.takeIf { name -> decks.any { it.name == name } }
                ?: ankiRepository.guessDeck(decks)?.name
                ?: decks.firstOrNull()?.name
                ?: ""
            settingsRepository.saveAnki(true, deck)
            _anki.update { it.copy(decks = decks.map { d -> d.name }, hasPermission = true) }
            syncAnkiNow()
        }
    }

    fun chooseAnkiDeck(deck: String) {
        viewModelScope.launch {
            settingsRepository.saveAnki(true, deck)
            syncAnkiNow()
        }
    }

    fun syncAnkiNow() {
        viewModelScope.launch {
            _anki.update { it.copy(isSyncing = true) }
            ankiSync.syncIfEnabled()
            _anki.update { it.copy(isSyncing = false) }
        }
    }

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    fun save(geminiKey: String, azureKey: String, azureRegion: String) {
        viewModelScope.launch {
            settingsRepository.save(geminiKey.trim(), azureKey.trim(), azureRegion.trim())
        }
    }

    fun setDailyReviewLimit(limit: Int) {
        viewModelScope.launch { settingsRepository.saveDailyReviewLimit(limit) }
    }

    /** Daily reminder: stored setting + (re)scheduled WorkManager job. */
    fun setReminder(enabled: Boolean, hour: Int) {
        viewModelScope.launch {
            settingsRepository.saveReminder(enabled, hour)
            if (enabled) DailyReminder.schedule(context, hour) else DailyReminder.cancel(context)
        }
    }
}
