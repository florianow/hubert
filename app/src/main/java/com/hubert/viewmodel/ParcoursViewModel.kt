package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.repository.ParcoursLesson
import com.hubert.data.repository.ParcoursProgress
import com.hubert.data.repository.ParcoursRepository
import com.hubert.data.repository.ParcoursUnit
import com.hubert.data.repository.SettingsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.data.model.ParlezTopic
import com.hubert.data.model.VocabHint
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A lesson opened for reading: the unit, which of its notes, and the note's HTML. */
data class OpenLesson(val unit: ParcoursUnit, val index: Int, val html: String) {
    val lesson: ParcoursLesson get() = unit.lessons[index]
    val isLast: Boolean get() = index == unit.lessons.lastIndex
}

/** A wrong answer in a quiz, shown at the end. */
data class QuizMistake(val item: QuizItem, val given: String)

/** A running (or finished) Mini-Check, Contrôle or Révision. */
data class QuizState(
    val kind: QuizKind,
    val unit: ParcoursUnit,
    val items: List<QuizItem>,
    val index: Int = 0,
    val given: String? = null,          // answer of the current question (null = not answered yet)
    val typed: String = "",
    val correct: Int = 0,
    val mistakes: List<QuizMistake> = emptyList(),
    val finished: Boolean = false,
    val passed: Boolean = false,
    val outcome: String = ""
) {
    val item: QuizItem? get() = items.getOrNull(index)
    val percent: Int get() = if (items.isEmpty()) 0 else correct * 100 / items.size
    val isLast: Boolean get() = index >= items.lastIndex
}

data class ParcoursState(
    val units: List<ParcoursUnit> = emptyList(),
    val progress: ParcoursProgress = ParcoursProgress(),
    val enabled: Boolean = true,
    val selectedUnit: ParcoursUnit? = null,   // unit detail
    val lesson: OpenLesson? = null,           // lesson being read
    val quiz: QuizState? = null,              // quiz being played
    val notice: String? = null                // e.g. the result of an Entraînement
)

