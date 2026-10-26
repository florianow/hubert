package com.hubert.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Spaced-repetition state of one vocab word (by frequency rank). A row exists once the word
 * has been introduced or answered; words without a row are "new".
 */
@Entity(tableName = "word_progress")
data class WordProgress(
    @PrimaryKey val rank: Int,
    val intervalDays: Double,       // current review interval
    val ease: Double,               // growth factor, lowered by mistakes
    val dueDay: Long,               // epoch day of the next review
    val reps: Int,                  // successful reviews
    val lapses: Int,                // times forgotten
    val lastReviewDay: Long,        // epoch day of the last counted review
    val introducedDay: Long         // epoch day the word entered the learning path
) {
    val isKnown: Boolean get() = intervalDays >= KNOWN_INTERVAL_DAYS

    companion object {
        /** A word counts as "sicher" once it survives three weeks without review. */
        const val KNOWN_INTERVAL_DAYS = 21.0
    }
}
