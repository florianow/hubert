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
 * Nombrez!: numbers, clock times, dates, days and months.
 * Mixes typed questions (hear a number → type the digits) and 4-choice questions.
 * Timer-based: correct = +3s, wrong = -5s, streak bonus points.
 */
data class NombrezState(
    val isSelection: Boolean = false,
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,
    val selectedTopics: Set<NombrezTopic> = NombrezTopic.entries.toSet(),

    val question: NombrezQuestion? = null,
    val typedText: String = "",
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
class NombrezViewModel @Inject constructor(
    private val highScoreRepository: HighScoreRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(NombrezState())
    val uiState: StateFlow<NombrezState> = _uiState.asStateFlow()

    private val generator = NombrezQuestionGenerator()
    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var timerDeadline = 0L
    private var gameStartTime = 0L
    private var gameTimeMs = GAME_TIME_MS
    private val answerLog = mutableListOf<AnswerRecord>()
    private val replayPool: ArrayDeque<NombrezQuestion> = ArrayDeque()
    private var currentFromReplay = false

    companion object {
        const val GAME_TYPE = "numbers"
        const val GAME_TIME_MS = 60_000L
        const val POINTS_CHOICE = 100
        const val POINTS_TYPED = 150
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
            NombrezState(isSelection = true, selectedTopics = it.selectedTopics, highScore = it.highScore)
        }
    }

    fun toggleTopic(topic: NombrezTopic) {
        val current = _uiState.value.selectedTopics
        val updated = if (topic in current) {
            if (current.size <= 1) current else current - topic
        } else current + topic
        _uiState.update { it.copy(selectedTopics = updated) }
    }

    fun startGame(stage: StageConfig? = null) {
        countdownJob?.cancel()
        timerJob?.cancel()
        gameTimeMs = stage?.timeMs ?: GAME_TIME_MS
        answerLog.clear()
        replayPool.clear()

        _uiState.update {
            NombrezState(
                countdown = 3,
                selectedTopics = it.selectedTopics,
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
            generator.next(_uiState.value.selectedTopics)
        }
        _uiState.update {
            it.copy(question = question, typedText = "", selectedChoice = null, feedback = null)
        }
        if (question.audioOnly) question.speech?.let { frenchTts.speak(it) }
    }

    fun replayAudio() {
        _uiState.value.question?.speech?.let { frenchTts.speak(it) }
    }

    fun onTypedTextChanged(text: String) {
        val state = _uiState.value
        if (!state.isPlaying || state.feedback != null) return
        // Allow thinking in groups ("250 300") but keep digits only
        _uiState.update { it.copy(typedText = text.filter { c -> c.isDigit() || c == ' ' }.take(12)) }
    }

    fun submitTyped() {
        val state = _uiState.value
        val question = state.question ?: return
        if (!state.isPlaying || state.feedback != null || !question.isTyped) return
        val typed = state.typedText.filter { it.isDigit() }.trimStart('0').ifEmpty { if (state.typedText.isNotBlank()) "0" else "" }
        if (typed.isEmpty()) return
        handleAnswer(question, typed, typed == question.answer)
    }

    fun answerChoice(choice: String) {
        val state = _uiState.value
        val question = state.question ?: return
        if (!state.isPlaying || state.feedback != null || question.isTyped) return
        _uiState.update { it.copy(selectedChoice = choice) }
        handleAnswer(question, choice, choice == question.answer)
    }

    private fun handleAnswer(question: NombrezQuestion, given: String, isCorrect: Boolean) {
        val state = _uiState.value
        answerLog.add(
            AnswerRecord(
                question = if (question.isTyped) "🔊 ${question.prompt}" else question.prompt,
                yourAnswer = given,
                correctAnswer = question.answer,
                isCorrect = isCorrect,
                explanation = question.reveal
            )
        )

        if (isCorrect) {
            val newStreak = state.streak + 1
            val points = (if (question.isTyped) POINTS_TYPED else POINTS_CHOICE) + (newStreak - 1) * STREAK_BONUS
            timerDeadline += CORRECT_BONUS_MS
            // Choice questions with visible text: say it after answering so the player hears it too
            if (!question.audioOnly) question.speech?.let { frenchTts.speak(it) }
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
            if (currentFromReplay || replayPool.none { it.prompt == question.prompt }) {
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
            // Wrong answers stay longer so the written form can be read
            delay(if (isCorrect) 900 else 2200)
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
        _uiState.update { NombrezState(selectedTopics = it.selectedTopics) }
        loadHighScore()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        countdownJob?.cancel()
    }
}
