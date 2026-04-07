package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.VocabWord
import com.hubert.data.repository.HighScoreRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Spelling Bee: hear a French word via TTS, type it.
 * The German translation is shown as a hint.
 * Correct = +2s, wrong = -5s, streak bonus points.
 * Accents are ignored for comparison (e.g. "cafe" matches "café").
 */
data class SpellingBeeState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,

    val currentWord: VocabWord? = null,
    val germanHint: String = "",
    val typedText: String = "",

    // Feedback
    val feedback: Boolean? = null,  // true=correct, false=wrong, null=none
    val correctAnswer: String = "", // shown on wrong answer

    // Scoring
    val score: Int = 0,
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val highScore: Int = 0,

    // Timer
    val timeRemainingMs: Long = 0L,
    val timerFraction: Float = 1f,

    // Countdown
    val countdown: Int? = null
)

@HiltViewModel
class SpellingBeeViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpellingBeeState())
    val uiState: StateFlow<SpellingBeeState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var timerDeadline = 0L
    private var wordPool: MutableList<VocabWord> = mutableListOf()

    companion object {
        const val GAME_TIME_MS = 60_000L
        const val POINTS_PER_CORRECT = 200
        const val STREAK_BONUS = 50
        const val WRONG_PENALTY_MS = 5_000L
        const val CORRECT_BONUS_MS = 2_000L
    }

    init {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "spelling_bee")
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    fun startGame() {
        countdownJob?.cancel()
        timerJob?.cancel()

        // Use all words, shuffled
        wordPool = vocabRepository.getAllWords().shuffled().toMutableList()

        _uiState.update {
            SpellingBeeState(
                isPlaying = false,
                countdown = 3,
                highScore = it.highScore
            )
        }

        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                _uiState.update { it.copy(countdown = i) }
                delay(800)
            }
            _uiState.update { it.copy(countdown = null, isPlaying = true) }
            timerDeadline = System.currentTimeMillis() + GAME_TIME_MS
            _uiState.update {
                it.copy(timeRemainingMs = GAME_TIME_MS, timerFraction = 1f)
            }
            showNextWord()
            startTimer()
        }
    }

    private fun showNextWord() {
        if (wordPool.isEmpty()) {
            wordPool = vocabRepository.getAllWords().shuffled().toMutableList()
        }
        val word = wordPool.removeFirst()
        _uiState.update {
            it.copy(
                currentWord = word,
                germanHint = word.german,
                typedText = "",
                feedback = null,
                correctAnswer = ""
            )
        }
        // Speak the word so the player can hear it
        frenchTts.speak(word.french)
    }

    /** Called when user types / edits the text field. */
    fun onTypedTextChanged(text: String) {
        if (!_uiState.value.isPlaying || _uiState.value.feedback != null) return
        _uiState.update { it.copy(typedText = text) }
    }

    /** Replay the current word via TTS. */
    fun replayAudio() {
        val word = _uiState.value.currentWord ?: return
        frenchTts.speak(word.french)
    }

    /** Submit the typed answer. */
    fun submit() {
        val state = _uiState.value
        if (!state.isPlaying || state.feedback != null) return
        val word = state.currentWord ?: return

        val isCorrect = normalize(state.typedText) == normalize(word.french)

        if (isCorrect) {
            val newStreak = state.streak + 1
            val streakBonus = (newStreak - 1) * STREAK_BONUS
            val matchScore = POINTS_PER_CORRECT + streakBonus

            frenchTts.speak(word.french)
            timerDeadline += CORRECT_BONUS_MS

            _uiState.update {
                it.copy(
                    feedback = true,
                    score = it.score + matchScore,
                    totalCorrect = it.totalCorrect + 1,
                    streak = newStreak,
                    bestStreak = maxOf(it.bestStreak, newStreak)
                )
            }
        } else {
            timerDeadline -= WRONG_PENALTY_MS

            _uiState.update {
                it.copy(
                    feedback = false,
                    correctAnswer = word.french,
                    totalWrong = it.totalWrong + 1,
                    streak = 0
                )
            }

            if (timerDeadline <= System.currentTimeMillis()) {
                _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                endGame()
                return
            }
        }

        viewModelScope.launch {
            delay(if (isCorrect) 600 else 1500)
            if (_uiState.value.isPlaying) {
                showNextWord()
            }
        }
    }

    /**
     * Normalize text for comparison: trim, lowercase, strip accents.
     * This makes the game more forgiving — "cafe" matches "café".
     */
    private fun normalize(text: String): String {
        val trimmed = text.trim().lowercase()
        // Decompose unicode then strip combining marks (accents)
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        return decomposed.replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = timerDeadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                    endGame()
                    break
                }
                _uiState.update {
                    it.copy(
                        timeRemainingMs = remaining,
                        timerFraction = remaining.toFloat() / GAME_TIME_MS
                    )
                }
                delay(50)
            }
        }
    }

    private fun endGame() {
        timerJob?.cancel()
        val state = _uiState.value

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore(gameType = "spelling_bee")
            val isNewHigh = state.score > previousHigh

            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalCorrect,
                roundsCompleted = state.bestStreak,
                gameType = "spelling_bee"
            )

            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isGameOver = true,
                    isNewHighScore = isNewHigh,
                    highScore = maxOf(it.highScore, state.score)
                )
            }
        }
    }

    fun resetToMenu() {
        timerJob?.cancel()
        countdownJob?.cancel()
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "spelling_bee")
            _uiState.value = SpellingBeeState(highScore = hs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        frenchTts.shutdown()
    }
}
