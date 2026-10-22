package com.hubert.utils

import com.hubert.data.model.WordProgress
import kotlin.math.max

/**
 * How strongly an answer proves that a word is known. Typing the word from audio
 * (Écrivez!) is real recall; picking it from options or matching pairs is recognition.
 */
enum class ReviewStrength(val growthShare: Double) {
    EASY(1.3),          // Anki "Einfach": recalled effortlessly
    RECALL(1.0),        // Anki "Gut"
    RECOGNITION(0.5);   // Anki "Schwer" / recognising the word among options

    companion object {
        fun forGameType(gameType: String): ReviewStrength? = when (gameType) {
            "spelling_bee", "recall" -> RECALL
            // Dites! records its own Anki-style grades (Nochmal/Schwer/Gut/Einfach)
            "matching", "gender_snap", "gap_fill", "listening" -> RECOGNITION
            else -> null      // grammar/number games don't review vocab words
        }
    }
}

/**
 * SM-2 style scheduling (as in Anki), adapted for game answers:
 * - correct: interval grows by `ease` (recall) or half of the growth (recognition),
 *   based on the time actually elapsed — practising a word early doesn't inflate it, while
 *   remembering it after an overdue gap counts half of the extra time (as Anki does)
 * - wrong: back to 1 day, ease -0.2 (min 1.3), counted as a lapse
 * - at most one growth and one lapse per word and day, so five correct answers in one
 *   round don't count as five reviews
 */
object SpacedRepetition {

    const val START_EASE = 2.5
    const val MIN_EASE = 1.3
    const val EASE_PENALTY = 0.2
    /** "Einfach" on a new word skips the first steps, as in Anki. */
    const val EASY_FIRST_INTERVAL = 4.0
    /** Minimum growth for "Einfach", also when the word was practised before it was due. */
    const val EASY_BONUS = 1.3

    fun review(current: WordProgress?, rank: Int, correct: Boolean, strength: ReviewStrength, today: Long): WordProgress {
        val p = current ?: WordProgress(
            rank = rank, intervalDays = 0.0, ease = START_EASE, dueDay = today,
            reps = 0, lapses = 0, lastReviewDay = -1, introducedDay = today
        )
        val reviewedToday = p.lastReviewDay == today

        if (!correct) {
            // One lapse per day is enough — repeated misses in the same round don't stack
            if (reviewedToday && p.intervalDays <= 1.0 && p.reps == 0) return p
            return p.copy(
                intervalDays = 1.0,
                ease = max(MIN_EASE, p.ease - EASE_PENALTY),
                dueDay = today + 1,
                reps = 0,
                lapses = p.lapses + if (p.reps > 0 || p.intervalDays > 1.0) 1 else 0,
                lastReviewDay = today
            )
        }

        if (reviewedToday) return p     // already counted today

        val elapsed = if (p.lastReviewDay < 0) 0.0 else (today - p.lastReviewDay).toDouble()
        val newInterval = if (p.intervalDays < 1.0) {
            if (strength == ReviewStrength.EASY) EASY_FIRST_INTERVAL else 1.0   // first success
        } else {
            // Grow from the time that really passed: early practice grows less, an overdue
            // success proves more and counts half of the extra days
            val base = if (elapsed < p.intervalDays) max(1.0, elapsed)
                       else p.intervalDays + (elapsed - p.intervalDays) / 2
            val factor = 1.0 + (p.ease - 1.0) * strength.growthShare
            val grown = max(p.intervalDays, base * factor)
            // "Einfach" is an explicit judgment: it always moves the word further out, even early
            if (strength == ReviewStrength.EASY) max(grown, p.intervalDays * EASY_BONUS) else grown
        }
        return p.copy(
            intervalDays = newInterval,
            dueDay = today + Math.round(newInterval),
            reps = p.reps + 1,
            lastReviewDay = today
        )
    }
}
