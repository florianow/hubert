package com.hubert.utils

import com.hubert.data.model.WordProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionTest {

    private fun review(p: WordProgress?, correct: Boolean, day: Long, strength: ReviewStrength = ReviewStrength.RECALL) =
        SpacedRepetition.review(p, rank = 42, correct = correct, strength = strength, today = day)

    /** Review the word correctly every time it becomes due; returns (day, progress) per step. */
    private fun reviewOnDue(steps: Int, strength: ReviewStrength): List<WordProgress> {
        var p = review(null, true, 0, strength)
        val history = mutableListOf(p)
        repeat(steps) {
            p = review(p, true, p.dueDay, strength)
            history += p
        }
        return history
    }

    @Test
    fun first_success_schedules_tomorrow() {
        val p = review(null, true, 100)
        assertEquals(1.0, p.intervalDays, 0.0)
        assertEquals(101L, p.dueDay)
        assertEquals(1, p.reps)
        assertEquals(100L, p.introducedDay)
    }

    @Test
    fun intervals_grow_and_recall_grows_faster_than_recognition() {
        val recall = reviewOnDue(4, ReviewStrength.RECALL)
        val recognition = reviewOnDue(4, ReviewStrength.RECOGNITION)
        recall.zipWithNext().forEach { (a, b) -> assertTrue(b.intervalDays > a.intervalDays) }
        assertTrue(recall.last().intervalDays > recognition.last().intervalDays * 2)
        assertTrue("recall: known after a handful of reviews", recall.last().isKnown)
        assertFalse("recognition alone takes longer", recognition.last().isKnown)
    }

    @Test
    fun only_one_growth_per_day() {
        val first = review(null, true, 0)
        val again = review(first, true, 0)
        assertEquals(first, again)
    }

    @Test
    fun mistake_resets_interval_and_lowers_ease() {
        val known = reviewOnDue(5, ReviewStrength.RECALL).last()
        val failed = review(known, false, known.dueDay)
        assertEquals(1.0, failed.intervalDays, 0.0)
        assertEquals(known.dueDay + 1, failed.dueDay)
        assertEquals(known.ease - SpacedRepetition.EASE_PENALTY, failed.ease, 1e-9)
        assertEquals(1, failed.lapses)
        assertFalse(failed.isKnown)
    }

    @Test
    fun repeated_misses_in_one_round_count_once() {
        val known = reviewOnDue(5, ReviewStrength.RECALL).last()
        val once = review(known, false, known.dueDay)
        val twice = review(once, false, known.dueDay)
        assertEquals(once, twice)
    }

    @Test
    fun ease_never_drops_below_minimum() {
        var p = review(null, true, 0)
        for (day in 1L..30L) p = review(review(p, true, day * 2), false, day * 2 + 1)
        assertEquals(SpacedRepetition.MIN_EASE, p.ease, 1e-9)
    }

    @Test
    fun overdue_success_counts_part_of_the_extra_time() {
        val p = reviewOnDue(1, ReviewStrength.RECALL).last()            // interval 2.5 days
        val onTime = review(p, true, p.lastReviewDay + 3)
        val sixWeeksLater = review(p, true, p.lastReviewDay + 42)
        assertTrue(sixWeeksLater.intervalDays > onTime.intervalDays * 5)
        assertTrue("remembered after 6 weeks → sicher", sixWeeksLater.isKnown)
    }

    @Test
    fun early_practice_does_not_inflate_the_interval() {
        val p = reviewOnDue(4, ReviewStrength.RECALL).last()        // interval well above 2 days
        val early = review(p, true, p.lastReviewDay + 2)
        assertEquals(p.intervalDays, early.intervalDays, 0.0)
    }
}
