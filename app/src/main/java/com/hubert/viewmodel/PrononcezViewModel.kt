package com.hubert.viewmodel

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.SentenceEntry
import com.hubert.data.repository.HighScoreRepository
import com.hubert.data.repository.SettingsRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.AudioRecorder
import com.hubert.utils.AzurePronunciationApi
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
 * Timer system: 90 s base, +10 s correct, −10 s wrong, 300 s cap.
 * Timer pauses during recording, Azure processing, feedback, and retry prompts.
 */
data class PrononcezState(
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

    // Timer
    val timeRemainingMs: Long = 0L,
    val timerFraction: Float = 1f,

    // Scoring
    val score: Int = 0,
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val highScore: Int = 0,

    // Difficulty
    val difficultyLabel: String = "Facile",

    // Second chance — score 80-94 on first attempt allows a retry at lower threshold (85)
    val isRetry: Boolean = false,       // true if this is the second attempt on the same sentence
    val canRetry: Boolean = false,      // true when first attempt scored 80-94 and player can try again

    // Playback of user's own recording
    val isPlayingRecording: Boolean = false,

    // Manual advance — true when feedback is shown and player needs to tap "Next"
    val awaitingNext: Boolean = false,

    // Countdown
    val countdown: Int? = null,

    // Run stats (shown on game over)
    val runStats: RunStats = RunStats()
) {
    companion object {
        const val PASS_THRESHOLD = 95.0
        const val RETRY_THRESHOLD = 80.0       // 80-94 on first attempt = can retry
        const val RETRY_PASS_THRESHOLD = 85.0  // lower bar on second attempt
    }
}

