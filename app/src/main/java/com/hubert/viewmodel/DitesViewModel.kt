package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.Example
import com.hubert.data.model.VocabWord
import com.hubert.data.repository.LearningRepository
import com.hubert.data.repository.SettingsRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.AudioRecorder
import com.hubert.utils.AzurePronunciationApi
import com.hubert.utils.ClozeGame
import com.hubert.utils.FrenchTts
import com.hubert.utils.ReviewStrength
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

/** Like the two sub-decks in Anki: French first (FR → DE) or German first (DE → FR). */
enum class DitesDirection { FR_DE, DE_FR }

/** How a card went — decided by the game, not by self-rating. */
enum class DitesOutcome(val label: String, val strength: ReviewStrength?) {
    RICHTIG("Richtig", ReviewStrength.RECALL),          // no wrong tile, no hint
    WACKLIG("Noch wacklig", ReviewStrength.RECOGNITION), // found it, but with mistakes or hints
    FALSCH("Falsch", null)                               // target word revealed or given up
}

data class DitesCard(val word: VocabWord, val direction: DitesDirection, val example: Example)

data class DitesState(
    val isActive: Boolean = false,
    val isFinished: Boolean = false,
    val cards: List<DitesCard> = emptyList(),
    val index: Int = 0,
    val cloze: ClozeGame? = null,
    val outcome: DitesOutcome? = null,      // set when the sentence is complete
    // Optional speaking after the solution (Hubert's extra, not graded)
    val hasAzure: Boolean = false,
    val isRecording: Boolean = false,
    val isAssessing: Boolean = false,
    val pronScore: Int? = null,
    val wordScores: List<AzurePronunciationApi.WordResult> = emptyList(),
    val error: String? = null,
    val outcomes: List<DitesOutcome> = emptyList(),
    val answerHistory: List<AnswerRecord> = emptyList()
) {
    val current: DitesCard? get() = cards.getOrNull(index)
}

/**
 * Dites!: a round of "Vokabeln lernen" built on the anki_french Lückenspiel. Rebuild the
 * sentence in the other language from word tiles; the deck's target word is the ★. The game
 * grades itself — no wrong tile and no hint = richtig, mistakes or hints = noch wacklig,
 * target revealed or given up = falsch. Afterwards the solution can be heard and spoken
 * (Azure score, not graded).
 */
