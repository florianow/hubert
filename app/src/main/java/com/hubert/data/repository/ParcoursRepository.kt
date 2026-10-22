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
 * One étape of the Parcours: a grammar point with its lesson(s) and the grammar game
 * (GameSession key) with its focus that serves as Plat du chef. [theme] supplies the word
 * hints of the étape's Parlez! conversation; the menus keep the theme of the day.
 */
/** The étape's conversation for Parlez! (scripts/build_parcours.py, PARLEZ). */
data class ParcoursParlez(
    val themeFr: String,
    val scenarioDe: String,
    val hubertRoleFr: String,
    val starterFr: String,
    val grammarFr: String
)

data class ParcoursUnit(
    val id: Int,
    val level: String,
    val title: String,
    val theme: String,
    val lessons: List<ParcoursLesson>,
    val game: String,
    val focus: List<String>,
    val parlez: ParcoursParlez? = null
)

/** Spaced review of a passed étape's grammar: due on [dueDay], [step] into REVIEW_DAYS. */
data class GrammarReview(val dueDay: Long, val step: Int)

/** Stored progress. [menus]: menus finished per unit; [best]: best menu score (0–100). */
data class ParcoursProgress(
    val current: Int = 1,
    val lessonsRead: Set<Int> = emptySet(),
    val menus: Map<Int, Int> = emptyMap(),
    val best: Map<Int, Int> = emptyMap(),
    val passed: Set<Int> = emptySet(),
    val reviews: Map<Int, GrammarReview> = emptyMap()
)

/** A passed étape whose grammar is due again. */
data class DueReview(val unit: ParcoursUnit, val index: Int)

/** Where the current unit stands: Leçon → 2 menus → Contrôle (a Complet with ≥ 12/20). */
data class ParcoursStatus(
    val unit: ParcoursUnit,
    val index: Int,               // 0-based position in the Parcours
    val total: Int,
    val lessonRead: Boolean,
    val menusDone: Int,
    val enabled: Boolean,
    val dueReviews: Int = 0       // passed étapes whose grammar is due again
) {
    val controlUnlocked: Boolean get() = menusDone >= ParcoursRepository.MENUS_BEFORE_CONTROL
}

/**
 * The Parcours: units from A1 to B2 (assets/parcours.json, built by scripts/build_parcours.py
 * from the anki_french grammar notes). Menu du jour serves the current unit's grammar game
 * (the theme of the day stays) and a passed Contrôle moves on to the next one.
 */
@Singleton
class ParcoursRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val MENUS_BEFORE_CONTROL = 2
        /** Contrôle passed from 12/20. */
        const val PASS_SCORE = 60
        /** Days until a passed étape comes back as Révision; a good Révision moves one step on. */
        val REVIEW_DAYS = listOf(3, 7, 21, 60)
        /** A Révision counts as good from 70% (Plat-du-chef score). */
        const val REVIEW_PASS_SCORE = 70
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
                    reviews = it.reviews ?: emptyMap()
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
            menusDone = p.menus[unit.id] ?: 0,
            enabled = enabled,
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

    /**
     * A menu of the current unit was finished. Once [MENUS_BEFORE_CONTROL] menus are done, a
     * Complet counts as Contrôle: with [PASS_SCORE] or more the unit is passed and the next
     * one starts. Returns true when the unit was passed.
     */
    suspend fun recordMenu(isComplet: Boolean, score: Int): Boolean {
        var passed = false
        update { p ->
            val id = p.current
            val before = p.menus[id] ?: 0
            val isControl = isComplet && before >= MENUS_BEFORE_CONTROL
            passed = isControl && score >= PASS_SCORE
            val next = if (passed) units.getOrNull(units.indexOfFirst { it.id == id } + 1)?.id ?: id else id
            p.copy(
                current = next,
                menus = p.menus + (id to before + 1),
                best = p.best + (id to maxOf(p.best[id] ?: 0, score)),
                passed = if (passed) p.passed + id else p.passed,
                // A passed étape starts its review schedule (a repeated one keeps its own)
                reviews = if (passed && id !in p.reviews) p.reviews + (id to GrammarReview(today() + REVIEW_DAYS[0], 0)) else p.reviews
            )
        }
        return passed
    }

    /** The most overdue passed étape (not the current one), or null. */
    suspend fun dueReview(): DueReview? {
        val p = current()
        val (id, _) = p.reviews.entries
            .filter { (id, r) -> r.dueDay <= today() && id != p.current }
            .minByOrNull { it.value.dueDay } ?: return null
        val index = units.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        return DueReview(units[index], index)
    }

    /**
     * Result of a Révision course: from [REVIEW_PASS_SCORE] the étape moves one step on in
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