@HiltViewModel
class PrononcezViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrononcezState())
    val uiState: StateFlow<PrononcezState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var timerJob: Job? = null
    private var timerDeadline = 0L
    private var timerPausedRemaining = 0L
    private var audioRecorder: AudioRecorder? = null
    private var recordingJob: Job? = null
    private var gameStartTime = 0L
    private var lastRecordingWav: ByteArray? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    // Sentence pools by difficulty tier
    private var easyPool: MutableList<Pair<Int, SentenceEntry>> = mutableListOf()
    private var mediumPool: MutableList<Pair<Int, SentenceEntry>> = mutableListOf()
    private var hardPool: MutableList<Pair<Int, SentenceEntry>> = mutableListOf()
    private val replayPool: ArrayDeque<Pair<Int, SentenceEntry>> = ArrayDeque()
    private var currentSentenceEntry: Pair<Int, SentenceEntry>? = null

    private var runStats = RunStats()

    companion object {
        const val GAME_TIME_MS = 90_000L
        const val MAX_TIME_MS = GAME_TIME_MS
        const val CORRECT_BONUS_MS = 10_000L
        const val WRONG_PENALTY_MS = 10_000L
        const val POINTS_PER_CORRECT = 150
        const val STREAK_BONUS = 30

    }

    init {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "pronunciation")
            _uiState.update { it.copy(highScore = hs) }
        }
        // Observe shared settings — update state whenever keys change
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _uiState.update { it.copy(azureKey = s.azureKey, azureRegion = s.azureRegion) }
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

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun dismissSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun dismissNeedsApiKey() {
        _uiState.update { it.copy(needsApiKey = false, showSettings = false) }
    }

    /** Called by MainActivity after settings were saved, to proceed to game if we were waiting. */
    fun onSettingsSaved() {
        val s = _uiState.value
        _uiState.update { it.copy(showSettings = false, needsApiKey = false) }
        if (s.needsApiKey && s.azureKey.isNotBlank() && s.azureRegion.isNotBlank()) {
            startGame()
        }
    }

    fun startGame() {
        countdownJob?.cancel()
        timerJob?.cancel()
        runStats = RunStats()
        replayPool.clear()
        currentSentenceEntry = null

        // Show countdown immediately while loading sentences in background
        _uiState.update {
            PrononcezState(
                isPlaying = false,
                countdown = 3,
                timeRemainingMs = GAME_TIME_MS,
                timerFraction = 1f,
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
            timerDeadline = System.currentTimeMillis() + GAME_TIME_MS
            _uiState.update {
                it.copy(timeRemainingMs = GAME_TIME_MS, timerFraction = 1f)
            }
            startTimer()
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

        val pair = if (replayPool.isNotEmpty() && Math.random() < 0.30) {
            replayPool.removeFirst()
        } else {
            activePool.removeFirst()
        }
        currentSentenceEntry = pair
        val (_, entry) = pair

        _uiState.update {
            it.copy(
                sentenceFr = entry.fr,
                sentenceDe = entry.de,
                highlightWord = entry.blank,
                isRecording = false,
                isProcessing = false,
                isRetry = false,
                canRetry = false,
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
     * Resumes the timer.
     */
    fun nextSentence() {
        if (!_uiState.value.awaitingNext) return
        stopPlayback()
        lastRecordingWav = null
        _uiState.update { it.copy(awaitingNext = false, isRetry = false, canRetry = false) }
        resumeTimer()
        viewModelScope.launch {
            showNextSentence()
        }
    }

    /**
     * Called when the player taps "TRY AGAIN" after scoring 80-94 on the first attempt.
     * Resets recording state so they can re-record the same sentence.
     * Resumes the timer (it will pause again when they start recording).
     */
    fun retryRecording() {
        if (!_uiState.value.canRetry) return
        _uiState.update {
            it.copy(
                isRetry = true,
                canRetry = false,
                pronScore = null,
                wordScores = emptyList(),
                feedback = null,
                errorMessage = null
            )
        }
        resumeTimer()
    }

    /**
     * Called when the player taps "SKIP" during the retry prompt (skip without re-recording).
     * Counts as wrong.
     */
    fun skipRetry() {
        if (!_uiState.value.canRetry) return
        val state = _uiState.value

        val cur = currentSentenceEntry
        if (cur != null && replayPool.none { it.second.fr == cur.second.fr }) {
            replayPool.addLast(cur)
        }

        // Apply time penalty (timer is paused, so adjust paused remaining)
        timerPausedRemaining = (timerPausedRemaining - WRONG_PENALTY_MS).coerceAtLeast(0L)

        // Count this as a decided attempt in run stats
        runStats = runStats.copy(
            sentencesAttempted = runStats.sentencesAttempted + 1,
            sentencesWrong = runStats.sentencesWrong + 1,
            totalPronScore = runStats.totalPronScore + (state.pronScore ?: 0.0),
            worstPronScore = minOf(runStats.worstPronScore, state.pronScore ?: 100.0),
            worstSentence = if ((state.pronScore ?: 100.0) < runStats.worstPronScore)
                state.sentenceFr else runStats.worstSentence
        )

        _uiState.update {
            it.copy(
                feedback = false,
                canRetry = false,
                totalWrong = it.totalWrong + 1,
                streak = 0,
                runStats = runStats,
                awaitingNext = true
            )
        }

        // Check if time penalty killed the timer
        if (timerPausedRemaining <= 0L) {
            _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
            viewModelScope.launch {
                delay(1200)
                endGame()
            }
        }
    }

    /**
     * Start or stop recording. Pauses the timer during recording.
     */
    fun toggleRecording() {
        val state = _uiState.value
        if (!state.isPlaying || state.isProcessing || state.feedback != null) return

        if (state.isRecording) {
            stopRecordingAndAssess()
        } else {
            pauseTimer()
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

        // Store the recording for playback after assessment
        lastRecordingWav = wavData

        _uiState.update { it.copy(isRecording = false, isProcessing = true, isPlayingRecording = false) }

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

                val isRetry = state.isRetry
                val passThreshold = if (isRetry) {
                    PrononcezState.RETRY_PASS_THRESHOLD
                } else {
                    PrononcezState.PASS_THRESHOLD
                }

                val isCorrect = result.pronScore >= passThreshold
                val canRetry = !isRetry
                    && !isCorrect
                    && result.pronScore >= PrononcezState.RETRY_THRESHOLD

                // Update run stats (only count as an "attempt" on the deciding result,
                // not on a retry-eligible first attempt)
                if (isCorrect || !canRetry) {
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
                }

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

                    // Add time bonus (timer is paused, so adjust paused remaining), capped at MAX_TIME_MS
                    timerPausedRemaining = (timerPausedRemaining + CORRECT_BONUS_MS).coerceAtMost(MAX_TIME_MS)

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            pronScore = result.pronScore,
                            wordScores = wordScores,
                            feedback = true,
                            canRetry = false,
                            score = it.score + pointsGained,
                            totalCorrect = it.totalCorrect + 1,
                            streak = newStreak,
                            bestStreak = maxOf(it.bestStreak, newStreak),
                            runStats = runStats,
                            awaitingNext = true
                        )
                    }
                    // Timer stays paused — will resume when player taps "Next"
                } else if (canRetry) {
                    // Score 80-94 on first attempt: show results but allow retry
                    // Timer stays paused — will resume when player taps "Try Again" or "Skip"
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            pronScore = result.pronScore,
                            wordScores = wordScores,
                            feedback = null,  // not yet decided — show as "try again"
                            canRetry = true,
                            runStats = runStats
                        )
                    }
                } else {
                    // Definitive wrong: < 80 on first attempt, or < 85 on retry
                    val cur = currentSentenceEntry
                    if (cur != null && replayPool.none { it.second.fr == cur.second.fr }) {
                        replayPool.addLast(cur)
                    }

                    timerPausedRemaining = (timerPausedRemaining - WRONG_PENALTY_MS).coerceAtLeast(0L)

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            pronScore = result.pronScore,
                            wordScores = wordScores,
                            feedback = false,
                            canRetry = false,
                            totalWrong = it.totalWrong + 1,
                            streak = 0,
                            runStats = runStats,
                            awaitingNext = true
                        )
                    }

                    // Check if time penalty killed the timer
                    if (timerPausedRemaining <= 0L) {
                        _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                        delay(1200)
                        endGame()
                        return@launch
                    }
                    // Timer stays paused — will resume when player taps "Next"
                }

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
                // Resume timer so the game continues after an error
                resumeTimer()
            }
        }
    }

    // ─── Timer ───────────────────────────────────────────────────────────────────

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
                        timerFraction = (remaining.toFloat() / GAME_TIME_MS).coerceIn(0f, 1f)
                    )
                }
                delay(50)
            }
        }
    }

    /**
     * Pause the timer (during recording, processing, feedback, retry prompt).
     */
    private fun pauseTimer() {
        val remaining = timerDeadline - System.currentTimeMillis()
        if (remaining > 0) {
            timerPausedRemaining = remaining
            timerJob?.cancel()
        }
    }

    /**
     * Resume the timer (when player taps Next, Try Again, or after an error).
     */
    private fun resumeTimer() {
        if (timerPausedRemaining > 0) {
            timerDeadline = System.currentTimeMillis() + timerPausedRemaining
            timerPausedRemaining = 0L
            startTimer()
        }
    }

    private fun endGame() {
        timerJob?.cancel()
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

    /**
     * Play back the user's own recording (the last WAV sent to Azure).
     * Uses AudioTrack for direct PCM playback from the WAV byte array.
     */
    fun playRecording() {
        val wav = lastRecordingWav ?: return
        if (_uiState.value.isPlayingRecording) {
            stopPlayback()
            return
        }

        // WAV header is 44 bytes; PCM data starts at offset 44
        if (wav.size <= 44) return
        val pcmData = wav.copyOfRange(44, wav.size)

        stopPlayback()  // clean up any previous playback

        val bufferSize = AudioTrack.getMinBufferSize(
            AudioRecorder.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcmData.size)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(AudioRecorder.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcmData, 0, pcmData.size)
        audioTrack = track

        _uiState.update { it.copy(isPlayingRecording = true) }

        track.play()

        // Monitor playback completion in a coroutine
        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            // Estimate playback duration from PCM data size
            // 16-bit mono 16kHz = 32000 bytes/sec
            val durationMs = (pcmData.size.toLong() * 1000L) / (AudioRecorder.SAMPLE_RATE * 2)
            delay(durationMs + 100)  // small buffer for completion
            withContext(Dispatchers.Main) {
                stopPlayback()
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
        } catch (_: Exception) { }
        audioTrack?.release()
        audioTrack = null
        _uiState.update { it.copy(isPlayingRecording = false) }
    }

    fun speak(text: String) {
        frenchTts.speak(text)
    }

    fun resetToMenu() {
        countdownJob?.cancel()
        timerJob?.cancel()
        recordingJob?.cancel()
        stopPlayback()
        lastRecordingWav = null
        audioRecorder?.release()
        audioRecorder = null

        replayPool.clear()
        currentSentenceEntry = null
        val currentKey = _uiState.value.azureKey
        val currentRegion = _uiState.value.azureRegion
        val currentHighScore = _uiState.value.highScore
        _uiState.value = PrononcezState(azureKey = currentKey, azureRegion = currentRegion, highScore = currentHighScore)
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "pronunciation")
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        timerJob?.cancel()
        recordingJob?.cancel()
        stopPlayback()
        audioRecorder?.release()
    }
}
