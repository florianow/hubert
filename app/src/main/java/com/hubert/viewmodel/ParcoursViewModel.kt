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

data class ParcoursState(
    val units: List<ParcoursUnit> = emptyList(),
    val progress: ParcoursProgress = ParcoursProgress(),
    val enabled: Boolean = true,
    val selectedUnit: ParcoursUnit? = null,   // unit detail
    val lesson: OpenLesson? = null            // lesson being read
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
        _uiState.update { it.copy(selectedUnit = null, lesson = null) }
    }

    fun selectUnit(unit: ParcoursUnit) {
        _uiState.update { it.copy(selectedUnit = unit, lesson = null) }
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

    /** "Weiter" on a lesson: the next note of the unit, or done (marks the lesson read). */
    fun lessonNext() {
        val open = _uiState.value.lesson ?: return
        if (!open.isLast) {
            openLesson(open.unit, open.index + 1)
            return
        }
        viewModelScope.launch { parcoursRepository.markLessonRead(open.unit.id) }
        _uiState.update { it.copy(lesson = null) }
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
