package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.WordProgress
import com.hubert.data.repository.AnkiSync
import com.hubert.data.repository.LearningRepository
import com.hubert.data.repository.VocabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How far a due word is, by its current review interval. The learning mode quizzes each
 * stage differently: recognise first, then use in context, then recall it yourself.
 */
enum class LernStufe(val label: String) {
    ERKENNEN("erkennen"),      // new, shaky or just forgotten (< 3 days)
    IM_SATZ("im Satz"),        // 3–10 days
    ABRUFEN("abrufen");        // 10+ days: type it from the meaning

    companion object {
        fun of(p: WordProgress): LernStufe = when {
            p.intervalDays < 3.0 -> ERKENNEN
            p.intervalDays < 10.0 -> IM_SATZ
            else -> ABRUFEN
        }
    }
}

/** The games a learning round can use. */
enum class LernSpiel(val title: String, val stufe: LernStufe, val batchSize: Int) {
    TROUVEZ("Trouvez!", LernStufe.ERKENNEN, 20),
    ECOUTEZ("Écoutez!", LernStufe.ERKENNEN, 15),
    COMPLETEZ("Complétez!", LernStufe.IM_SATZ, 10),
    DITES("Dites!", LernStufe.IM_SATZ, 8),    // also the 2nd recall game
    RAPPEL("Rappel", LernStufe.ABRUFEN, 10)
}

/** [stufe] is the stage the words come from (Dites! serves two stages). */
data class LernRunde(val spiel: LernSpiel, val ranks: List<Int>, val stufe: LernStufe = spiel.stufe)

data class RundenErgebnis(val spiel: LernSpiel, val correct: Int, val wrong: Int)

data class LernenState(
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val dueTotal: Int = 0,
    val dueBeyondLimit: Int = 0,          // due, but over today's limit
    val ignoringLimit: Boolean = false,
    val duePerStufe: Map<LernStufe, Int> = emptyMap(),
    val nextRound: LernRunde? = null,
    val results: List<RundenErgebnis> = emptyList()
) {
    val isDone: Boolean get() = !isLoading && dueTotal == 0
    val limitReached: Boolean get() = isDone && dueBeyondLimit > 0
}

/**
 * "Vokabeln lernen": works through today's due words in rounds. The stages take turns (a
 * small stage is not starved by a big one), starting with the one with the most due words.
 * Games per stage alternate: recognition Trouvez!/Écoutez!, context Complétez!/Dites!,
 * recall Rappel/Dites!. Answers update the schedule, so finished words drop out of "fällig"
 * and the next round is planned from what is left.
 */
@HiltViewModel
class LernenViewModel @Inject constructor(
    private val learningRepository: LearningRepository,
    private val vocabRepository: VocabRepository,
    private val ankiSync: AnkiSync
) : ViewModel() {

    private val _uiState = MutableStateFlow(LernenState())
    val uiState: StateFlow<LernenState> = _uiState.asStateFlow()

    private var recognitionRounds = 0
    private var contextRounds = 0
    private var recallRounds = 0
    private var lastStufe: LernStufe? = null
    private var ignoreLimit = false

    companion object {
        const val DUE_SCAN_LIMIT = 2000
    }

    fun start() {
        recognitionRounds = 0
        contextRounds = 0
        recallRounds = 0
        lastStufe = null
        ignoreLimit = false
        _uiState.value = LernenState(isRunning = true, isLoading = true)
        viewModelScope.launch {
            // With Anki: make sure today's Anki queue is known (normally taken in the morning)
            ankiSync.ensureTodayQueue()
            planNextRound()
        }
    }

    /** Called by the navigation layer when a round's game is over. */
    fun onRoundFinished(answers: List<AnswerRecord>) {
        val round = _uiState.value.nextRound ?: return
        val correct = answers.count { it.isCorrect }
        _uiState.update {
            it.copy(
                isLoading = true,
                results = it.results + RundenErgebnis(round.spiel, correct, answers.size - correct)
            )
        }
        if (round.stufe == LernStufe.ERKENNEN) recognitionRounds++
        if (round.stufe == LernStufe.IM_SATZ) contextRounds++
        if (round.stufe == LernStufe.ABRUFEN) recallRounds++
        lastStufe = round.stufe
        planNextRound()
    }

    /** "Trotzdem weiter": keep going past today's review limit for this session. */
    fun continuePastLimit() {
        ignoreLimit = true
        _uiState.update { it.copy(isLoading = true, ignoringLimit = true) }
        planNextRound()
    }

    fun stop() {
        _uiState.value = LernenState()
    }

    /** First round: the biggest stage; after that the stages with due words take turns. */
    private fun nextStufe(withDue: List<LernStufe>, size: (LernStufe) -> Int): LernStufe? {
        val last = lastStufe ?: return withDue.maxByOrNull(size)
        return withDue.firstOrNull { it.ordinal > last.ordinal } ?: withDue.firstOrNull()
    }

    private fun planNextRound() {
        viewModelScope.launch {
            val due = learningRepository.dueProgress(DUE_SCAN_LIMIT, ignoreDailyLimit = ignoreLimit)
            val beyondLimit = if (ignoreLimit) 0 else learningRepository.overview().dueBeyondLimit
            val byStufe = due.groupBy { LernStufe.of(it) }.mapValues { (_, list) -> list.map { it.rank } }.toMutableMap()

            // Complétez! needs one of its gap sentences; Dites! works with every word (deck examples)
            val withSentences = vocabRepository.getWordsWithSentences().map { it.rank }.toSet()

            val stufe = nextStufe(LernStufe.entries.filter { byStufe[it].orEmpty().isNotEmpty() }) { byStufe[it].orEmpty().size }
            val round = stufe?.let {
                val words = byStufe[it].orEmpty()
                val completezWords = words.filter { r -> r in withSentences }
                val spiel = when (it) {
                    LernStufe.ERKENNEN -> if (recognitionRounds % 2 == 0) LernSpiel.TROUVEZ else LernSpiel.ECOUTEZ
                    // "im Satz" alternates between Complétez! and Dites!
                    LernStufe.IM_SATZ -> if (contextRounds % 2 == 0 && completezWords.isNotEmpty()) LernSpiel.COMPLETEZ else LernSpiel.DITES
                    LernStufe.ABRUFEN -> if (recallRounds % 2 == 0) LernSpiel.RAPPEL else LernSpiel.DITES
                }
                val batch = if (spiel == LernSpiel.COMPLETEZ) completezWords else words
                LernRunde(spiel, batch.take(spiel.batchSize), it)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    dueTotal = due.size,
                    dueBeyondLimit = beyondLimit,
                    duePerStufe = LernStufe.entries.associateWith { s -> byStufe[s].orEmpty().size },
                    nextRound = round
                )
            }
        }
    }
}
