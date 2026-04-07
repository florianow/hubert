package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.ConjugationVerb
import com.hubert.data.model.SentenceMatch
import com.hubert.data.repository.HighScoreRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Conjuguez! — verb conjugation game.
 *
 * Player selects which tenses to practice before the game starts.
 * During gameplay, tenses are picked randomly from the selection,
 * with error-weighted repetition: tenses the player gets wrong
 * appear more frequently.
 *
 * When a matching example sentence exists, it's shown with the verb blanked out.
 * Otherwise, a plain drill view (infinitive + pronoun + tense) is used.
 *
 * ALL data comes directly from the Anki deck — nothing is generated.
 */
data class ConjugationState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,

    // Tense selection (shown before game starts)
    val isTenseSelection: Boolean = false,
    val availableTenses: Map<String, String> = emptyMap(),  // key -> display name
    val selectedTenses: Set<String> = setOf("present"),

    // Current question
    val infinitive: String = "",
    val german: String = "",
    val tenseName: String = "",        // Display name: "Présent", "Imparfait", etc.
    val personLabel: String = "",      // "je", "tu", "il/elle", etc.

    // Sentence context (null = use plain drill view)
    val sentenceFr: String? = null,    // French sentence with blank
    val sentenceDe: String? = null,    // German translation

    // Multiple choice
    val choices: List<String> = emptyList(),
    val correctIndex: Int = -1,

    // Feedback
    val selectedIndex: Int? = null,
    val feedback: Boolean? = null,     // true=correct, false=wrong

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

    // Countdown
    val countdown: Int? = null
)

