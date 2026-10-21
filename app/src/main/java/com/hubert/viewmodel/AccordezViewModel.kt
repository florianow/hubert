package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.repository.HighScoreRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Accordez!: past participle agreement (être, avoir with a preceding object, reflexive verbs).
 * Pick the correctly agreed form; the rule is explained after every answer.
 * Timer-based: correct = +3s, wrong = -5s, streak bonus points.
 */
data class AccordezState(
    val isSelection: Boolean = false,
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,
    val selectedGroups: Set<AccordGroup> = AccordGroup.entries.toSet(),

    val question: AccordQuestion? = null,
    val selectedChoice: String? = null,
    val feedback: Boolean? = null,       // true=correct, false=wrong, null=none

    val score: Int = 0,
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val highScore: Int = 0,

    val timeRemainingMs: Long = 0L,
    val timerFraction: Float = 1f,

    val durationMs: Long = 0L,
    val answerHistory: List<AnswerRecord> = emptyList(),
    val countdown: Int? = null
)

@HiltViewModel
class AccordezViewModel @Inject constructor(
    private val highScoreRepository: HighScoreRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccordezState())
    val uiState: StateFlow<AccordezState> = _uiState.asStateFlow()

    private val generator = AccordezQuestionGenerator()
    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var timerDeadline = 0L
    private var gameStartTime = 0L
    private var gameTimeMs = GAME_TIME_MS
    private val answerLog = mutableListOf<AnswerRecord>()
    private val replayPool: ArrayDeque<AccordQuestion> = ArrayDeque()
    private var currentFromReplay = false

    companion object {
        const val GAME_TYPE = "agreement"
        const val GAME_TIME_MS = 60_000L
        const val POINTS_PER_CORRECT = 150
        const val STREAK_BONUS = 25
        const val WRONG_PENALTY_MS = 5_000L
        const val CORRECT_BONUS_MS = 3_000L
    }

    init {
        loadHighScore()
    }

    private fun loadHighScore() {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = GAME_TYPE)
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    fun showSelection() {
        timerJob?.cancel()
        countdownJob?.cancel()
        _uiState.update {
            AccordezState(isSelection = true, selectedGroups = it.selectedGroups, highScore = it.highScore)
        }
    }

    fun toggleGroup(group: AccordGroup) {
        val current = _uiState.value.selectedGroups
        val updated = if (group in current) {
            if (current.size <= 1) current else current - group
        } else current + group
        _uiState.update { it.copy(selectedGroups = updated) }
    }

    fun startGame(stage: StageConfig? = null) {
        countdownJob?.cancel()
        timerJob?.cancel()
        gameTimeMs = stage?.timeMs ?: GAME_TIME_MS
        if (stage != null && stage.grammarFocus.isNotEmpty()) {
            val focus = AccordGroup.entries.filter { it.name in stage.grammarFocus }.toSet()
            if (focus.isNotEmpty()) _uiState.update { it.copy(selectedGroups = focus) }
        }
        answerLog.clear()
        replayPool.clear()

        _uiState.update {
            AccordezState(
                countdown = 3,
                selectedGroups = it.selectedGroups,
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
            timerDeadline = System.currentTimeMillis() + gameTimeMs
            _uiState.update { it.copy(timeRemainingMs = gameTimeMs, timerFraction = 1f) }
            showNextQuestion()
            startTimer()
        }
    }

    private fun showNextQuestion() {
        val question = if (replayPool.isNotEmpty() && Math.random() < 0.30) {
            currentFromReplay = true
            replayPool.removeFirst()
        } else {
            currentFromReplay = false
            generator.next(_uiState.value.selectedGroups)
        }
        _uiState.update {
            it.copy(question = question, selectedChoice = null, feedback = null)
        }
    }

    fun answerChoice(choice: String) {
        val state = _uiState.value
        val question = state.question ?: return
        if (!state.isPlaying || state.feedback != null) return
        _uiState.update { it.copy(selectedChoice = choice) }
        handleAnswer(question, choice, choice == question.answer)
    }

    private fun handleAnswer(question: AccordQuestion, given: String, isCorrect: Boolean) {
        val state = _uiState.value
        answerLog.add(
            AnswerRecord(
                question = "${question.sentence} ${question.hint}",
                yourAnswer = given,
                correctAnswer = question.answer,
                isCorrect = isCorrect,
                explanation = question.rule.explanation
            )
        )

        if (isCorrect) {
            val newStreak = state.streak + 1
            val points = POINTS_PER_CORRECT + (newStreak - 1) * STREAK_BONUS
            timerDeadline += CORRECT_BONUS_MS
            frenchTts.speak(question.solved)
            _uiState.update {
                it.copy(
                    feedback = true,
                    score = it.score + points,
                    totalCorrect = it.totalCorrect + 1,
                    streak = newStreak,
                    bestStreak = maxOf(it.bestStreak, newStreak)
                )
            }
        } else {
            timerDeadline -= WRONG_PENALTY_MS
            if (currentFromReplay || replayPool.none { it.sentence == question.sentence }) {
                replayPool.addLast(question)
            }
            _uiState.update {
                it.copy(feedback = false, totalWrong = it.totalWrong + 1, streak = 0)
            }
            if (timerDeadline <= System.currentTimeMillis()) {
                _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                endGame()
                return
            }
        }

        viewModelScope.launch {
            // Wrong answers stay longer so the rule can be read
            delay(if (isCorrect) 1200 else 3000)
            if (_uiState.value.isPlaying) showNextQuestion()
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
                        timerFraction = (remaining.toFloat() / gameTimeMs).coerceIn(0f, 1f)
                    )
                }
                delay(50)
            }
        }
    }

    private fun endGame() {
        timerJob?.cancel()
        val state = _uiState.value
        if (!state.isPlaying) return
        _uiState.update { it.copy(isPlaying = false) }
        val durationMs = System.currentTimeMillis() - gameStartTime

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore(gameType = GAME_TYPE)
            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalCorrect,
                roundsCompleted = state.bestStreak,
                gameType = GAME_TYPE
            )
            statisticsRepository.saveSession(
                gameType = GAME_TYPE,
                score = state.score,
                totalCorrect = state.totalCorrect,
                totalWrong = state.totalWrong,
                bestStreak = state.bestStreak,
                durationMs = durationMs
            )
            statisticsRepository.saveWordAttempts(GAME_TYPE, answerLog)

            _uiState.update {
                it.copy(
                    isGameOver = true,
                    isNewHighScore = state.score > previousHigh,
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
        _uiState.update { AccordezState(selectedGroups = it.selectedGroups) }
        loadHighScore()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        countdownJob?.cancel()
    }
}
