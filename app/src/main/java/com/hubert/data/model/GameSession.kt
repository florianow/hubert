package com.hubert.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single game session. Saved at the end of every game.
 *
 * This replaces the old HighScore-only approach with richer data
 * that enables the Statistics screen (heatmaps, trends, per-game stats).
 */
@Entity(tableName = "game_sessions")
data class GameSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameType: String,           // "matching", "gender_snap", "gap_fill", "spelling_bee", "conjugation", "pronunciation"
    val score: Int,
    val totalCorrect: Int,
    val totalWrong: Int,
    val bestStreak: Int,
    val durationMs: Long,           // wall-clock time from game start to game end
    val timestamp: Long = System.currentTimeMillis()
)
