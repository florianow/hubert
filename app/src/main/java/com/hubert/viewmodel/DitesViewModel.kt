package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.ConjugationVerb
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

/** Anki's four answer buttons; [strength] null = forgotten. */
enum class DitesGrade(val label: String, val strength: ReviewStrength?) {
    AGAIN("Nochmal", null),
    HARD("Schwer", ReviewStrength.RECOGNITION),
    GOOD("Gut", ReviewStrength.RECALL),
    EASY("Einfach", ReviewStrength.EASY)
}

/** One card: a word with the deck's example sentences, asked in one direction. */
data class DitesCard(
    val word: VocabWord,
    val direction: DitesDirection,
    val examples: List<Example>,
    val conjugation: ConjugationVerb?
)

data class DitesState(
    val isActive: Boolean = false,
    val isFinished: Boolean = false,
    val cards: List<DitesCard> = emptyList(),
    val index: Int = 0,
    val isRevealed: Boolean = false,
    // Front: the Lückenspiel sits behind an overlay (⚡ / 🔊 / start), as on the Anki card
    val clozeStarted: Boolean = false,
    val cloze: ClozeGame? = null,
    // Back: example box
    val exampleIndex: Int = 0,
    val translationRevealed: Boolean = true,      // the first example is shown open, like Anki
    val backCloze: ClozeGame? = null,             // Lückenspiel button on the back
    val showConjugation: Boolean = false,
    val intervals: Map<DitesGrade, Double> = emptyMap(),
    // Speaking
    val hasAzure: Boolean = false,
    val isRecording: Boolean = false,
    val isAssessing: Boolean = false,
    val pronScore: Int? = null,
    val wordScores: List<AzurePronunciationApi.WordResult> = emptyList(),
    val error: String? = null,
    // Session
    val grades: List<DitesGrade> = emptyList(),
    val pronScores: List<Int> = emptyList()
) {
    val current: DitesCard? get() = cards.getOrNull(index)
    val example: Example? get() = current?.examples?.getOrNull(exampleIndex)
    /** What is spoken and assessed: the shown French sentence, or the word without one. */
    val spokenText: String? get() = example?.frPlain ?: current?.word?.french
    val averagePronScore: Int? get() = pronScores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val knownCount: Int get() = grades.count { it != DitesGrade.AGAIN }
}

