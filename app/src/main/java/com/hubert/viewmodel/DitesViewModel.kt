package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.SentenceEntry
import com.hubert.data.model.VocabWord
import com.hubert.data.repository.LearningRepository
import com.hubert.data.repository.SettingsRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.AudioRecorder
import com.hubert.utils.ClozeGame
import com.hubert.utils.AzurePronunciationApi
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** Like the two sub-decks in Anki: French first (FR → DE) or German first (DE → FR). */
enum class DitesDirection { FR_DE, DE_FR }

/** One card: a word with all its example sentences, asked in one direction. */
data class DitesCard(
    val word: VocabWord,
    val direction: DitesDirection,
    val sentences: List<SentenceEntry>
)

data class DitesState(
    val isActive: Boolean = false,
    val isFinished: Boolean = false,
    val cards: List<DitesCard> = emptyList(),
    val index: Int = 0,
    val isRevealed: Boolean = false,
    val cloze: ClozeGame? = null,               // front: rebuild the sentence from word tiles
    val sentenceIndex: Int = 0,                 // which example sentence is shown
    val translationRevealed: Boolean = false,   // the translation is a spoiler until tapped
    val hasAzure: Boolean = false,
    val isRecording: Boolean = false,
    val isAssessing: Boolean = false,
    val pronScore: Int? = null,                                   // 0–100
    val wordScores: List<AzurePronunciationApi.WordResult> = emptyList(),
    val error: String? = null,
    val knownCount: Int = 0,
    val unknownCount: Int = 0,
    val pronScores: List<Int> = emptyList()
) {
    val current: DitesCard? get() = cards.getOrNull(index)
    val sentence: SentenceEntry? get() = current?.sentences?.getOrNull(sentenceIndex)
    /** What is spoken and assessed: the shown French sentence, or the word without one. */
    val spokenText: String? get() = sentence?.fr ?: current?.word?.french
    val averagePronScore: Int? get() = pronScores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

/**
 * Dites!: the Anki routine with pronunciation feedback. See the German word and sentence,
 * translate it in your head, reveal, speak the French sentence and get it scored by Azure,
 * then say whether you knew it. "Wusste ich" counts as recall in Hubert's schedule (never
 * written to Anki).
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
            // Due words first, then learned ones — preferring words that have an example sentence
            val ranks = learningRepository.practiceRanks(CARDS_PER_SESSION * 4)
            val withSentence = vocabRepository.getWordsWithSentences().map { it.rank }.toSet()
            val ordered = ranks.filter { it in withSentence } + ranks.filter { it !in withSentence }
            val sentences = vocabRepository.getSentences()
            val cards = ordered.take(CARDS_PER_SESSION).mapIndexedNotNull { i, rank ->
                val word = vocabRepository.getWordByRank(rank) ?: return@mapIndexedNotNull null
                DitesCard(
                    word = word,
                    direction = if (i % 2 == 0) DitesDirection.DE_FR else DitesDirection.FR_DE,
                    sentences = sentences[rank.toString()].orEmpty().shuffled()
                )
            }
            _uiState.update {
                it.copy(
                    cards = cards, hasAzure = settings.hasAzure, isFinished = cards.isEmpty(),
                    cloze = cards.firstOrNull()?.let { c -> clozeFor(c) }
                )
            }
        }
    }

    /**
     * The Lückenspiel as on the Anki card: DE → FR rebuilds the French sentence (target word
     * as ★), FR → DE the German one (its highlighted counterpart as ★).
     */
    private fun clozeFor(card: DitesCard): ClozeGame? {
        val sentence = card.sentences.firstOrNull() ?: return null
        return if (card.direction == DitesDirection.DE_FR) ClozeGame.create(sentence.fr, sentence.blank)
               else ClozeGame.create(sentence.de, sentence.deBlank)
    }

    fun tapClozeTile(index: Int) = updateCloze { it.tapTile(index) }

    fun tapClozeSlot(index: Int) = updateCloze { it.tapSlot(index) }

    /** ⚡ — only the target word is left to find. */
    fun clozeLightning() = updateCloze { it.revealAllButStar() }

    private fun updateCloze(action: (ClozeGame) -> ClozeGame) {
        val before = _uiState.value.cloze ?: return
        if (before.isFinished) return
        val after = action(before)
        _uiState.update { it.copy(cloze = after) }
        // Finished: read the French sentence aloud, like the Anki card
        if (after.isFinished) speakSentence()
    }

    fun reveal() {
        _uiState.update { it.copy(isRevealed = true) }
        _uiState.value.current?.let { frenchTts.speak(it.word.french) }
    }

    fun speakWord() {
        _uiState.value.current?.let { frenchTts.speak(it.word.french) }
    }

    fun speakSentence() {
        _uiState.value.spokenText?.let { frenchTts.speak(it) }
    }

    /** Tap on the hidden translation. */
    fun toggleTranslation() {
        _uiState.update { it.copy(translationRevealed = !it.translationRevealed) }
    }

    /** "›": next example sentence (cycles), translation hidden again. */
    fun nextSentence() {
        val state = _uiState.value
        val count = state.current?.sentences?.size ?: 0
        if (count < 2 || state.isRecording || state.isAssessing) return
        _uiState.update {
            it.copy(
                sentenceIndex = (it.sentenceIndex + 1) % count,
                translationRevealed = false,
                pronScore = null,
                wordScores = emptyList(),
                error = null
            )
        }
    }

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
                // After speaking, the French sentence is shown with its word colours
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

    // ── Self rating ─────────────────────────────────────────────────────

    fun rate(knewIt: Boolean) {
        val state = _uiState.value
        val card = state.current ?: return
        if (!state.isRevealed || state.isRecording || state.isAssessing) return
        answerLog += AnswerRecord(
            question = "${card.word.german} →",
            yourAnswer = if (knewIt) card.word.french else "—",
            correctAnswer = card.word.french,
            isCorrect = knewIt,
            explanation = state.sentence?.fr ?: "",
            rank = card.word.rank
        )
        val next = state.index + 1
        _uiState.update {
            it.copy(
                knownCount = it.knownCount + if (knewIt) 1 else 0,
                unknownCount = it.unknownCount + if (knewIt) 0 else 1,
                index = next,
                isRevealed = false,
                cloze = it.cards.getOrNull(next)?.let { c -> clozeFor(c) },
                sentenceIndex = 0,
                translationRevealed = false,
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
            statisticsRepository.saveSession(
                gameType = GAME_TYPE,
                score = state.knownCount * 100 + (state.averagePronScore ?: 0),
                totalCorrect = state.knownCount,
                totalWrong = state.unknownCount,
                bestStreak = 0,
                durationMs = System.currentTimeMillis() - startTime
            )
            // Updates Hubert's schedule ("Wusste ich" = recall); nothing goes to Anki
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