/** The Parcours screen: all étapes, a unit's detail and its lessons. */
@HiltViewModel
class ParcoursViewModel @Inject constructor(
    private val parcoursRepository: ParcoursRepository,
    settingsRepository: SettingsRepository,
    private val vocabRepository: VocabRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParcoursState())
    val uiState: StateFlow<ParcoursState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val units = withContext(Dispatchers.IO) { parcoursRepository.units }
            _uiState.update { it.copy(units = units) }
        }
        viewModelScope.launch {
            parcoursRepository.progress.collect { p -> _uiState.update { it.copy(progress = p) } }
        }
        viewModelScope.launch {
            settingsRepository.parcoursEnabled.collect { e -> _uiState.update { it.copy(enabled = e) } }
        }
    }

    fun showOverview() {
        _uiState.update { it.copy(selectedUnit = null, lesson = null, quiz = null, notice = null) }
    }

    fun selectUnit(unit: ParcoursUnit) {
        _uiState.update { it.copy(selectedUnit = unit, lesson = null, quiz = null, notice = null) }
    }

    /** The current étape's detail (from the start screen). */
    fun showCurrentUnit() {
        val state = _uiState.value
        val unit = state.units.firstOrNull { it.id == state.progress.current } ?: parcoursRepository.units.first()
        selectUnit(unit)
    }

    /** Opens the current unit's first lesson directly (from the start screen). */
    fun openCurrentLesson() {
        val state = _uiState.value
        val unit = state.units.firstOrNull { it.id == state.progress.current } ?: parcoursRepository.units.first()
        _uiState.update { it.copy(selectedUnit = unit) }
        openLesson(unit, 0)
    }

    fun openLesson(unit: ParcoursUnit, index: Int) {
        viewModelScope.launch {
            val html = withContext(Dispatchers.IO) { parcoursRepository.lessonHtml(unit.lessons[index].file) }
            _uiState.update { it.copy(lesson = OpenLesson(unit, index, html)) }
        }
    }

    /** "Weiter" on a lesson: the next note of the unit, or done (marks it read, then the Mini-Check). */
    fun lessonNext() {
        val open = _uiState.value.lesson ?: return
        if (!open.isLast) {
            openLesson(open.unit, open.index + 1)
            return
        }
        viewModelScope.launch { parcoursRepository.markLessonRead(open.unit.id) }
        _uiState.update { it.copy(lesson = null, selectedUnit = open.unit) }
        startQuiz(open.unit, QuizKind.CHECK)
    }

    // ── Quizzes ─────────────────────────────────────────────────────────

    fun startQuiz(unit: ParcoursUnit, kind: QuizKind) {
        val items = ParcoursQuizBuilder.build(unit, kind)
        if (items.isEmpty()) return
        _uiState.update { it.copy(quiz = QuizState(kind, unit, items), notice = null) }
        items.first().takeIf { it.audioOnly }?.speech?.let { frenchTts.speak(it) }
    }

    /** The most overdue Révision. */
    fun startDueReview() {
        viewModelScope.launch {
            val due = parcoursRepository.dueReview() ?: return@launch
            _uiState.update { it.copy(selectedUnit = due.unit, lesson = null) }
            startQuiz(due.unit, QuizKind.REVIEW)
        }
    }

    fun choose(option: String) = answer(option)

    fun onTyped(text: String) {
        _uiState.update { s -> s.copy(quiz = s.quiz?.takeIf { it.given == null }?.copy(typed = text.filter { it.isDigit() || it == ' ' }.take(12)) ?: s.quiz) }
    }

    fun submitTyped() {
        val quiz = _uiState.value.quiz ?: return
        if (quiz.typed.isNotBlank()) answer(quiz.typed.trim())
    }

    private fun answer(given: String) {
        val quiz = _uiState.value.quiz ?: return
        val item = quiz.item ?: return
        if (quiz.given != null) return
        val right = ParcoursQuizBuilder.isRight(item, given)
        _uiState.update {
            it.copy(quiz = quiz.copy(
                given = given,
                correct = quiz.correct + if (right) 1 else 0,
                mistakes = if (right) quiz.mistakes else quiz.mistakes + QuizMistake(item, given)
            ))
        }
        item.speech?.let { frenchTts.speak(it) }
    }

    fun nextQuestion() {
        val quiz = _uiState.value.quiz ?: return
        if (quiz.given == null) return
        if (!quiz.isLast) {
            val next = quiz.copy(index = quiz.index + 1, given = null, typed = "")
            _uiState.update { it.copy(quiz = next) }
            next.item?.takeIf { it.audioOnly }?.speech?.let { frenchTts.speak(it) }
            return
        }
        viewModelScope.launch {
            val percent = quiz.percent
            val (passed, outcome) = when (quiz.kind) {
                QuizKind.CHECK -> {
                    parcoursRepository.recordCheck(quiz.unit.id)
                    true to if (quiz.mistakes.isEmpty()) "Alles richtig! Weiter mit dem Entraînement."
                            else "Schau dir die Fehler unten an – dann weiter mit dem Entraînement."
                }
                QuizKind.CONTROL -> {
                    val passed = parcoursRepository.recordControl(quiz.unit.id, percent)
                    passed to if (passed) "🎓 Étape bestanden! Sie kommt als Révision wieder: in ${ParcoursRepository.REVIEW_DAYS[0]} Tagen."
                               else "Ab ${ParcoursRepository.PASS_SCORE} % ist die Étape bestanden. Lies die Regeln zu den Fehlern nach und versuch's nochmal."
                }
                QuizKind.REVIEW -> {
                    val days = parcoursRepository.recordReview(quiz.unit.id, percent)
                    val good = percent >= ParcoursRepository.REVIEW_PASS_SCORE
                    good to if (good) "Sitzt! Nächste Révision in $days Tagen." else "Noch wacklig – morgen nochmal."
                }
            }
            _uiState.update { it.copy(quiz = quiz.copy(finished = true, passed = passed, outcome = outcome)) }
        }
    }

    fun closeQuiz() {
        _uiState.update { it.copy(quiz = null) }
    }

    /** Result of an Entraînement round in the étape's game (MainActivity reports it). */
    fun recordTraining(unitId: Int, correct: Int, total: Int) {
        viewModelScope.launch {
            val done = parcoursRepository.recordTraining(unitId, correct)
            val unit = _uiState.value.units.firstOrNull { it.id == unitId }
            _uiState.update {
                it.copy(
                    selectedUnit = unit ?: it.selectedUnit,
                    lesson = null,
                    quiz = null,
                    notice = if (done) "🎯 Entraînement geschafft: $correct von $total richtig. Jetzt der Contrôle!"
                             else "🎯 $correct von $total richtig – für das Entraînement braucht es ${ParcoursRepository.TRAINING_MIN_CORRECT} richtige. Nochmal?"
                )
            }
        }
    }

    fun closeLesson() {
        _uiState.update { it.copy(lesson = null) }
    }

    fun jumpTo(unit: ParcoursUnit) {
        viewModelScope.launch { parcoursRepository.jumpTo(unit.id) }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { parcoursRepository.setEnabled(enabled) }
    }

    fun speak(text: String) = frenchTts.speak(text)

    /** The étape's conversation as a Parlez! topic; hints are the theme's most frequent words. */
    fun parlezTopic(unit: ParcoursUnit): ParlezTopic? {
        val p = unit.parlez ?: return null
        val index = _uiState.value.units.indexOf(unit)
        val hints = vocabRepository.getWordsInCategory(unit.theme)
            .sortedBy { it.rank }
            .take(PARLEZ_HINTS)
            .map { VocabHint(fr = it.french, de = it.german) }
        return ParlezTopic(
            id = "parcours_${unit.id}",
            themeFr = p.themeFr,
            themeDe = "🧭 Étape ${index + 1}: ${unit.title}",
            niveau = unit.level.take(2),
            descriptionDe = "Grammatik-Ziel: ${unit.title}",
            scenarioDe = p.scenarioDe,
            hubertRoleFr = p.hubertRoleFr,
            starterFr = p.starterFr,
            vocabHints = hints,
            grammarFr = p.grammarFr
        )
    }

    companion object {
        const val PARLEZ_HINTS = 6
    }
}
