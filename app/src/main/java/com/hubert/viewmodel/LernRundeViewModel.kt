package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.VocabWord
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

/** The two untimed vocab rounds of "Vokabeln lernen". */
enum class LernRundeKind(val gameType: String, val title: String) {
    LISTEN("listening", "Écoutez!"),     // hear the French word → pick the German meaning
    RECALL("recall", "Rappel")           // see the German meaning → type the French word
}

data class LernRundeState(
    val isActive: Boolean = false,
    val isFinished: Boolean = false,
    val kind: LernRundeKind = LernRundeKind.LISTEN,
    val words: List<VocabWord> = emptyList(),
    val index: Int = 0,
    val choices: List<String> = emptyList(),   // LISTEN: German options
    val typedText: String = "",                // RECALL
    val selectedChoice: String? = null,
    val feedback: Boolean? = null,             // null = not answered yet
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val answerHistory: List<AnswerRecord> = emptyList()
) {
    val current: VocabWord? get() = words.getOrNull(index)
}

/**
 * Écoutez! and Rappel: one pass over a fixed batch of words chosen by the learning mode.
 * Answers update the spaced-repetition schedule like every vocab game (listening counts as
 * recognition, typing the word from its meaning as recall).
 */
@HiltViewModel
class LernRundeViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(LernRundeState())
    val uiState: StateFlow<LernRundeState> = _uiState.asStateFlow()

    private val answerLog = mutableListOf<AnswerRecord>()
    private var startTime = 0L

    fun start(kind: LernRundeKind, ranks: List<Int>) {
        val words = ranks.mapNotNull { vocabRepository.getWordByRank(it) }
        answerLog.clear()
        startTime = System.currentTimeMillis()
        _uiState.value = LernRundeState(isActive = true, kind = kind, words = words)
        showQuestion()
    }

    private fun showQuestion() {
        val word = _uiState.value.current ?: return finish()
        val choices = if (_uiState.value.kind == LernRundeKind.LISTEN) {
            val distractors = vocabRepository.getCategoryDistractors(word.rank, 6)
                .map { it.german }
                .filter { it != word.german }
                .distinct()
                .take(3)
            (distractors + word.german).shuffled()
        } else emptyList()
        _uiState.update { it.copy(choices = choices, typedText = "", selectedChoice = null, feedback = null) }
        if (_uiState.value.kind == LernRundeKind.LISTEN) frenchTts.speak(word.french)
    }

    fun speak() {
        _uiState.value.current?.let { frenchTts.speak(it.french) }
    }

    // ── Écoutez! ────────────────────────────────────────────────────────

    fun answerChoice(choice: String) {
        val state = _uiState.value
        val word = state.current ?: return
        if (state.feedback != null) return
        val correct = choice == word.german
        record(word, given = choice, expected = word.german, correct = correct, question = word.french)
        _uiState.update { it.copy(selectedChoice = choice, feedback = correct) }
        viewModelScope.launch {
            delay(if (correct) 900 else 1800)
            next()
        }
    }

    // ── Rappel ──────────────────────────────────────────────────────────

    fun onTypedTextChanged(text: String) {
        if (_uiState.value.feedback != null) return
        _uiState.update { it.copy(typedText = text) }
    }

    fun submitTyped() {
        val state = _uiState.value
        val word = state.current ?: return
        if (state.feedback != null || state.typedText.isBlank()) return
        // Any French word with the same German meaning counts (e.g. two words for "Haus")
        val accepted = vocabRepository.getAllWords()
            .filter { it.german == word.german }
            .map { normalize(it.french) }
            .toSet()
        val correct = normalize(state.typedText) in accepted
        record(word, given = state.typedText.trim(), expected = word.french, correct = correct, question = "${word.german} →")
        _uiState.update { it.copy(feedback = correct) }
        frenchTts.speak(word.french)
    }

    /** Rappel advances manually so the solution can be read. */
    fun next() {
        val state = _uiState.value
        if (!state.isActive || state.isFinished) return
        if (state.index + 1 >= state.words.size) return finish()
        _uiState.update { it.copy(index = it.index + 1) }
        showQuestion()
    }

    private fun record(word: VocabWord, given: String, expected: String, correct: Boolean, question: String) {
        answerLog += AnswerRecord(
            question = question,
            yourAnswer = given,
            correctAnswer = expected,
            isCorrect = correct,
            rank = word.rank
        )
        _uiState.update {
            it.copy(
                totalCorrect = it.totalCorrect + if (correct) 1 else 0,
                totalWrong = it.totalWrong + if (correct) 0 else 1
            )
        }
    }

    private fun finish() {
        val state = _uiState.value
        if (state.isFinished) return
        viewModelScope.launch {
            statisticsRepository.saveSession(
                gameType = state.kind.gameType,
                score = state.totalCorrect * 100,
                totalCorrect = state.totalCorrect,
                totalWrong = state.totalWrong,
                bestStreak = 0,
                durationMs = System.currentTimeMillis() - startTime
            )
            // Also updates the spaced-repetition schedule
            statisticsRepository.saveWordAttempts(state.kind.gameType, answerLog)
            _uiState.update { it.copy(isFinished = true, answerHistory = answerLog.toList()) }
        }
    }

    fun reset() {
        answerLog.clear()
        _uiState.value = LernRundeState()
    }

    companion object {
        // Only a separate article ("la maison", "l'homme") — never the start of a word ("lever")
        private val ARTICLES = Regex("^(?:(?:le|la|les|un|une|des)\\s+|l')")

        /** Lowercase, no accents, no leading article: "L'Été" → "ete", "la maison" → "maison". */
        fun normalize(text: String): String {
            val lower = text.trim().lowercase().replace('’', '\'')
            val noArticle = lower.replace(ARTICLES, "")
            return Normalizer.normalize(noArticle, Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