/**
 * Dites!: the anki_french card with pronunciation feedback. Front: sentence in the front
 * language and the Lückenspiel; back: word, definition, example sentences (spoilers,
 * audio, Lückenspiel), conjugation, then Anki's four buttons with Hubert's next intervals.
 * Speaking a sentence is scored by Azure. Grades go into Hubert's schedule only.
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
        const val CARDS_PER_SESSION = 10
    }

    fun start() {
        answerLog.clear()
        startTime = System.currentTimeMillis()
        _uiState.value = DitesState(isActive = true)
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val ranks = learningRepository.practiceRanks(CARDS_PER_SESSION * 3)
            val cards = withContext(Dispatchers.IO) {
                val verbs = vocabRepository.getConjugations().associateBy { it.infinitive }
                ranks.take(CARDS_PER_SESSION).mapIndexedNotNull { i, rank ->
                    val word = vocabRepository.getWordByRank(rank) ?: return@mapIndexedNotNull null
                    DitesCard(
                        word = word,
                        direction = if (i % 2 == 0) DitesDirection.DE_FR else DitesDirection.FR_DE,
                        examples = vocabRepository.getExamples(rank).shuffled(),
                        conjugation = verbs[word.french]
                    )
                }
            }
            _uiState.update { it.copy(cards = cards, hasAzure = settings.hasAzure, isFinished = cards.isEmpty()) }
            prepareCard()
        }
    }

    private fun prepareCard() {
        val card = _uiState.value.current ?: return
        _uiState.update { it.copy(cloze = clozeFor(card, 0), clozeStarted = false) }
        viewModelScope.launch {
            val preview = learningRepository.previewIntervals(card.word.rank)
            _uiState.update { s -> s.copy(intervals = DitesGrade.entries.associateWith { preview[it.strength] ?: 0.0 }) }
        }
    }

    /** DE → FR rebuilds the French sentence, FR → DE the German one; the deck's target is ★. */
    private fun clozeFor(card: DitesCard, exampleIndex: Int): ClozeGame? {
        val ex = card.examples.getOrNull(exampleIndex) ?: return null
        return if (card.direction == DitesDirection.DE_FR) ClozeGame.create(ex.frPlain, ex.frTarget)
               else ClozeGame.create(ex.dePlain, ex.deTarget)
    }

    // ── Front: overlay and Lückenspiel ──────────────────────────────────

    fun startCloze() = _uiState.update { it.copy(clozeStarted = true) }

    /** ⚡ — open the game with everything but the target word revealed. */
    fun clozeLightning() {
        _uiState.update { s -> s.copy(clozeStarted = true, cloze = s.cloze?.revealAllButStar()) }
    }

    fun tapClozeTile(index: Int) = updateCloze(back = false) { it.tapTile(index) }
    fun tapClozeSlot(index: Int) = updateCloze(back = false) { it.tapSlot(index) }
    fun tapBackClozeTile(index: Int) = updateCloze(back = true) { it.tapTile(index) }
    fun tapBackClozeSlot(index: Int) = updateCloze(back = true) { it.tapSlot(index) }

    private fun updateCloze(back: Boolean, action: (ClozeGame) -> ClozeGame) {
        val state = _uiState.value
        val before = (if (back) state.backCloze else state.cloze) ?: return
        if (before.isFinished) return
        val after = action(before)
        _uiState.update { if (back) it.copy(backCloze = after) else it.copy(cloze = after) }
        if (after.isFinished) speakSentence()   // like autoPlaySentenceOnClozeFinish
    }

    fun reveal() {
        _uiState.update { it.copy(isRevealed = true, exampleIndex = 0, translationRevealed = true, backCloze = null) }
        _uiState.value.current?.let { frenchTts.speak(it.word.french) }
    }

    // ── Back: example box ───────────────────────────────────────────────

    fun speakWord() {
        _uiState.value.current?.let { frenchTts.speak(it.word.french) }
    }

    fun speakSentence() {
        _uiState.value.spokenText?.let { frenchTts.speak(it) }
    }

    fun toggleTranslation() = _uiState.update { it.copy(translationRevealed = !it.translationRevealed) }

    /** "›": next example; from the second one on the translation is a spoiler again. */
    fun nextExample() {
        val state = _uiState.value
        val count = state.current?.examples?.size ?: 0
        if (count < 2 || state.isRecording || state.isAssessing) return
        _uiState.update {
            it.copy(
                exampleIndex = (it.exampleIndex + 1) % count,
                translationRevealed = false,
                backCloze = null,
                pronScore = null,
                wordScores = emptyList(),
                error = null
            )
        }
    }

    /** Lückenspiel button on the back: play the shown example as a game (or close it). */
    fun toggleBackCloze() {
        val state = _uiState.value
        val card = state.current ?: return
        _uiState.update { it.copy(backCloze = if (it.backCloze == null) clozeFor(card, state.exampleIndex) else null) }
    }

    fun toggleConjugation() = _uiState.update { it.copy(showConjugation = !it.showConjugation) }

    // ── Speaking ────────────────────────────────────────────────────────

    fun toggleRecording() {
        val state = _uiState.value
        if (!state.isRevealed || !state.hasAzure || state.isAssessing) return
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
        val text = _uiState.value.spokenText ?: return
        _uiState.update { it.copy(isRecording = false, isAssessing = true) }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            runCatching {
                AzurePronunciationApi.assess(settings.azureRegion, settings.azureKey, text, wav)
            }.onSuccess { result ->
                val score = result.pronScore.roundToInt()
                _uiState.update {
                    it.copy(
                        isAssessing = false, pronScore = score, wordScores = result.words,
                        pronScores = it.pronScores + score, translationRevealed = true
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isAssessing = false, error = "Bewertung fehlgeschlagen: ${e.message ?: "Netzwerk"}") }
            }
        }
    }

    // ── Rating (Anki's four buttons) ────────────────────────────────────

    fun rate(grade: DitesGrade) {
        val state = _uiState.value
        val card = state.current ?: return
        if (!state.isRevealed || state.isRecording || state.isAssessing) return
        answerLog += AnswerRecord(
            question = "${card.word.german} →",
            yourAnswer = grade.label,
            correctAnswer = card.word.french,
            isCorrect = grade != DitesGrade.AGAIN,
            explanation = state.example?.frPlain ?: "",
            rank = card.word.rank
        )
        viewModelScope.launch { learningRepository.recordGrade(card.word.rank, grade.strength) }
        val next = state.index + 1
        _uiState.update {
            it.copy(
                grades = it.grades + grade,
                index = next,
                isRevealed = false,
                exampleIndex = 0,
                translationRevealed = true,
                backCloze = null,
                showConjugation = false,
                pronScore = null,
                wordScores = emptyList(),
                error = null
            )
        }
        if (next >= state.cards.size) finish() else prepareCard()
    }

    private fun finish() {
        val state = _uiState.value
        viewModelScope.launch {
            statisticsRepository.saveSession(
                gameType = GAME_TYPE,
                score = state.knownCount * 100 + (state.averagePronScore ?: 0),
                totalCorrect = state.knownCount,
                totalWrong = state.grades.size - state.knownCount,
                bestStreak = 0,
                durationMs = System.currentTimeMillis() - startTime
            )
            // Statistics only — the schedule was already updated per grade
            statisticsRepository.saveWordAttempts(GAME_TYPE, answerLog)
            _uiState.update { it.copy(isFinished = true) }
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
