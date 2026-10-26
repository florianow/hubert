package com.hubert.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** One lesson of a unit: an anki_french grammar note (HTML in assets/grammar). */
data class ParcoursLesson(val title: String, val file: String)

/**
 * A gap exercise of an étape (scripts/parcours_exercises.py): [fr] with "___", the [answer]
 * and wrong choices that test the étape's grammar. [source] "lesson" = a sentence of the
 * étape's lessons, "pool" = Hubert's sentences for the same grammar.
 */
data class ParcoursExercise(
    val fr: String,
    val de: String,
    val answer: String,
    val wrong: List<String>,
    val rule: String,
    val source: String
)

/** The étape's conversation for Parlez! (scripts/build_parcours.py, PARLEZ). */
data class ParcoursParlez(
    val themeFr: String,
    val scenarioDe: String,
    val hubertRoleFr: String,
    val starterFr: String,
    val grammarFr: String
)

/**
 * One étape of the Parcours: a grammar point with its lesson(s), its gap [exercises], the
 * grammar game (GameSession key) with its focus for the Entraînement (and as Plat du chef in
 * the menus) and a Parlez! conversation. [kind]: article, adjective, conjugation,
 * preposition, relative, agreement or numbers (the last two are filled up in the app).
 */
data class ParcoursUnit(
    val id: Int,
    val level: String,
    val title: String,
    val theme: String,
    val lessons: List<ParcoursLesson>,
    val game: String,
    val focus: List<String>,
    val kind: String = "",
    val exercises: List<ParcoursExercise>? = null,
    val parlez: ParcoursParlez? = null
)

/** Spaced review of a passed étape's grammar: due on [dueDay], [step] into REVIEW_DAYS. */
data class GrammarReview(val dueDay: Long, val step: Int)

/**
 * Stored progress per étape: lesson read (with its Mini-Check), Entraînement played,
 * best [control] result in percent, passed, and the Révision schedule. ([menus] and [best]
 * are left from the menu-based Contrôle and no longer used.)
 */
data class ParcoursProgress(
    val current: Int = 1,
    val lessonsRead: Set<Int> = emptySet(),
    val menus: Map<Int, Int> = emptyMap(),
    val best: Map<Int, Int> = emptyMap(),
    val passed: Set<Int> = emptySet(),
    val reviews: Map<Int, GrammarReview> = emptyMap(),
    val checked: Set<Int> = emptySet(),
    val trained: Set<Int> = emptySet(),
    val control: Map<Int, Int> = emptyMap()
)

/** A passed étape whose grammar is due again. */
data class DueReview(val unit: ParcoursUnit, val index: Int)

/** The steps of an étape, in order. */
enum class ParcoursStep(val label: String, val icon: String) {
    LECON("Leçon + Mini-Check", "📖"),
    ENTRAINEMENT("Entraînement", "🎯"),
    CONTROLE("Contrôle", "🎓")
}

/** Where the current unit stands: Leçon (+ Mini-Check) → Entraînement → Contrôle (≥ 80 %). */
data class ParcoursStatus(
    val unit: ParcoursUnit,
    val index: Int,               // 0-based position in the Parcours
    val total: Int,
    val lessonRead: Boolean,
    val trained: Boolean,
    val bestControl: Int?,        // best Contrôle result in percent, null = not tried yet
    val enabled: Boolean,
    val dueReview: DueReview? = null,   // most overdue passed étape (not the current one)
    val dueReviews: Int = 0             // passed étapes whose grammar is due again
) {
    /** The step to do next. */
    val nextStep: ParcoursStep get() = when {
        !lessonRead -> ParcoursStep.LECON
        !trained -> ParcoursStep.ENTRAINEMENT
        else -> ParcoursStep.CONTROLE
    }
    fun isDone(step: ParcoursStep): Boolean = when (step) {
        ParcoursStep.LECON -> lessonRead
        ParcoursStep.ENTRAINEMENT -> trained
        ParcoursStep.CONTROLE -> false
    }
}

/**
 * The Parcours: units from A1 to B2 (assets/parcours.json, built by scripts/build_parcours.py
 * from the anki_french grammar notes). Each étape: Leçon with a Mini-Check, Entraînement in
 * the étape's game, then a Contrôle of gap exercises; a passed Contrôle moves on to the next
 * étape and starts its Révisions (short quizzes after 3, 7, 21, 60 days). Menu du jour serves
 * the étape's game as Plat du chef (extra practice; the theme of the day stays).
 */
