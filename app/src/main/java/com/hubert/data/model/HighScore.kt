package com.hubert.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * High score entry persisted in Room database.
 * gameType distinguishes scores across different game modes.
 */
@Entity(tableName = "high_scores")
data class HighScore(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val score: Int,
    val matchesCompleted: Int,
    val roundsCompleted: Int,
    val gameType: String = "matching",  // "matching", "gender_snap", "gap_fill", "spelling_bee"
    val timestamp: Long = System.currentTimeMillis()
)