@HiltViewModel
class DitesViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val learningRepository: LearningRepository,
    private val statisticsRepository: StatisticsRepository,
    private val settingsRepository: SettingsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(DitesState())
    val uiState: StateFlow<DitesState> = _uiState.asStateFlow()

    private var recorder: AudioRecorder? = null
    private var recordingJob: Job? = null
    private val answerLog = mutableListOf<AnswerRecord>()
    private var startTime = 0L

    companion object {
        const val GAME_TYPE = "phrases"
    }

    /** Start a round for the words chosen by the learning mode. */
    fun start(ranks: List<Int>) {
        answerLog.clear()
        startTime = System.currentTimeMillis()
        _uiState.value = DitesState(isActive = true)
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val cards = withContext(Dispatchers.IO) {
                ranks.mapIndexedNotNull { i, rank ->
                    val word = vocabRepository.getWordByRank(rank) ?: return@mapIndexedNotNull null
                    // Short sentences make better tile games
                    val example = vocabRepository.getExamples(rank).minByOrNull { it.frPlain.length } ?: return@mapIndexedNotNull null
                    DitesCard(word, if (i % 2 == 0) DitesDirection.DE_FR else DitesDirection.FR_DE, example)
                }
            }
            _uiState.update {
                it.copy(cards = cards, hasAzure = settings.hasAzure, cloze = cards.firstOrNull()?.let(::clozeFor))
            }
            if (cards.isEmpty()) finish()
        }
    }

    /** DE → FR rebuilds the French sentence, FR → DE the German one; the deck's target is ★. */
    private fun clozeFor(card: DitesCard): ClozeGame =
        if (card.direction == DitesDirection.DE_FR) ClozeGame.create(card.example.frPlain, card.example.frTarget)
        else ClozeGame.create(card.example.dePlain, card.example.deTarget)

    fun tapTile(index: Int) = play { it.tapTile(index) }
    fun tapSlot(index: Int) = play { it.tapSlot(index) }
    fun lightning() = play { it.revealAllButStar() }
    fun giveUp() = play { it.revealAll() }

    private fun play(action: (ClozeGame) -> ClozeGame) {
        val state = _uiState.value
        val before = state.cloze ?: return
        if (state.outcome != null) return
        val after = action(before)
        val outcome = if (after.isFinished) gradeOf(after) else null
        _uiState.update { it.copy(cloze = after, outcome = outcome) }
        if (outcome != null) speakSentence()
    }

    private fun gradeOf(game: ClozeGame): DitesOutcome = when {
        game.starHinted -> DitesOutcome.FALSCH
        game.errors == 0 && game.hints == 0 -> DitesOutcome.RICHTIG
        else -> DitesOutcome.WACKLIG
    }

    fun speakSentence() {
        _uiState.value.current?.let { frenchTts.speak(it.example.frPlain) }
    }

    fun speakWord() {
        _uiState.value.current?.let { frenchTts.speak(it.word.french) }
    }

    // ── Optional speaking after the solution ────────────────────────────

    fun toggleRecording() {
        val state = _uiState.value
        if (state.outcome == null || !state.hasAzure || state.isAssessing) return
        if (state.isRecording) stopAndAssess() else startRecording()
    }

    private fun startRecording() {
        recorder = AudioRecorder()
        _uiState.update { it.copy(isRecording = true, error = null, pronScore = null, wordScores = emptyList()) }
        recordingJob = viewModelScope.launch { recorder?.startRecording() }
    }

    private fun stopAndAssess() {
        val wav = recorder?.stop() ?: return
        recorder = null
        recordingJob?.cancel()
        val text = _uiState.value.current?.example?.frPlain ?: return
        _uiState.update { it.copy(isRecording = false, isAssessing = true) }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            runCatching { AzurePronunciationApi.assess(settings.azureRegion, settings.azureKey, text, wav) }
                .onSuccess { r -> _uiState.update { it.copy(isAssessing = false, pronScore = r.pronScore.roundToInt(), wordScores = r.words) } }
                .onFailure { e -> _uiState.update { it.copy(isAssessing = false, error = "Bewertung fehlgeschlagen: ${e.message ?: "Netzwerk"}") } }
        }
    }

    // ── Next card ───────────────────────────────────────────────────────

    fun next() {
        val state = _uiState.value
        val card = state.current ?: return
        val outcome = state.outcome ?: return
        if (state.isRecording || state.isAssessing) return
        answerLog += AnswerRecord(
            question = "${card.word.german} →",
            yourAnswer = outcome.label,
            correctAnswer = card.word.french,
            isCorrect = outcome != DitesOutcome.FALSCH,
            explanation = card.example.frPlain,
            rank = card.word.rank
        )
        viewModelScope.launch { learningRepository.recordGrade(card.word.rank, outcome.strength) }
        val next = state.index + 1
        _uiState.update {
            it.copy(
                outcomes = it.outcomes + outcome,
                index = next,
                cloze = it.cards.getOrNull(next)?.let(::clozeFor),
                outcome = null,
                pronScore = null,
                wordScores = emptyList(),
                error = null
            )
        }
        if (next >= state.cards.size) finish()
    }

    private fun finish() {
        val state = _uiState.value
        viewModelScope.launch {
            val known = state.outcomes.count { it != DitesOutcome.FALSCH }
            statisticsRepository.saveSession(
                gameType = GAME_TYPE,
                score = state.outcomes.count { it == DitesOutcome.RICHTIG } * 100 + state.outcomes.count { it == DitesOutcome.WACKLIG } * 50,
                totalCorrect = known,
                totalWrong = state.outcomes.size - known,
                bestStreak = 0,
                durationMs = System.currentTimeMillis() - startTime
            )
            // Statistics only — the schedule was already updated per card
            statisticsRepository.saveWordAttempts(GAME_TYPE, answerLog)
            _uiState.update { it.copy(isFinished = true, answerHistory = answerLog.toList()) }
        }
    }

    fun reset() {
        recorder?.release()
        recorder = null
        recordingJob?.cancel()
        answerLog.clear()
        _uiState.value = DitesState()
    }

    override fun onCleared() {
        super.onCleared()
        recorder?.release()
    }
}
