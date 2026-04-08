package com.hubert.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single answer attempt within a game session.
 * Persisted so we can track which words/questions the player struggles with
 * across sessions.
 */
@Entity(tableName = "word_attempts")
data class WordAttempt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameType: String,           // "matching", "gender_snap", "gap_fill", "spelling_bee", "conjugation", "pronunciation"
    val question: String,           // the question text (e.g., "maison", "être (Présent, je)")
    val yourAnswer: String,         // what the player answered
    val correctAnswer: String,      // what the correct answer was
    val isCorrect: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
