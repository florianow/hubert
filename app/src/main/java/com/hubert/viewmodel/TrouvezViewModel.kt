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
 * Game state for the matching screen.
 *
 * 6 French words on the left, 6 German words on the right (shuffled).
 * Match pairs by tapping one French + one German word.
 * Matched pairs fade out. After 2 pairs are greyed out, the oldest gets
 * replaced with a new word (rolling replacement — always fresh words coming in).
 * Wrong match = -5 seconds penalty. Timer runs out = game over.
 */
data class TrouvezState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,

    // Current 6 word slots (always 6 visible on each side)
    val frenchWords: List<WordItem> = emptyList(),
    val germanWords: List<WordItem> = emptyList(),

    // Selection state
    val selectedFrench: Int? = null,  // index (0-5)
    val selectedGerman: Int? = null,  // index (0-5)

    // Per-slot feedback: index -> true (correct flash) or false (wrong flash)
    val frenchFeedback: Map<Int, Boolean> = emptyMap(),
    val germanFeedback: Map<Int, Boolean> = emptyMap(),
    // Penalty feedback shown in UI
    val showPenalty: Boolean = false,

    // Scoring
    val score: Int = 0,
    val totalMatches: Int = 0,
    val streak: Int = 0,          // consecutive correct matches (for bonus)
    val bestStreak: Int = 0,
    val highScore: Int = 0,

    // Timer
    val timeRemainingMs: Long = 0L,
    val totalTimeMs: Long = 0L,
    val timerFraction: Float = 1f,

    // Post-game
    val durationMs: Long = 0L,
    val answerHistory: List<AnswerRecord> = emptyList(),

    // Countdown before game starts
    val countdown: Int? = null
)

data class WordItem(
    val text: String,
    val pairId: Int,  // links French to German (same pairId = correct match)
    val rank: Int = -1,  // vocab word rank, used for replay pool
    val ipa: String? = null,  // IPA pronunciation (French cards only)
    val matched: Boolean = false  // greyed out after a correct match
)

