package com.hubert.data.local

import androidx.room.*
import com.hubert.data.model.GameSession
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for game sessions.
 * Provides all queries needed by the Statistics screen.
 */
@Dao
interface GameSessionDao {

    @Insert
    suspend fun insert(session: GameSession): Long

    // ── Overview stats ──────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM game_sessions")
    suspend fun getTotalSessionCount(): Int

    @Query("SELECT COUNT(*) FROM game_sessions WHERE gameType = :gameType")
    suspend fun getSessionCount(gameType: String): Int

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM game_sessions")
    suspend fun getTotalPlayTimeMs(): Long

    @Query("SELECT COALESCE(SUM(totalCorrect), 0) FROM game_sessions")
    suspend fun getTotalCorrect(): Int

    @Query("SELECT COALESCE(SUM(totalWrong), 0) FROM game_sessions")
    suspend fun getTotalWrong(): Int

    @Query("SELECT MAX(bestStreak) FROM game_sessions")
    suspend fun getOverallBestStreak(): Int?

    // ── Per-game stats ──────────────────────────────────────────────────

    @Query("SELECT MAX(score) FROM game_sessions WHERE gameType = :gameType")
    suspend fun getHighestScore(gameType: String): Int?

    @Query("SELECT COALESCE(AVG(score), 0) FROM game_sessions WHERE gameType = :gameType")
    suspend fun getAverageScore(gameType: String): Double

    @Query("SELECT MAX(bestStreak) FROM game_sessions WHERE gameType = :gameType")
    suspend fun getBestStreak(gameType: String): Int?

    @Query("SELECT COALESCE(SUM(totalCorrect), 0) FROM game_sessions WHERE gameType = :gameType")
    suspend fun getTotalCorrectForGame(gameType: String): Int

    @Query("SELECT COALESCE(SUM(totalWrong), 0) FROM game_sessions WHERE gameType = :gameType")
    suspend fun getTotalWrongForGame(gameType: String): Int

    @Query("SELECT COALESCE(AVG(durationMs), 0) FROM game_sessions WHERE gameType = :gameType")
    suspend fun getAverageDurationMs(gameType: String): Long

    // ── Top scores (replaces old HighScore queries) ─────────────────────

    @Query("SELECT * FROM game_sessions WHERE gameType = :gameType ORDER BY score DESC LIMIT 10")
    fun getTopScores(gameType: String): Flow<List<GameSession>>

    @Query("SELECT * FROM game_sessions ORDER BY score DESC LIMIT 10")
    fun getAllTopScores(): Flow<List<GameSession>>

    // ── Time-based queries ──────────────────────────────────────────────

    /** All sessions for a game type, ordered by time (for score trend charts). */
    @Query("SELECT * FROM game_sessions WHERE gameType = :gameType ORDER BY timestamp ASC")
    suspend fun getSessionsForGame(gameType: String): List<GameSession>

    /** All sessions in a time range (for heatmap, filtered views). */
    @Query("SELECT * FROM game_sessions WHERE timestamp >= :fromMs ORDER BY timestamp ASC")
    suspend fun getSessionsSince(fromMs: Long): List<GameSession>

    /** Distinct days with at least one session (for streak calculation). */
    @Query("""
        SELECT DISTINCT date(timestamp / 1000, 'unixepoch', 'localtime') as day 
        FROM game_sessions 
        ORDER BY day DESC
    """)
    suspend fun getDistinctDays(): List<String>

    /** Sessions per day for the heatmap (count + date). */
    @Query("""
        SELECT date(timestamp / 1000, 'unixepoch', 'localtime') as day, 
               COUNT(*) as count 
        FROM game_sessions 
        WHERE timestamp >= :fromMs
        GROUP BY day 
        ORDER BY day ASC
    """)
    suspend fun getSessionCountByDay(fromMs: Long): List<DayCount>

    /** Most played game type. */
    @Query("SELECT gameType FROM game_sessions GROUP BY gameType ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getMostPlayedGameType(): String?

    /** Total play time per game type (for "Hubert choisit!" least-played logic). */
    @Query("""
        SELECT gameType, COALESCE(SUM(durationMs), 0) AS totalMs
        FROM game_sessions
        GROUP BY gameType
    """)
    suspend fun getTotalDurationPerGameType(): List<GameTypeDuration>

    @Query("DELETE FROM game_sessions")
    suspend fun clearAll()
}

/** Helper class for the heatmap query result. */
data class DayCount(
    val day: String,    // "2026-04-08"
    val count: Int
)

/** Helper class for total duration per game type. */
data class GameTypeDuration(
    val gameType: String,
    val totalMs: Long
)
