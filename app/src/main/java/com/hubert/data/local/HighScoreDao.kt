package com.hubert.data.local

import androidx.room.*
import com.hubert.data.model.HighScore
import kotlinx.coroutines.flow.Flow

@Dao
interface HighScoreDao {

    @Query("SELECT * FROM high_scores WHERE gameType = :gameType ORDER BY score DESC LIMIT 10")
    fun getTopScores(gameType: String): Flow<List<HighScore>>

    @Query("SELECT * FROM high_scores ORDER BY score DESC LIMIT 10")
    fun getAllTopScores(): Flow<List<HighScore>>

    @Query("SELECT MAX(score) FROM high_scores WHERE gameType = :gameType")
    suspend fun getHighestScore(gameType: String): Int?

    @Insert
    suspend fun insertScore(score: HighScore): Long

    @Query("DELETE FROM high_scores")
    suspend fun clearAll()
}
