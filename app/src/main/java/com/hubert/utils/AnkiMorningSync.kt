package com.hubert.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hubert.data.repository.AnkiSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Takes today's Anki queue early each morning (after Anki's day starts at 4:00), so
 * "Vokabeln lernen" knows the day's words even when they were done in Anki before Hubert
 * was opened. Read-only; does nothing while the Anki sync is off.
 */
object AnkiMorningSync {

    private const val WORK_NAME = "anki_morning_sync"
    private const val HOUR = 4
    private const val MINUTE = 30

    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(HOUR, MINUTE)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<AnkiMorningWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AnkiMorningEntryPoint {
    fun ankiSync(): AnkiSync
}

class AnkiMorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, AnkiMorningEntryPoint::class.java)
            .ankiSync()
            .snapshotQueueIfEnabled()
        return Result.success()
    }
}
