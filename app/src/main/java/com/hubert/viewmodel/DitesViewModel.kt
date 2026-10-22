package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.VocabWord
import com.hubert.data.repository.LearningRepository
import com.hubert.data.repository.SettingsRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.AudioRecorder
import com.hubert.utils.AzurePronunciationApi
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** One card: a word with an example sentence (when the deck has one). */
data class DitesCard(
    val word: VocabWord,
    val sentenceFr: String?,
    val sentenceDe: String?,
    val blank: String?          // the word as it appears in the sentence (highlighted)
) {
    /** What is spoken and assessed: the sentence, or the word itself without one. */
    val spokenText: String get() = sentenceFr ?: word.french
}

data class DitesState(
    val isActive: Boolean = false,
    val isFinished: Boolean = false,
    val cards: List<DitesCard> = emptyList(),
    val index: Int = 0,
    val isRevealed: Boolean = false,
    val hasAzure: Boolean = false,
    val isRecording: Boolean = false,
    val isAssessing: Boolean = false,
    val pronScore: Int? = null,                                   // 0–100
    val wordScores: List<AzurePronunciationApi.WordResult> = emptyList(),
    val error: String? = null,
    val knownCount: Int = 0,
    val unknownCount: Int = 0,
    val pronScores: List<Int> = emptyList()
) {
    val current: DitesCard? get() = cards.getOrNull(index)
    val averagePronScore: Int? get() = pronScores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

/**
 * Dites!: the Anki routine with pronunciation feedback. See the German word and sentence,
 * translate it in your head, reveal, speak the French sentence and get it scored by Azure,
 * then say whether you knew it. "Wusste ich" counts as recall in Hubert's schedule (never
 * written to Anki).
 */
@HiltViewModel
class DitesViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val learningRepository: LearningRepository,
    private val statisticsRepository: StatisticsRepository,
    private val settingsRepository: SettingsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(DitesState())
    val uiState: StateFlow<DitesState> = _uiState.asStateFlow()

    private var recorder: AudioRecorder? = null
    private var recordingJob: Job? = null
    private val answerLog = mutableListOf<AnswerRecord>()
    private var startTime = 0L

    companion object {
        const val GAME_TYPE = "phrases"
        const val CARDS_PER_SESSION = 10
    }

    fun start() {
        answerLog.clear()
        startTime = System.currentTimeMillis()
        _uiState.value = DitesState(isActive = true)
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            // Due words first, then learned ones — preferring words that have an example sentence
            val ranks = learningRepository.practiceRanks(CARDS_PER_SESSION * 4)
            val withSentence = vocabRepository.getWordsWithSentences().map { it.rank }.toSet()
            val ordered = ranks.filter { it in withSentence } + ranks.filter { it !in withSentence }
            val cards = ordered.take(CARDS_PER_SESSION).mapNotNull { rank ->
                val word = vocabRepository.getWordByRank(rank) ?: return@mapNotNull null
                val sentence = vocabRepository.getRandomSentence(rank)
                DitesCard(word, sentence?.fr, sentence?.de, sentence?.blank)
            }
            _uiState.update {
                it.copy(cards = cards, hasAzure = settings.hasAzure, isFinished = cards.isEmpty())
            }
        }
    }

    fun reveal() {
        val card = _uiState.value.current ?: return
        _uiState.update { it.copy(isRevealed = true) }
        frenchTts.speak(card.spokenText)
    }

    fun listen() {
        _uiState.value.current?.let { frenchTts.speak(it.spokenText) }
    }

    // ── Speaking ────────────────────────────────────────────────────────

    fun toggleRecording() {
        val state = _uiState.value
        if (!state.isRevealed || !state.hasAzure || state.isAssessing) return
        if (state.isRecording) stopAndAssess() else startRecording()
    }

    private fun startRecording() {
        recorder = AudioRecorder()
        _uiState.update { it.copy(isRecording = true, error = null, pronScore = null, wordScores = emptyList()) }
        recordingJob = viewModelScope.launch { recorder?.startRecording() }
    }

    private fun stopAndAssess() {
        val wav = recorder?.stop() ?: return
        recorder = null
        recordingJob?.cancel()
        val card = _uiState.value.current ?: return
        _uiState.update { it.copy(isRecording = false, isAssessing = true) }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            runCatching {
                AzurePronunciationApi.assess(settings.azureRegion, settings.azureKey, card.spokenText, wav)
            }.onSuccess { result ->
                val score = result.pronScore.roundToInt()
                _uiState.update {
                    it.copy(isAssessing = false, pronScore = score, wordScores = result.words, pronScores = it.pronScores + score)
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isAssessing = false, error = "Bewertung fehlgeschlagen: ${e.message ?: "Netzwerk"}") }
            }
        }
    }

    // ── Self rating ─────────────────────────────────────────────────────

    fun rate(knewIt: Boolean) {
        val state = _uiState.value
        val card = state.current ?: return
        if (!state.isRevealed || state.isRecording || state.isAssessing) return
        answerLog += AnswerRecord(
            question = "${card.word.german} →",
            yourAnswer = if (knewIt) card.word.french else "—",
            correctAnswer = card.word.french,
            isCorrect = knewIt,
            explanation = card.sentenceFr ?: "",
            rank = card.word.rank
        )
        val next = state.index + 1
        _uiState.update {
            it.copy(
                knownCount = it.knownCount + if (knewIt) 1 else 0,
                unknownCount = it.unknownCount + if (knewIt) 0 else 1,
                index = next,
                isRevealed = false,
                pronScore = null,
                wordScores = emptyList(),
                error = null
            )
        }
        if (next >= state.cards.size) finish()
    }

    private fun finish() {
        val state = _uiState.value
        viewModelScope.launch {
            statisticsRepository.saveSession(
                gameType = GAME_TYPE,
                score = state.knownCount * 100 + (state.averagePronScore ?: 0),
                totalCorrect = state.knownCount,
                totalWrong = state.unknownCount,
                bestStreak = 0,
                durationMs = System.currentTimeMillis() - startTime
            )
            // Updates Hubert's schedule ("Wusste ich" = recall); nothing goes to Anki
            statisticsRepository.saveWordAttempts(GAME_TYPE, answerLog)
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    fun reset() {
        recorder?.release()
        recorder = null
        recordingJob?.cancel()
        answerLog.clear()
        _uiState.value = DitesState()
    }

    override fun onCleared() {
        super.onCleared()
        recorder?.release()
    }
}
