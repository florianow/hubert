package com.hubert.viewmodel

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.SentenceEntry
import com.hubert.data.repository.HighScoreRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.AudioRecorder
import com.hubert.utils.AzurePronunciationApi
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// DataStore for Azure settings
private val Context.azureDataStore: DataStore<Preferences> by preferencesDataStore(name = "azure_settings")

/**
 * Word-level pronunciation result for UI display.
 */
data class WordScore(
    val word: String,
    val accuracyScore: Double,
    val errorType: String
)

/**
 * Statistics tracked across the run for the game-over screen.
 */
data class RunStats(
    val sentencesAttempted: Int = 0,
    val sentencesCorrect: Int = 0,
    val sentencesWrong: Int = 0,
    val totalPronScore: Double = 0.0,
    val bestPronScore: Double = 0.0,
    val worstPronScore: Double = 100.0,
    val bestSentence: String = "",
    val worstSentence: String = "",
    val wordErrors: MutableMap<String, Int> = mutableMapOf()  // word -> error count
) {
    val avgPronScore: Double
        get() = if (sentencesAttempted > 0) totalPronScore / sentencesAttempted else 0.0
}

/**
 * Prononcez! — French pronunciation game.
 *
 * The player reads French sentences aloud. Azure Pronunciation Assessment
 * evaluates accuracy. PronScore >= 95 = correct, < 95 = wrong.
 *
 * Adaptive difficulty: streak drives sentence length.
 *   - Streak 0-2: <= 6 words (Facile)
 *   - Streak 3-5: 7-10 words (Moyen)
 *   - Streak 6+:  11+ words (Difficile)
 *
 * Points system: same as Conjuguez!
 *   - Start with 10 points
 *   - Correct: +3 base + streak bonus
 *   - Wrong: -5 points
 *   - Game over when points hit 0
 */
data class PronunciationState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,

    // Settings / setup
    val needsApiKey: Boolean = false,
    val azureKey: String = "",
    val azureRegion: String = "",
    val showSettings: Boolean = false,

    // Current sentence
    val sentenceFr: String = "",
    val sentenceDe: String = "",
    val highlightWord: String = "",   // The key vocab word to highlight

    // Recording state
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,  // waiting for Azure response
    val errorMessage: String? = null,

    // Results for current sentence
    val pronScore: Double? = null,
    val wordScores: List<WordScore> = emptyList(),
    val feedback: Boolean? = null,      // true=correct (>=95), false=wrong

    // Scoring
    val points: Int = STARTING_POINTS,
    val score: Int = 0,
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val highScore: Int = 0,

    // Difficulty
    val difficultyLabel: String = "Facile",

    // Manual advance — true when feedback is shown and player needs to tap "Next"
    val awaitingNext: Boolean = false,

    // Countdown
    val countdown: Int? = null,

    // Run stats (shown on game over)
    val runStats: RunStats = RunStats()
) {
    companion object {
        const val STARTING_POINTS = 10
        const val PASS_THRESHOLD = 95.0
    }
}

