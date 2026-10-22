package com.hubert.data.repository

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Runs the read-only Anki sync when it is enabled and allowed; returns a short summary. */
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
            val summary = "${cards.size} Karten erkannt · $started begonnen · $known sicher"
            settingsRepository.saveAnkiLastSync(summary)
            summary
        }.getOrElse { e ->
            val summary = "Abgleich fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
            settingsRepository.saveAnkiLastSync(summary)
            summary
        }
    }
}
