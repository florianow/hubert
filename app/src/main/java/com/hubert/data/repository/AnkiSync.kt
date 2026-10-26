package com.hubert.data.repository

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the read-only Anki sync when it is enabled and allowed; returns a short summary.
 * Besides the learning state it takes today's Anki queue, which "Vokabeln lernen" works
 * through — taken on app start and early each morning, so it is known before (and after)
 * the cards were done in Anki.
 */
@Singleton
class AnkiSync @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val learningRepository: LearningRepository,
    private val vocabRepository: VocabRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun syncIfEnabled(): String? {
        val settings = settingsRepository.settings.first()
        if (!settings.ankiEnabled || settings.ankiDeck.isBlank()) return null
        if (!ankiRepository.isInstalled() || !ankiRepository.hasPermission()) return null
        return runCatching {
            val rankByFrench = vocabRepository.getAllWords().associate { it.french to it.rank }
            val cards = ankiRepository.cards(settings.ankiDeck, rankByFrench)
            val (started, known) = learningRepository.applyAnki(cards)
            val queue = snapshotQueue(settings.ankiDeck, rankByFrench)
            val summary = "${cards.size} Karten erkannt · $started begonnen · $known sicher · heute $queue fällig"
            settingsRepository.saveAnkiLastSync(summary)
            summary
        }.getOrElse { e ->
            val summary = "Abgleich fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
            settingsRepository.saveAnkiLastSync(summary)
            summary
        }
    }

    /** Only today's queue (morning job / start of "Vokabeln lernen"); false if not possible. */
    suspend fun snapshotQueueIfEnabled(): Boolean {
        val settings = settingsRepository.settings.first()
        if (!settings.ankiEnabled || settings.ankiDeck.isBlank()) return false
        if (!ankiRepository.isInstalled() || !ankiRepository.hasPermission()) return false
        return runCatching {
            snapshotQueue(settings.ankiDeck, vocabRepository.getAllWords().associate { it.french to it.rank })
        }.isSuccess
    }

    /** Takes today's queue unless there already is one for today. */
    suspend fun ensureTodayQueue() {
        if (settingsRepository.ankiQueueOn(learningRepository.today()) == null) snapshotQueueIfEnabled()
    }

    /**
     * Stores today's Anki queue (merged with an earlier snapshot of the day); returns its size.
     * Cards already done in Anki today count too — otherwise a first snapshot taken after the
     * Anki session would be empty and every word would look done in Hubert.
     */
    private suspend fun snapshotQueue(deck: String, rankByFrench: Map<String, Int>): Int {
        val day = learningRepository.today()
        val rankByNote = ankiRepository.noteRanks(deck, rankByFrench)
        val queue = ankiRepository.queuedRanks(deck, rankByNote)
        val doneInAnki = runCatching { ankiRepository.reviewedTodayRanks(deck, rankByNote) }.getOrDefault(emptyList())
        val ranks = (queue + doneInAnki).distinct()
        val needs = runCatching { ankiRepository.todayNeeds(deck, rankByNote, ranks) }.getOrDefault(emptyMap())
        settingsRepository.saveAnkiQueue(day, ranks, needs)
        return settingsRepository.ankiQueueOn(day)?.size ?: 0
    }
}
