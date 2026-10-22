package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.WordProgress
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
    RAPPEL("Rappel", LernStufe.ABRUFEN, 10)
}

data class LernRunde(val spiel: LernSpiel, val ranks: List<Int>)

data class RundenErgebnis(val spiel: LernSpiel, val correct: Int, val wrong: Int)

data class LernenState(
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val dueTotal: Int = 0,
    val duePerStufe: Map<LernStufe, Int> = emptyMap(),
    val nextRound: LernRunde? = null,
    val results: List<RundenErgebnis> = emptyList()
) {
    val isDone: Boolean get() = !isLoading && dueTotal == 0
}

/**
 * "Vokabeln lernen": works through today's due words in rounds. Each round takes the stage
 * with the most due words and the matching game (recognition: Trouvez!/Écoutez! alternating,
 * context: Complétez!, recall: Rappel). Answers update the schedule, so finished words drop
 * out of "fällig" and the next round is planned from what is left.
 */
@HiltViewModel
class LernenViewModel @Inject constructor(
    private val learningRepository: LearningRepository,
    private val vocabRepository: VocabRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LernenState())
    val uiState: StateFlow<LernenState> = _uiState.asStateFlow()

    private var recognitionRounds = 0

    companion object {
        const val DUE_SCAN_LIMIT = 2000
    }

    fun start() {
        recognitionRounds = 0
        _uiState.value = LernenState(isRunning = true, isLoading = true)
        planNextRound()
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
        if (round.spiel.stufe == LernStufe.ERKENNEN) recognitionRounds++
        planNextRound()
    }

    fun stop() {
        _uiState.value = LernenState()
    }

    private fun planNextRound() {
        viewModelScope.launch {
            val due = learningRepository.dueProgress(DUE_SCAN_LIMIT)
            val byStufe = due.groupBy { LernStufe.of(it) }.mapValues { (_, list) -> list.map { it.rank } }.toMutableMap()

            // Complétez! needs an example sentence; other "im Satz" words are recalled instead
            val withSentences = vocabRepository.getWordsWithSentences().map { it.rank }.toSet()
            byStufe[LernStufe.IM_SATZ]?.let { context ->
                val (ok, noSentence) = context.partition { it in withSentences }
                byStufe[LernStufe.IM_SATZ] = ok
                byStufe[LernStufe.ABRUFEN] = byStufe[LernStufe.ABRUFEN].orEmpty() + noSentence
            }

            val stufe = LernStufe.entries
                .filter { byStufe[it].orEmpty().isNotEmpty() }
                .maxByOrNull { byStufe[it].orEmpty().size }
            val round = stufe?.let {
                val spiel = when (it) {
                    LernStufe.ERKENNEN -> if (recognitionRounds % 2 == 0) LernSpiel.TROUVEZ else LernSpiel.ECOUTEZ
                    LernStufe.IM_SATZ -> LernSpiel.COMPLETEZ
                    LernStufe.ABRUFEN -> LernSpiel.RAPPEL
                }
                LernRunde(spiel, byStufe[it].orEmpty().take(spiel.batchSize))
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    dueTotal = due.size,
                    duePerStufe = LernStufe.entries.associateWith { s -> byStufe[s].orEmpty().size },
                    nextRound = round
                )
            }
        }
    }
}