@Singleton
class ParcoursRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        /** Contrôle passed from 80 %. */
        const val PASS_SCORE = 80
        /** The Entraînement counts once this many answers in the étape's game were right. */
        const val TRAINING_MIN_CORRECT = 6
        /** Days until a passed étape comes back as Révision; a good Révision moves one step on. */
        val REVIEW_DAYS = listOf(3, 7, 21, 60)
        /** A Révision counts as good from 75 %. */
        const val REVIEW_PASS_SCORE = 75
        /** A weak Révision comes back after this many days. */
        const val REVIEW_RETRY_DAYS = 1
    }

    private fun today(): Long = java.time.LocalDate.now().toEpochDay()

    private val gson = Gson()
    private val mutex = Mutex()

    val units: List<ParcoursUnit> by lazy {
        val json = context.assets.open("parcours.json").bufferedReader().use { it.readText() }
        gson.fromJson(json, object : TypeToken<List<ParcoursUnit>>() {}.type)
    }

    val progress: Flow<ParcoursProgress> = settingsRepository.parcoursProgress.map(::parse)

    private fun parse(json: String): ParcoursProgress =
        if (json.isBlank()) ParcoursProgress()
        else runCatching { gson.fromJson(json, ParcoursProgress::class.java) }.getOrNull()
            ?.let {
                // Gson leaves missing collections null
                ParcoursProgress(
                    current = it.current,
                    lessonsRead = it.lessonsRead ?: emptySet(),
                    menus = it.menus ?: emptyMap(),
                    best = it.best ?: emptyMap(),
                    passed = it.passed ?: emptySet(),
                    reviews = it.reviews ?: emptyMap(),
                    checked = it.checked ?: emptySet(),
                    trained = it.trained ?: emptySet(),
                    control = it.control ?: emptyMap()
                )
            } ?: ParcoursProgress()

    suspend fun current(): ParcoursProgress = parse(settingsRepository.parcoursProgress.first())

    /** Status of the current unit; null when the Parcours is switched off. */
    suspend fun status(): ParcoursStatus? {
        val enabled = settingsRepository.parcoursEnabled.first()
        val p = current()
        val index = units.indexOfFirst { it.id == p.current }.coerceAtLeast(0)
        val unit = units[index]
        return ParcoursStatus(
            unit = unit,
            index = index,
            total = units.size,
            lessonRead = unit.id in p.lessonsRead,
            trained = unit.id in p.trained,
            bestControl = p.control[unit.id],
            enabled = enabled,
            dueReview = dueReview(p),
            dueReviews = p.reviews.count { (id, r) -> r.dueDay <= today() && id != unit.id }
        ).takeIf { enabled }
    }

    private suspend fun update(change: (ParcoursProgress) -> ParcoursProgress) = mutex.withLock {
        settingsRepository.saveParcoursProgress(gson.toJson(change(current())))
    }

    suspend fun markLessonRead(unitId: Int) = update { it.copy(lessonsRead = it.lessonsRead + unitId) }

    /** Continue the Parcours at [unitId] (e.g. skip what is already known). */
    suspend fun jumpTo(unitId: Int) = update { it.copy(current = unitId) }

    suspend fun setEnabled(enabled: Boolean) = settingsRepository.saveParcoursEnabled(enabled)

    /** The Mini-Check after the lesson was done. */
    suspend fun recordCheck(unitId: Int) = update { it.copy(checked = it.checked + unitId) }

    /** An Entraînement round in the étape's game; counts with [TRAINING_MIN_CORRECT] right answers. */
    suspend fun recordTraining(unitId: Int, correct: Int): Boolean {
        val done = correct >= TRAINING_MIN_CORRECT
        if (done) update { it.copy(trained = it.trained + unitId) }
        return done
    }

    /**
     * A Contrôle of [unitId] with [percent] right. From [PASS_SCORE] the étape is passed: the
     * Parcours moves on (if it was the current étape) and the étape's Révisions start.
     */
    suspend fun recordControl(unitId: Int, percent: Int): Boolean {
        val passed = percent >= PASS_SCORE
        update { p ->
            val next = if (passed && p.current == unitId) units.getOrNull(units.indexOfFirst { it.id == unitId } + 1)?.id ?: unitId else p.current
            p.copy(
                current = next,
                control = p.control + (unitId to maxOf(p.control[unitId] ?: 0, percent)),
                passed = if (passed) p.passed + unitId else p.passed,
                // A passed étape starts its review schedule (a repeated one keeps its own)
                reviews = if (passed && unitId !in p.reviews) p.reviews + (unitId to GrammarReview(today() + REVIEW_DAYS[0], 0)) else p.reviews
            )
        }
        return passed
    }

    /** The most overdue passed étape (not the current one), or null. */
    suspend fun dueReview(): DueReview? = dueReview(current())

    private fun dueReview(p: ParcoursProgress): DueReview? {
        val (id, _) = p.reviews.entries
            .filter { (id, r) -> r.dueDay <= today() && id != p.current }
            .minByOrNull { it.value.dueDay } ?: return null
        val index = units.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        return DueReview(units[index], index)
    }

    /**
     * Result of a Révision quiz (percent): from [REVIEW_PASS_SCORE] the étape moves one step on in
     * [REVIEW_DAYS], otherwise it comes back soon and starts over. Returns days until next time.
     */
    suspend fun recordReview(unitId: Int, score: Int): Int {
        var days = REVIEW_RETRY_DAYS
        update { p ->
            val r = p.reviews[unitId] ?: GrammarReview(today(), 0)
            val next = if (score >= REVIEW_PASS_SCORE) {
                val step = minOf(r.step + 1, REVIEW_DAYS.lastIndex)
                days = REVIEW_DAYS[step]
                GrammarReview(today() + days, step)
            } else GrammarReview(today() + REVIEW_RETRY_DAYS, 0)
            p.copy(reviews = p.reviews + (unitId to next))
        }
        return days
    }

    fun lessonHtml(file: String): String =
        context.assets.open(file).bufferedReader().use { it.readText() }
}