@HiltViewModel
class ConjugationViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConjugationState())
    val uiState: StateFlow<ConjugationState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var timerDeadline = 0L

    private var verbPool: MutableList<ConjugationVerb> = mutableListOf()
    private var activeTenses: Set<String> = setOf("present")

    // Error-weighted tense tracking: count of wrong answers per tense
    private var tenseErrorCounts: MutableMap<String, Int> = mutableMapOf()

    companion object {
        const val GAME_TIME_MS = 90_000L
        const val POINTS_PER_CORRECT = 200
        const val STREAK_BONUS = 50
        const val WRONG_PENALTY_MS = 5_000L
        const val CORRECT_BONUS_MS = 4_000L
        const val NUM_CHOICES = 4

        // Error weight: each error on a tense adds this much extra selection weight
        const val ERROR_WEIGHT_BONUS = 2

        // Tense keys → display names
        val TENSE_DISPLAY = mapOf(
            "present" to "Présent",
            "imparfait" to "Imparfait",
            "futur" to "Futur simple",
            "conditionnel" to "Conditionnel",
            "subjonctif" to "Subjonctif",
            "passe_simple" to "Passé simple",
            "imperatif" to "Impératif"
        )

        val PERSON_LABELS = listOf("je", "tu", "il/elle", "nous", "vous", "ils/elles")
        // Impératif only has tu (1), nous (3), vous (4) forms
        val IMPERATIF_PERSONS = listOf(1, 3, 4)
    }

    init {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "conjugation")
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    /**
     * Show the tense selection screen. Called when user taps the Conjuguez! card.
     */
    fun showTenseSelection() {
        _uiState.update {
            it.copy(
                isTenseSelection = true,
                availableTenses = TENSE_DISPLAY,
                selectedTenses = it.selectedTenses  // preserve previous selection
            )
        }
    }

    /**
     * Toggle a tense on/off in the selection screen.
     * Ensures at least one tense remains selected.
     */
    fun toggleTense(tenseKey: String) {
        _uiState.update { state ->
            val current = state.selectedTenses
            val updated = if (tenseKey in current) {
                // Don't allow deselecting the last tense
                if (current.size > 1) current - tenseKey else current
            } else {
                current + tenseKey
            }
            state.copy(selectedTenses = updated)
        }
    }

    /**
     * Start the game with the currently selected tenses.
     */
    fun startGame() {
        countdownJob?.cancel()
        timerJob?.cancel()

        activeTenses = _uiState.value.selectedTenses
        tenseErrorCounts = mutableMapOf()
        verbPool = vocabRepository.getConjugations().shuffled().toMutableList()

        _uiState.update {
            ConjugationState(
                isPlaying = false,
                isTenseSelection = false,
                countdown = 3,
                highScore = it.highScore,
                selectedTenses = it.selectedTenses  // preserve for next round
            )
        }

        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                _uiState.update { it.copy(countdown = i) }
                delay(800)
            }
            _uiState.update { it.copy(countdown = null, isPlaying = true) }
            timerDeadline = System.currentTimeMillis() + GAME_TIME_MS
            _uiState.update {
                it.copy(timeRemainingMs = GAME_TIME_MS, timerFraction = 1f)
            }
            showNextQuestion()
            startTimer()
        }
    }

    private fun showNextQuestion() {
        if (verbPool.isEmpty()) {
            verbPool = vocabRepository.getConjugations().shuffled().toMutableList()
        }

        val verb = verbPool.removeFirst()

        // Pick tense using error-weighted random selection
        val tense = pickWeightedTense(verb)
        if (tense == null) {
            // This verb doesn't have any of the selected tenses, skip
            showNextQuestion()
            return
        }

        val forms = verb.tenses[tense] ?: return

        // Pick a random person (that has a non-empty form)
        val eligiblePersons = if (tense == "imperatif") {
            IMPERATIF_PERSONS.filter { idx -> idx < forms.size && forms[idx].isNotEmpty() }
        } else {
            forms.indices.filter { forms[it].isNotEmpty() }
        }

        if (eligiblePersons.isEmpty()) {
            showNextQuestion()
            return
        }

        val personIdx = eligiblePersons.random()
        val correctForm = forms[personIdx]

        // Build distractors: other forms of the SAME verb (different tenses/persons)
        val distractorPool = mutableSetOf<String>()
        for ((_, fs) in verb.tenses) {
            for (f in fs) {
                if (f.isNotEmpty() && f.lowercase() != correctForm.lowercase()) {
                    distractorPool.add(f)
                }
            }
        }

        val distractors = distractorPool.shuffled().take(NUM_CHOICES - 1)

        // If not enough distractors from same verb, this is rare but handle it
        if (distractors.isEmpty()) {
            showNextQuestion()
            return
        }

        val allChoices = (listOf(correctForm) + distractors).shuffled()
        val correctIdx = allChoices.indexOf(correctForm)

        // Check for matching sentence
        val sentenceMatch = verb.sentences
            ?.get(tense)
            ?.get(personIdx.toString())

        val sentenceFr = if (sentenceMatch != null) {
            sentenceMatch.fr.replace(sentenceMatch.blank, "___")
        } else null

        _uiState.update {
            it.copy(
                infinitive = verb.infinitive,
                german = verb.german,
                tenseName = TENSE_DISPLAY[tense] ?: tense,
                personLabel = PERSON_LABELS[personIdx],
                sentenceFr = sentenceFr,
                sentenceDe = sentenceMatch?.de,
                choices = allChoices,
                correctIndex = correctIdx,
                selectedIndex = null,
                feedback = null
            )
        }
    }

    /**
     * Pick a tense from the active set using error-weighted random selection.
     * Each tense gets a base weight of 1. Each error on a tense adds
     * [ERROR_WEIGHT_BONUS] extra weight, so tenses the player struggles with
     * appear more often.
     *
     * Returns null if the verb has none of the active tenses.
     */
    private fun pickWeightedTense(verb: ConjugationVerb): String? {
        val eligibleTenses = verb.tenses.keys.filter { it in activeTenses }
        if (eligibleTenses.isEmpty()) return null

        // Build weighted list
        val weighted = eligibleTenses.flatMap { tense ->
            val weight = 1 + (tenseErrorCounts[tense] ?: 0) * ERROR_WEIGHT_BONUS
            List(weight) { tense }
        }

        return weighted.random()
    }

    fun answer(choiceIndex: Int) {
        val state = _uiState.value
        if (!state.isPlaying || state.feedback != null) return

        val isCorrect = choiceIndex == state.correctIndex

        // Always speak the correct conjugated form (learn from mistakes too)
        frenchTts.speak(state.choices[state.correctIndex])

        // Find the current tense key for error tracking
        val currentTenseKey = TENSE_DISPLAY.entries
            .firstOrNull { it.value == state.tenseName }?.key

        if (isCorrect) {
            val newStreak = state.streak + 1
            val streakBonus = (newStreak - 1) * STREAK_BONUS
            val matchScore = POINTS_PER_CORRECT + streakBonus

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

            // Track error for weighted tense selection
            if (currentTenseKey != null) {
                tenseErrorCounts[currentTenseKey] =
                    (tenseErrorCounts[currentTenseKey] ?: 0) + 1
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
            delay(if (isCorrect) 600 else 1200)
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

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore(gameType = "conjugation")
            val isNewHigh = state.score > previousHigh

            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalCorrect,
                roundsCompleted = state.bestStreak,
                gameType = "conjugation"
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

    fun resetToMenu() {
        timerJob?.cancel()
        countdownJob?.cancel()
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "conjugation")
            _uiState.value = ConjugationState(highScore = hs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        countdownJob?.cancel()
    }
}
