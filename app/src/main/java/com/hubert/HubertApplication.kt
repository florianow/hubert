package com.hubert

import android.app.Application
import com.hubert.data.repository.LearningRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HubertApplication : Application() {

    @Inject lateinit var learningRepository: LearningRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // One-time import of past answers into the spaced-repetition schedule (no-op afterwards)
        appScope.launch { learningRepository.seedFromHistoryIfNeeded() }
    }
}
