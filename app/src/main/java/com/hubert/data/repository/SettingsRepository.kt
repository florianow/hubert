package com.hubert.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.globalSettingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "hubert_global_settings")

data class AppSettings(
    val geminiKey: String = "",
    val azureKey: String = "",
    val azureRegion: String = "",
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val dailyReviewLimit: Int = 150,    // 0 = unlimited
    val ankiEnabled: Boolean = false,   // read-only sync with AnkiDroid
    val ankiDeck: String = ""
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
        private val KEY_REPORT_SEEN_WEEK = stringPreferencesKey("weekly_report_seen_week")
        private val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
        private val KEY_DAILY_REVIEW_LIMIT = intPreferencesKey("daily_review_limit")
        private val KEY_ANKI_ENABLED = booleanPreferencesKey("anki_enabled")
        private val KEY_ANKI_DECK = stringPreferencesKey("anki_deck")
        private val KEY_ANKI_LAST_SYNC = stringPreferencesKey("anki_last_sync")
        private val KEY_ANKI_TODAY_DAY = stringPreferencesKey("anki_today_day")
        private val KEY_ANKI_TODAY_RANKS = stringPreferencesKey("anki_today_ranks")
        private val KEY_ANKI_DONE_RANKS = stringPreferencesKey("anki_done_ranks")
        private val KEY_DUE_REVIEWS_DAY = stringPreferencesKey("due_reviews_day")
        private val KEY_DUE_REVIEWS_COUNT = intPreferencesKey("due_reviews_count")
    }

    /** Monday (ISO date) of the last week whose report was shown automatically. */
    val reportSeenWeek: Flow<String> = context.globalSettingsDataStore.data.map { prefs ->
        prefs[KEY_REPORT_SEEN_WEEK] ?: ""
    }

    suspend fun saveAnki(enabled: Boolean, deck: String) {
        context.globalSettingsDataStore.edit { prefs ->
            prefs[KEY_ANKI_ENABLED] = enabled
            prefs[KEY_ANKI_DECK] = deck
        }
    }

    /** Short summary of the last Anki sync, e.g. "1234 Wörter · 456 sicher". */
    val ankiLastSync: Flow<String> = context.globalSettingsDataStore.data.map { it[KEY_ANKI_LAST_SYNC] ?: "" }

    suspend fun saveAnkiLastSync(summary: String) {
        context.globalSettingsDataStore.edit { prefs -> prefs[KEY_ANKI_LAST_SYNC] = summary }
    }

    /**
     * Anki's queue of [day] (new + learning + review cards as Hubert ranks), taken in the
     * morning so it is still known after the cards were done in Anki. Null for another day.
     */
    suspend fun ankiQueueOn(day: Long): List<Int>? {
        val prefs = context.globalSettingsDataStore.data.first()
        if (prefs[KEY_ANKI_TODAY_DAY] != day.toString()) return null
        return parseRanks(prefs[KEY_ANKI_TODAY_RANKS])
    }

    /** Store Anki's queue for [day]; later snapshots of the same day only add cards. */
    suspend fun saveAnkiQueue(day: Long, ranks: List<Int>) {
        context.globalSettingsDataStore.edit { prefs ->
            val sameDay = prefs[KEY_ANKI_TODAY_DAY] == day.toString()
            val merged = if (sameDay) (parseRanks(prefs[KEY_ANKI_TODAY_RANKS]) + ranks).distinct() else ranks
            prefs[KEY_ANKI_TODAY_DAY] = day.toString()
            prefs[KEY_ANKI_TODAY_RANKS] = merged.joinToString(",")
            if (!sameDay) prefs[KEY_ANKI_DONE_RANKS] = ""
        }
    }

    /** Words of today's Anki queue answered correctly in Hubert. */
    suspend fun ankiDoneOn(day: Long): Set<Int> {
        val prefs = context.globalSettingsDataStore.data.first()
        if (prefs[KEY_ANKI_TODAY_DAY] != day.toString()) return emptySet()
        return parseRanks(prefs[KEY_ANKI_DONE_RANKS]).toSet()
    }

    suspend fun addAnkiDone(day: Long, ranks: Collection<Int>) {
        if (ranks.isEmpty()) return
        context.globalSettingsDataStore.edit { prefs ->
            if (prefs[KEY_ANKI_TODAY_DAY] != day.toString()) return@edit
            val queue = parseRanks(prefs[KEY_ANKI_TODAY_RANKS]).toSet()
            val done = parseRanks(prefs[KEY_ANKI_DONE_RANKS]).toSet() + ranks.filter { it in queue }
            prefs[KEY_ANKI_DONE_RANKS] = done.joinToString(",")
        }
    }

    private fun parseRanks(text: String?): List<Int> =
        text.orEmpty().split(",").mapNotNull { it.toIntOrNull() }

    /** Due reviews done on [day] (epoch day) — what the daily review limit counts. */
    suspend fun dueReviewsOn(day: Long): Int {
        val prefs = context.globalSettingsDataStore.data.first()
        return if (prefs[KEY_DUE_REVIEWS_DAY] == day.toString()) prefs[KEY_DUE_REVIEWS_COUNT] ?: 0 else 0
    }

    suspend fun addDueReviewsToday(day: Long, count: Int) {
        context.globalSettingsDataStore.edit { prefs ->
            val sameDay = prefs[KEY_DUE_REVIEWS_DAY] == day.toString()
            prefs[KEY_DUE_REVIEWS_DAY] = day.toString()
            prefs[KEY_DUE_REVIEWS_COUNT] = (if (sameDay) prefs[KEY_DUE_REVIEWS_COUNT] ?: 0 else 0) + count
        }
    }

    suspend fun saveDailyReviewLimit(limit: Int) {
        context.globalSettingsDataStore.edit { prefs -> prefs[KEY_DAILY_REVIEW_LIMIT] = limit }
    }

    suspend fun saveReminder(enabled: Boolean, hour: Int) {
        context.globalSettingsDataStore.edit { prefs ->
            prefs[KEY_REMINDER_ENABLED] = enabled
            prefs[KEY_REMINDER_HOUR] = hour
        }
    }

    suspend fun markReportSeen(weekStart: String) {
        context.globalSettingsDataStore.edit { prefs -> prefs[KEY_REPORT_SEEN_WEEK] = weekStart }
    }

    val settings: Flow<AppSettings> = context.globalSettingsDataStore.data.map { prefs ->
        AppSettings(
            geminiKey   = prefs[KEY_GEMINI_KEY]   ?: "",
            azureKey    = prefs[KEY_AZURE_KEY]    ?: "",
            azureRegion = prefs[KEY_AZURE_REGION] ?: "",
            reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: false,
            reminderHour = prefs[KEY_REMINDER_HOUR] ?: 19,
            dailyReviewLimit = prefs[KEY_DAILY_REVIEW_LIMIT] ?: 150,
            ankiEnabled = prefs[KEY_ANKI_ENABLED] ?: false,
            ankiDeck = prefs[KEY_ANKI_DECK] ?: ""
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
