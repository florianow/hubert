package com.hubert

import android.app.Application
import com.hubert.data.repository.AnkiSync
import com.hubert.data.repository.LearningRepository
import com.hubert.data.repository.SettingsRepository
import com.hubert.utils.AnkiMorningSync
import com.hubert.utils.DailyReminder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HubertApplication : Application() {

    @Inject lateinit var learningRepository: LearningRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ankiSync: AnkiSync

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // One-time import of past answers into the spaced-repetition schedule (no-op afterwards)
        appScope.launch {
            learningRepository.seedFromHistoryIfNeeded()
            // Read-only: adapt Hubert's schedule to the Anki deck (no-op when the sync is off)
            ankiSync.syncIfEnabled()
        }
        // Take Anki's queue each morning, before the cards are done in Anki
        AnkiMorningSync.schedule(this)
        // Re-anchor the daily reminder to the chosen hour (survives updates and time changes)
        appScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.reminderEnabled) DailyReminder.schedule(this@HubertApplication, settings.reminderHour)
        }
    }
}
