package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.HighScore
import com.hubert.data.model.VocabWord
import com.hubert.data.repository.HighScoreRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Gender Snap: "le ou la?"
 * A French noun appears, player taps masculine (le) or feminine (la).
 * Speed-based: correct = +2s, wrong = -5s, streak bonus points.
 */
data class ClassezState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,

    val currentWord: VocabWord? = null,
    val germanHint: String = "",

    // Feedback flash
    val feedback: Boolean? = null,  // true=correct, false=wrong, null=none

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

    // Post-game
    val durationMs: Long = 0L,
    val answerHistory: List<AnswerRecord> = emptyList(),

    // Countdown
    val countdown: Int? = null
)

@HiltViewModel
class ClassezViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClassezState())
    val uiState: StateFlow<ClassezState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var timerDeadline = 0L
    private var nounPool: MutableList<VocabWord> = mutableListOf()
    private var gameStartTime = 0L
    private val answerLog = mutableListOf<AnswerRecord>()
    private val replayPool: ArrayDeque<VocabWord> = ArrayDeque()
    private var currentWordFromReplay = false

    companion object {
        const val GAME_TIME_MS = 60_000L
        const val POINTS_PER_CORRECT = 100
        const val STREAK_BONUS = 25
        const val WRONG_PENALTY_MS = 5_000L
        const val CORRECT_BONUS_MS = 2_000L
    }

    init {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "gender_snap")
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    fun startGame() {
        countdownJob?.cancel()
        timerJob?.cancel()
        answerLog.clear()

        // Build shuffled pool of nouns with known gender
        nounPool = vocabRepository.getNouns().shuffled().toMutableList()

        _uiState.update {
            ClassezState(
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
            gameStartTime = System.currentTimeMillis()
            timerDeadline = System.currentTimeMillis() + GAME_TIME_MS
            _uiState.update {
                it.copy(
                    timeRemainingMs = GAME_TIME_MS,
                    timerFraction = 1f
                )
            }
            showNextWord()
            startTimer()
        }
    }

    private fun showNextWord() {
        val word = if (replayPool.isNotEmpty() && Math.random() < 0.30) {
            currentWordFromReplay = true
            replayPool.removeFirst()
        } else {
            currentWordFromReplay = false
            if (nounPool.isEmpty()) nounPool = vocabRepository.getNouns().shuffled().toMutableList()
            nounPool.removeFirst()
        }
        _uiState.update {
            it.copy(
                currentWord = word,
                germanHint = word.german,
                feedback = null
            )
        }
    }

    fun answer(isMasculine: Boolean) {
        val state = _uiState.value
        if (!state.isPlaying || state.feedback != null) return
        val word = state.currentWord ?: return

        val correctGender = word.gender
        val isCorrect = (isMasculine && correctGender == "m") ||
                (!isMasculine && correctGender == "f")

        val correctArticle = if (correctGender == "m") "le" else "la"
        val yourArticle = if (isMasculine) "le" else "la"
        answerLog.add(
            AnswerRecord(
                question = word.french,
                yourAnswer = "$yourArticle ${word.french}",
                correctAnswer = "$correctArticle ${word.french}",
                isCorrect = isCorrect
            )
        )

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

            if (currentWordFromReplay) {
                replayPool.addLast(word)
            } else if (replayPool.none { it.rank == word.rank }) {
                replayPool.addLast(word)
            }

            _uiState.update {
                it.copy(
                    feedback = false,
                    totalWrong = it.totalWrong + 1,
                    streak = 0
                )
            }

            // Check if penalty killed the timer
            if (timerDeadline <= System.currentTimeMillis()) {
                _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                endGame()
                return
            }
        }

        // Brief feedback flash, then next word
        viewModelScope.launch {
            delay(400)
            if (_uiState.value.isPlaying) {
                showNextWord()
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
        val durationMs = System.currentTimeMillis() - gameStartTime

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore(gameType = "gender_snap")
            val isNewHigh = state.score > previousHigh

            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalCorrect,
                roundsCompleted = state.bestStreak,
                gameType = "gender_snap"
            )

            statisticsRepository.saveSession(
                gameType = "gender_snap",
                score = state.score,
                totalCorrect = state.totalCorrect,
                totalWrong = state.totalWrong,
                bestStreak = state.bestStreak,
                durationMs = durationMs
            )

            statisticsRepository.saveWordAttempts("gender_snap", answerLog)

            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isGameOver = true,
                    isNewHighScore = isNewHigh,
                    highScore = maxOf(it.highScore, state.score),
                    durationMs = durationMs,
                    answerHistory = answerLog.toList()
                )
            }
        }
    }

    fun resetToMenu() {
        timerJob?.cancel()
        countdownJob?.cancel()
        answerLog.clear()
        replayPool.clear()
        _uiState.value = ClassezState()
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "gender_snap")
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        countdownJob?.cancel()
    }
}
