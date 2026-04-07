package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.SentenceEntry
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
 * Gap Fill: a French sentence with a blanked-out word.
 * German translation shown as hint. Pick from 4 choices (same category).
 * Correct = +2s, wrong = -5s.
 */
data class GapFillState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,

    // Current question
    val sentenceWithGap: String = "",   // French sentence with "___" replacing the blank
    val germanTranslation: String = "", // Full German sentence
    val choices: List<String> = emptyList(), // 4 answer choices
    val correctIndex: Int = -1,         // index of correct answer in choices

    // Feedback
    val selectedIndex: Int? = null,     // which the player tapped
    val feedback: Boolean? = null,      // true=correct, false=wrong

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
class GapFillViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(GapFillState())
    val uiState: StateFlow<GapFillState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var timerDeadline = 0L

    // Pool of available questions (rank -> word)
    private var questionPool: MutableList<Int> = mutableListOf()

    companion object {
        const val GAME_TIME_MS = 60_000L
        const val POINTS_PER_CORRECT = 150
        const val STREAK_BONUS = 30
        const val WRONG_PENALTY_MS = 5_000L
        const val CORRECT_BONUS_MS = 2_000L
        const val NUM_CHOICES = 4
    }

    init {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "gap_fill")
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    fun startGame() {
        countdownJob?.cancel()
        timerJob?.cancel()

        // Build pool of words that have sentences
        val wordsWithSentences = vocabRepository.getWordsWithSentences()
        questionPool = wordsWithSentences.map { it.rank }.shuffled().toMutableList()

        _uiState.update {
            GapFillState(
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
            showNextQuestion()
            startTimer()
        }
    }

    private fun showNextQuestion() {
        if (questionPool.isEmpty()) {
            val wordsWithSentences = vocabRepository.getWordsWithSentences()
            questionPool = wordsWithSentences.map { it.rank }.shuffled().toMutableList()
        }

        val targetRank = questionPool.removeFirst()
        val targetWord = vocabRepository.getWordByRank(targetRank) ?: return
        val sentence = vocabRepository.getRandomSentence(targetRank) ?: return

        // Create the gap: replace the blank word with "___"
        val gapSentence = sentence.fr.replace(sentence.blank, "___")

        // Build choices: correct + 3 distractors from same category
        val distractors = vocabRepository.getCategoryDistractors(targetRank, NUM_CHOICES - 1)
        val distractorTexts = distractors.map { it.french }

        val allChoices = (listOf(sentence.blank) + distractorTexts).shuffled()
        val correctIdx = allChoices.indexOf(sentence.blank)

        _uiState.update {
            it.copy(
                sentenceWithGap = gapSentence,
                germanTranslation = sentence.de,
                choices = allChoices,
                correctIndex = correctIdx,
                selectedIndex = null,
                feedback = null
            )
        }
    }

    fun answer(choiceIndex: Int) {
        val state = _uiState.value
        if (!state.isPlaying || state.feedback != null) return

        val isCorrect = choiceIndex == state.correctIndex

        if (isCorrect) {
            val newStreak = state.streak + 1
            val streakBonus = (newStreak - 1) * STREAK_BONUS
            val matchScore = POINTS_PER_CORRECT + streakBonus

            // Speak the correct word
            frenchTts.speak(state.choices[choiceIndex])
            timerDeadline += CORRECT_BONUS_MS

            _uiState.update {
                it.copy(
                    selectedIndex = choiceIndex,
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
                    selectedIndex = choiceIndex,
                    feedback = false,
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
            delay(if (isCorrect) 600 else 1000)
            if (_uiState.value.isPlaying) {
                showNextQuestion()
            }
        }
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
            val previousHigh = highScoreRepository.getHighestScore(gameType = "gap_fill")
            val isNewHigh = state.score > previousHigh

            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalCorrect,
                roundsCompleted = state.bestStreak,
                gameType = "gap_fill"
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
            val hs = highScoreRepository.getHighestScore(gameType = "gap_fill")
            _uiState.value = GapFillState(highScore = hs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        countdownJob?.cancel()
    }
}
