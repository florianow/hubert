package com.hubert.data.local

import androidx.room.*
import com.hubert.data.model.WordAttempt

/**
 * Data access object for per-word attempt tracking.
 * Enables "Words I Struggle With" statistics.
 */
@Dao
interface WordAttemptDao {

    @Insert
    suspend fun insertAll(attempts: List<WordAttempt>)

    /**
     * Most-missed words: group by question text, require at least [minAttempts] tries,
     * order by error rate (desc) then total attempts (desc).
     */
    @Query("""
        SELECT question,
               gameType,
               correctAnswer,
               COUNT(*) AS totalAttempts,
               SUM(CASE WHEN isCorrect = 0 THEN 1 ELSE 0 END) AS totalWrong,
               CAST(SUM(CASE WHEN isCorrect = 0 THEN 1 ELSE 0 END) AS REAL) / COUNT(*) AS errorRate
        FROM word_attempts
        GROUP BY question
        HAVING COUNT(*) >= :minAttempts
        ORDER BY errorRate DESC, totalAttempts DESC
        LIMIT :limit
    """)
    suspend fun getMostMissedWords(minAttempts: Int = 2, limit: Int = 20): List<StruggledWord>

    /**
     * Recently struggled words: wrong attempts in the last [sinceDaysAgo] days,
     * grouped and ordered by error count.
     */
    @Query("""
        SELECT question,
               gameType,
               correctAnswer,
               COUNT(*) AS totalAttempts,
               SUM(CASE WHEN isCorrect = 0 THEN 1 ELSE 0 END) AS totalWrong,
               CAST(SUM(CASE WHEN isCorrect = 0 THEN 1 ELSE 0 END) AS REAL) / COUNT(*) AS errorRate
        FROM word_attempts
        WHERE timestamp >= :sinceMs
        GROUP BY question
        HAVING SUM(CASE WHEN isCorrect = 0 THEN 1 ELSE 0 END) > 0
        ORDER BY totalWrong DESC, errorRate DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyStruggledWords(sinceMs: Long, limit: Int = 15): List<StruggledWord>

    @Query("DELETE FROM word_attempts")
    suspend fun clearAll()
}

/** Query result for struggled-word aggregation. */
data class StruggledWord(
    val question: String,
    val gameType: String,
    val correctAnswer: String,
    val totalAttempts: Int,
    val totalWrong: Int,
    val errorRate: Double
)
