package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.repository.AppSettings
import android.content.Context
import com.hubert.data.repository.SettingsRepository
import com.hubert.utils.DailyReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

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

    /** Daily reminder: stored setting + (re)scheduled WorkManager job. */
    fun setReminder(enabled: Boolean, hour: Int) {
        viewModelScope.launch {
            settingsRepository.saveReminder(enabled, hour)
            if (enabled) DailyReminder.schedule(context, hour) else DailyReminder.cancel(context)
        }
    }
}
