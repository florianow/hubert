package com.hubert.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hubert.data.repository.HighScoreRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrepositionQuestion(
    val fr: String,
    val de: String,
    val answer: String,
    val distractors: List<String>
)

data class PreposezState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,

    // Current question
    val sentenceWithGap: String = "",
    val germanTranslation: String = "",
    val choices: List<String> = emptyList(),
    val correctIndex: Int = -1,

    // Feedback
    val selectedIndex: Int? = null,
    val feedback: Boolean? = null,

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
class PreposezViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val highScoreRepository: HighScoreRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreposezState())
    val uiState: StateFlow<PreposezState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var timerDeadline = 0L
    private var gameStartTime = 0L
    private val answerLog = mutableListOf<AnswerRecord>()

    private var allQuestions: List<PrepositionQuestion> = emptyList()
    private var questionPool: MutableList<PrepositionQuestion> = mutableListOf()
    private val replayPool: ArrayDeque<PrepositionQuestion> = ArrayDeque()
    private var currentQuestion: PrepositionQuestion? = null
    private var currentQuestionFromReplay = false

    companion object {
        const val GAME_TIME_MS = 60_000L
        const val POINTS_PER_CORRECT = 150
        const val STREAK_BONUS = 30
        const val WRONG_PENALTY_MS = 5_000L
        const val CORRECT_BONUS_MS = 5_000L
        const val GAME_TYPE = "preposition"
    }

    init {
        loadQuestions()
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = GAME_TYPE)
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    private fun loadQuestions() {
        val json = context.assets.open("prepositions.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<PrepositionQuestion>>() {}.type
        allQuestions = Gson().fromJson(json, type)
    }

    fun startGame() {
        countdownJob?.cancel()
        timerJob?.cancel()
        answerLog.clear()

        questionPool = allQuestions.shuffled().toMutableList()

        _uiState.update {
            PreposezState(
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
            _uiState.update { it.copy(timeRemainingMs = GAME_TIME_MS, timerFraction = 1f) }
            showNextQuestion()
            startTimer()
        }
    }

    private fun showNextQuestion() {
        val question = if (replayPool.isNotEmpty() && Math.random() < 0.30) {
            currentQuestionFromReplay = true
            replayPool.removeFirst()
        } else {
            currentQuestionFromReplay = false
            if (questionPool.isEmpty()) questionPool = allQuestions.shuffled().toMutableList()
            questionPool.removeFirst()
        }
        currentQuestion = question
        val allChoices = (listOf(question.answer) + question.distractors).shuffled()
        val correctIdx = allChoices.indexOf(question.answer)

        _uiState.update {
            it.copy(
                sentenceWithGap = question.fr,
                germanTranslation = question.de,
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

        answerLog.add(
            AnswerRecord(
                question = state.sentenceWithGap,
                yourAnswer = state.choices[choiceIndex],
                correctAnswer = state.choices[state.correctIndex],
                isCorrect = isCorrect
            )
        )

        if (isCorrect) {
            val newStreak = state.streak + 1
            val streakBonus = (newStreak - 1) * STREAK_BONUS
            val matchScore = POINTS_PER_CORRECT + streakBonus

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

            val q = currentQuestion
            if (q != null) {
                if (currentQuestionFromReplay) {
                    replayPool.addLast(q)
                } else if (replayPool.none { it.fr == q.fr }) {
                    replayPool.addLast(q)
                }
            }

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
        val durationMs = System.currentTimeMillis() - gameStartTime

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore(gameType = GAME_TYPE)
            val isNewHigh = state.score > previousHigh

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
        _uiState.value = PreposezState()
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = GAME_TYPE)
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        countdownJob?.cancel()
    }
}