@HiltViewModel
class PronunciationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(PronunciationState())
    val uiState: StateFlow<PronunciationState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var audioRecorder: AudioRecorder? = null
    private var recordingJob: Job? = null
    private var gameStartTime = 0L

    // Sentence pools by difficulty tier
    private var easyPool: MutableList<Pair<Int, SentenceEntry>> = mutableListOf()
    private var mediumPool: MutableList<Pair<Int, SentenceEntry>> = mutableListOf()
    private var hardPool: MutableList<Pair<Int, SentenceEntry>> = mutableListOf()

    private var runStats = RunStats()

    companion object {
        const val POINTS_PER_CORRECT = 3
        const val STREAK_BONUS = 1
        const val WRONG_PENALTY = 5

        private val KEY_AZURE_KEY = stringPreferencesKey("azure_speech_key")
        private val KEY_AZURE_REGION = stringPreferencesKey("azure_speech_region")
    }

    init {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "pronunciation")
            _uiState.update { it.copy(highScore = hs) }
        }
        // Load saved Azure settings
        viewModelScope.launch {
            context.azureDataStore.data.first().let { prefs ->
                val key = prefs[KEY_AZURE_KEY] ?: ""
                val region = prefs[KEY_AZURE_REGION] ?: ""
                _uiState.update { it.copy(azureKey = key, azureRegion = region) }
            }
        }
    }

    /**
     * Called when user taps the Prononcez! card on the menu.
     * If no API key is configured, show settings dialog.
     * Otherwise, start the game.
     */
    fun onGameSelected() {
        val state = _uiState.value
        if (state.azureKey.isBlank() || state.azureRegion.isBlank()) {
            _uiState.update { it.copy(needsApiKey = true, showSettings = true) }
        } else {
            startGame()
        }
    }

    /**
     * Save Azure settings and optionally proceed to game.
     */
    fun saveSettings(key: String, region: String) {
        val wasWaitingForKey = _uiState.value.needsApiKey
        viewModelScope.launch {
            context.azureDataStore.edit { prefs ->
                prefs[KEY_AZURE_KEY] = key
                prefs[KEY_AZURE_REGION] = region
            }
            _uiState.update {
                it.copy(
                    azureKey = key,
                    azureRegion = region,
                    showSettings = false,
                    needsApiKey = false
                )
            }
            // If we were waiting for key to start, start now
            if (wasWaitingForKey && key.isNotBlank() && region.isNotBlank()) {
                startGame()
            }
        }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun dismissSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun startGame() {
        countdownJob?.cancel()
        runStats = RunStats()

        // Show countdown immediately while loading sentences in background
        _uiState.update {
            PronunciationState(
                isPlaying = false,
                countdown = 3,
                points = PronunciationState.STARTING_POINTS,
                highScore = it.highScore,
                azureKey = it.azureKey,
                azureRegion = it.azureRegion
            )
        }

        countdownJob = viewModelScope.launch {
            // Build sentence pools on IO thread during countdown
            withContext(Dispatchers.IO) {
                val allSentences = vocabRepository.getAllSentencesFlat()
                easyPool = allSentences.filter { (_, e) ->
                    e.fr.split("\\s+".toRegex()).size <= 6
                }.shuffled().toMutableList()
                mediumPool = allSentences.filter { (_, e) ->
                    val wc = e.fr.split("\\s+".toRegex()).size
                    wc in 7..10
                }.shuffled().toMutableList()
                hardPool = allSentences.filter { (_, e) ->
                    e.fr.split("\\s+".toRegex()).size >= 11
                }.shuffled().toMutableList()
            }

            for (i in 3 downTo 1) {
                _uiState.update { it.copy(countdown = i) }
                delay(800)
            }
            _uiState.update { it.copy(countdown = null, isPlaying = true) }
            gameStartTime = System.currentTimeMillis()
            showNextSentence()
        }
    }

    private suspend fun showNextSentence() {
        val state = _uiState.value
        val streak = state.streak

        // Pick from appropriate pool based on streak
        val (pool, label) = when {
            streak >= 6 -> Triple(hardPool, "Difficile", null)
            streak >= 3 -> Triple(mediumPool, "Moyen", null)
            else -> Triple(easyPool, "Facile", null)
        }.let { Pair(it.first, it.second) }

        // Refill pool if empty (on IO thread)
        if (pool.isEmpty()) {
            withContext(Dispatchers.IO) {
                val allSentences = vocabRepository.getAllSentencesFlat()
                when (label) {
                    "Facile" -> {
                        easyPool = allSentences.filter { (_, e) ->
                            e.fr.split("\\s+".toRegex()).size <= 6
                        }.shuffled().toMutableList()
                    }
                    "Moyen" -> {
                        mediumPool = allSentences.filter { (_, e) ->
                            val wc = e.fr.split("\\s+".toRegex()).size
                            wc in 7..10
                        }.shuffled().toMutableList()
                    }
                    "Difficile" -> {
                        hardPool = allSentences.filter { (_, e) ->
                            e.fr.split("\\s+".toRegex()).size >= 11
                        }.shuffled().toMutableList()
                    }
                }
            }
        }

        val activePool = when (label) {
            "Facile" -> easyPool
            "Moyen" -> mediumPool
            else -> hardPool
        }

        if (activePool.isEmpty()) return  // should never happen

        val (_, entry) = activePool.removeFirst()

        _uiState.update {
            it.copy(
                sentenceFr = entry.fr,
                sentenceDe = entry.de,
                highlightWord = entry.blank,
                isRecording = false,
                isProcessing = false,
                pronScore = null,
                wordScores = emptyList(),
                feedback = null,
                errorMessage = null,
                difficultyLabel = label
            )
        }
    }

    /**
     * Called when the player taps "Next" after seeing feedback.
     */
    fun nextSentence() {
        if (!_uiState.value.awaitingNext) return
        _uiState.update { it.copy(awaitingNext = false) }
        viewModelScope.launch {
            showNextSentence()
        }
    }

    /**
     * Start or stop recording.
     */
    fun toggleRecording() {
        val state = _uiState.value
        if (!state.isPlaying || state.isProcessing || state.feedback != null) return

        if (state.isRecording) {
            stopRecordingAndAssess()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        audioRecorder = AudioRecorder()
        _uiState.update { it.copy(isRecording = true, errorMessage = null) }

        recordingJob = viewModelScope.launch {
            audioRecorder?.startRecording()
        }
    }

    private fun stopRecordingAndAssess() {
        val wavData = audioRecorder?.stop() ?: return
        audioRecorder = null
        recordingJob?.cancel()

        _uiState.update { it.copy(isRecording = false, isProcessing = true) }

        viewModelScope.launch {
            try {
                val state = _uiState.value
                val result = AzurePronunciationApi.assess(
                    region = state.azureRegion,
                    apiKey = state.azureKey,
                    referenceText = state.sentenceFr,
                    audioWav = wavData
                )

                val wordScores = result.words.map { w ->
                    WordScore(
                        word = w.word,
                        accuracyScore = w.accuracyScore,
                        errorType = w.errorType
                    )
                }

                val isCorrect = result.pronScore >= PronunciationState.PASS_THRESHOLD

                // Update run stats
                runStats = runStats.copy(
                    sentencesAttempted = runStats.sentencesAttempted + 1,
                    sentencesCorrect = runStats.sentencesCorrect + if (isCorrect) 1 else 0,
                    sentencesWrong = runStats.sentencesWrong + if (!isCorrect) 1 else 0,
                    totalPronScore = runStats.totalPronScore + result.pronScore,
                    bestPronScore = maxOf(runStats.bestPronScore, result.pronScore),
                    worstPronScore = minOf(runStats.worstPronScore, result.pronScore),
                    bestSentence = if (result.pronScore > runStats.bestPronScore)
                        state.sentenceFr else runStats.bestSentence,
                    worstSentence = if (result.pronScore < runStats.worstPronScore)
                        state.sentenceFr else runStats.worstSentence
                )

                // Track mispronounced words
                for (ws in wordScores) {
                    if (ws.errorType != "None") {
                        runStats.wordErrors[ws.word.lowercase()] =
                            (runStats.wordErrors[ws.word.lowercase()] ?: 0) + 1
                    }
                }

                // Speak the sentence correctly (so user hears how it should sound)
                frenchTts.speak(state.sentenceFr)

                if (isCorrect) {
                    val newStreak = state.streak + 1
                    val streakBonus = if (newStreak >= 2) (newStreak - 1) * STREAK_BONUS else 0
                    val pointsGained = POINTS_PER_CORRECT + streakBonus

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            pronScore = result.pronScore,
                            wordScores = wordScores,
                            feedback = true,
                            points = it.points + pointsGained,
                            score = it.score + pointsGained,
                            totalCorrect = it.totalCorrect + 1,
                            streak = newStreak,
                            bestStreak = maxOf(it.bestStreak, newStreak),
                            runStats = runStats
                        )
                    }
                } else {
                    val newPoints = (state.points - WRONG_PENALTY).coerceAtLeast(0)

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            pronScore = result.pronScore,
                            wordScores = wordScores,
                            feedback = false,
                            points = newPoints,
                            totalWrong = it.totalWrong + 1,
                            streak = 0,
                            runStats = runStats
                        )
                    }

                    if (newPoints <= 0) {
                        delay(2000)
                        endGame()
                        return@launch
                    }
                }

                // Wait for player to tap "Next" (manual advance)
                _uiState.update { it.copy(awaitingNext = true) }

            } catch (e: Exception) {
                Log.e("PronunciationVM",
                    "Assessment failed [${e.javaClass.simpleName}]: ${e.message}" +
                        (e.cause?.let { " caused by [${it.javaClass.simpleName}]: ${it.message}" } ?: ""),
                    e)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Assessment failed"}"
                    )
                }
            }
        }
    }

    private fun endGame() {
        val state = _uiState.value
        val durationMs = System.currentTimeMillis() - gameStartTime

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore(gameType = "pronunciation")
            val isNewHigh = state.score > previousHigh

            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalCorrect,
                roundsCompleted = state.bestStreak,
                gameType = "pronunciation"
            )

            statisticsRepository.saveSession(
                gameType = "pronunciation",
                score = state.score,
                totalCorrect = state.totalCorrect,
                totalWrong = state.totalWrong,
                bestStreak = state.bestStreak,
                durationMs = durationMs
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

    fun speak(text: String) {
        frenchTts.speak(text)
    }

    fun resetToMenu() {
        countdownJob?.cancel()
        recordingJob?.cancel()
        audioRecorder?.release()
        audioRecorder = null

        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "pronunciation")
            _uiState.value = PronunciationState(
                highScore = hs,
                azureKey = _uiState.value.azureKey,
                azureRegion = _uiState.value.azureRegion
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        recordingJob?.cancel()
        audioRecorder?.release()
    }
}