@HiltViewModel
class TrouvezViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrouvezState())
    val uiState: StateFlow<TrouvezState> = _uiState.asStateFlow()

    val topScores: StateFlow<List<HighScore>> = highScoreRepository.getTopScores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var timerJob: Job? = null
    private var countdownJob: Job? = null

    // Track used pairIds so we don't repeat words in one session
    private var nextPairId = 0
    // Deadline timestamp for the timer (allows easy penalty subtraction)
    private var timerDeadline = 0L
    // Track wall-clock start time for statistics
    private var gameStartTime = 0L
    // Track per-question answers for post-game review
    private val answerLog = mutableListOf<AnswerRecord>()

    companion object {
        const val SLOTS = 5
        const val GAME_TIME_MS = 30_000L      // 30 seconds total
        const val POINTS_PER_MATCH = 100
        const val STREAK_BONUS = 25           // extra points per streak level
        const val WRONG_PENALTY_MS = 2_000L   // lose 2 seconds on wrong match
        const val CLEAR_BOARD_BONUS_MS = 5_000L  // gain 5 seconds when all pairs cleared
        const val MAX_GREYED_OUT = 3         // after this many greyed-out pairs, oldest gets replaced
    }

    // Queue of greyed-out matched pairs: Pair(frenchIndex, germanIndex), oldest first
    private val matchedQueue = mutableListOf<Pair<Int, Int>>()
    // Words that had at least one wrong attempt — added to replayPool once correctly matched
    private val wrongAttemptedRanks: MutableSet<Int> = mutableSetOf()
    private val replayPool: ArrayDeque<VocabWord> = ArrayDeque()

    init {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore()
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    fun startGame() {
        countdownJob?.cancel()
        timerJob?.cancel()
        nextPairId = 0
        answerLog.clear()
        matchedQueue.clear()
        wrongAttemptedRanks.clear()
        replayPool.clear()

        _uiState.update {
            TrouvezState(
                isPlaying = false,
                countdown = 3,
                highScore = it.highScore
            )
        }

        // 3-2-1-GO countdown
        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                _uiState.update { it.copy(countdown = i) }
                delay(800)
            }
            _uiState.update { it.copy(countdown = null, isPlaying = true) }
            gameStartTime = System.currentTimeMillis()
            initBoard()
        }
    }

    /**
     * Set up the initial 6x2 board and start the timer.
     */
    private fun initBoard() {
        val words = vocabRepository.getRandomWords(SLOTS)

        val frenchItems = words.mapIndexed { index, word ->
            WordItem(text = word.french, pairId = nextPairId + index, rank = word.rank, ipa = word.ipa)
        }
        val germanItems = words.mapIndexed { index, word ->
            WordItem(text = word.german, pairId = nextPairId + index, rank = word.rank)
        }.shuffled()

        nextPairId += SLOTS

        _uiState.update {
            it.copy(
                frenchWords = frenchItems,
                germanWords = germanItems,
                selectedFrench = null,
                selectedGerman = null,
                frenchFeedback = emptyMap(),
                germanFeedback = emptyMap(),
                timeRemainingMs = GAME_TIME_MS,
                totalTimeMs = GAME_TIME_MS,
                timerFraction = 1f
            )
        }

        timerDeadline = System.currentTimeMillis() + GAME_TIME_MS
        startTimer()
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

    fun selectFrench(index: Int) {
        val state = _uiState.value
        if (!state.isPlaying) return
        if (state.frenchWords[index].matched) return  // fading out, not selectable

        // Deselect if tapping the same one
        if (state.selectedFrench == index) {
            _uiState.update { it.copy(selectedFrench = null) }
            return
        }

        _uiState.update { it.copy(selectedFrench = index) }

        // If German is also selected, check match
        val germanIdx = _uiState.value.selectedGerman
        if (germanIdx != null) {
            checkMatch(index, germanIdx)
        }
    }

    fun selectGerman(index: Int) {
        val state = _uiState.value
        if (!state.isPlaying) return
        if (state.germanWords[index].matched) return  // fading out, not selectable

        // Deselect if tapping the same one
        if (state.selectedGerman == index) {
            _uiState.update { it.copy(selectedGerman = null) }
            return
        }

        _uiState.update { it.copy(selectedGerman = index) }

        // If French is also selected, check match
        val frenchIdx = _uiState.value.selectedFrench
        if (frenchIdx != null) {
            checkMatch(frenchIdx, index)
        }
    }

    private fun checkMatch(frenchIndex: Int, germanIndex: Int) {
        val state = _uiState.value
        val frenchItem = state.frenchWords[frenchIndex]
        val germanItem = state.germanWords[germanIndex]

        val isCorrect = frenchItem.pairId == germanItem.pairId

        // Find the actual correct German word for this French word
        val correctGerman = if (isCorrect) germanItem.text
            else state.germanWords.firstOrNull { it.pairId == frenchItem.pairId }?.text ?: "?"

        answerLog.add(
            AnswerRecord(
                question = frenchItem.text,
                yourAnswer = germanItem.text,
                correctAnswer = correctGerman,
                isCorrect = isCorrect
            )
        )

        if (isCorrect) {
            val newStreak = state.streak + 1
            val streakBonus = (newStreak - 1) * STREAK_BONUS
            val matchScore = POINTS_PER_MATCH + streakBonus

            // If this word had wrong attempts before, queue it for replay in a future slot
            if (frenchItem.rank >= 0 && wrongAttemptedRanks.remove(frenchItem.rank)) {
                val word = vocabRepository.getWordByRank(frenchItem.rank)
                if (word != null && replayPool.none { it.rank == frenchItem.rank }) {
                    replayPool.addLast(word)
                }
            }

            // Speak the French word aloud
            frenchTts.speak(frenchItem.text)

            // Mark both cards as matched (faded out in UI, not clickable)
            _uiState.update {
                val updatedFrench = it.frenchWords.toMutableList()
                val updatedGerman = it.germanWords.toMutableList()
                updatedFrench[frenchIndex] = updatedFrench[frenchIndex].copy(matched = true)
                updatedGerman[germanIndex] = updatedGerman[germanIndex].copy(matched = true)

                it.copy(
                    selectedFrench = null,
                    selectedGerman = null,
                    frenchWords = updatedFrench,
                    germanWords = updatedGerman,
                    // frenchFeedback = it.frenchFeedback + (frenchIndex to true),
                    germanFeedback = it.germanFeedback + (germanIndex to true),
                    score = it.score + matchScore,
                    totalMatches = it.totalMatches + 1,
                    streak = newStreak,
                    bestStreak = maxOf(it.bestStreak, newStreak)
                )
            }

            // Track this greyed-out pair
            matchedQueue.add(Pair(frenchIndex, germanIndex))

            // All pairs matched -> bonus time + fresh board!
            val allMatched = _uiState.value.frenchWords.all { it.matched }
            if (allMatched) {
                timerDeadline += CLEAR_BOARD_BONUS_MS
                viewModelScope.launch {
                    delay(600)
                    matchedQueue.clear()
                    loadNewBoard()
                }
            }

            // Clear correct feedback after a short flash
            viewModelScope.launch {
                delay(400)
                _uiState.update {
                    it.copy(
                        frenchFeedback = it.frenchFeedback - frenchIndex,
                        germanFeedback = it.germanFeedback - germanIndex
                    )
                }
            }
        } else {
            // Wrong match: remember this word was hard — will be replayed after it's correctly matched
            if (frenchItem.rank >= 0) wrongAttemptedRanks.add(frenchItem.rank)

            timerDeadline -= WRONG_PENALTY_MS

            _uiState.update {
                it.copy(
                    selectedFrench = null,
                    selectedGerman = null,
                    frenchFeedback = it.frenchFeedback + (frenchIndex to false),
                    germanFeedback = it.germanFeedback + (germanIndex to false),
                    showPenalty = true,
                    streak = 0  // reset streak
                )
            }

            // Check if penalty killed us
            if (timerDeadline <= System.currentTimeMillis()) {
                _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                endGame()
                return
            }

            viewModelScope.launch {
                delay(500)
                _uiState.update {
                    it.copy(
                        frenchFeedback = it.frenchFeedback - frenchIndex,
                        germanFeedback = it.germanFeedback - germanIndex,
                        showPenalty = false
                    )
                }
            }
        }
    }

    /**
     * Load a completely new set of word pairs (all slots).
     * Score, streak, and timer continue.
     */
    private fun loadNewBoard() {
        val words = vocabRepository.getRandomWords(SLOTS)

        val frenchItems = words.mapIndexed { index, word ->
            WordItem(text = word.french, pairId = nextPairId + index, rank = word.rank, ipa = word.ipa)
        }
        val germanItems = words.mapIndexed { index, word ->
            WordItem(text = word.german, pairId = nextPairId + index, rank = word.rank)
        }.shuffled()

        nextPairId += SLOTS

        _uiState.update {
            it.copy(
                frenchWords = frenchItems,
                germanWords = germanItems,
                selectedFrench = null,
                selectedGerman = null,
                frenchFeedback = emptyMap(),
                germanFeedback = emptyMap()
            )
        }
    }

    /**
     * Replace the oldest greyed-out matched pair with a new word.
     */
    private fun replaceOldestMatch() {
        if (matchedQueue.isEmpty()) return
        val (oldFrenchIdx, oldGermanIdx) = matchedQueue.removeFirst()

        val newWord = if (replayPool.isNotEmpty() && Math.random() < 0.30) {
            replayPool.removeFirst()
        } else {
            vocabRepository.getRandomWords(1).first()
        }
        val newPairId = nextPairId++

        val newFrench = WordItem(text = newWord.french, pairId = newPairId, rank = newWord.rank, ipa = newWord.ipa)
        val newGerman = WordItem(text = newWord.german, pairId = newPairId, rank = newWord.rank)

        _uiState.update {
            val updatedFrench = it.frenchWords.toMutableList()
            val updatedGerman = it.germanWords.toMutableList()

            updatedFrench[oldFrenchIdx] = newFrench
            updatedGerman[oldGermanIdx] = newGerman

            it.copy(
                frenchWords = updatedFrench,
                germanWords = updatedGerman,
                frenchFeedback = it.frenchFeedback - oldFrenchIdx,
                germanFeedback = it.germanFeedback - oldGermanIdx
            )
        }
    }

    private fun endGame() {
        timerJob?.cancel()
        val state = _uiState.value
        val durationMs = System.currentTimeMillis() - gameStartTime

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore()
            val isNewHigh = state.score > previousHigh

            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalMatches,
                roundsCompleted = state.bestStreak
            )

            statisticsRepository.saveSession(
                gameType = "matching",
                score = state.score,
                totalCorrect = state.totalMatches,
                totalWrong = 0,  // matching game doesn't track wrong separately, uses time penalty
                bestStreak = state.bestStreak,
                durationMs = durationMs
            )

            statisticsRepository.saveWordAttempts("matching", answerLog)

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
        wrongAttemptedRanks.clear()
        matchedQueue.clear()
        _uiState.value = TrouvezState()
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore()
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        countdownJob?.cancel()
    }
}
