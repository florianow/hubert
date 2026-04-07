package com.hubert.data.repository

import com.hubert.data.local.HighScoreDao
import com.hubert.data.model.HighScore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighScoreRepository @Inject constructor(
    private val highScoreDao: HighScoreDao
) {
    fun getTopScores(gameType: String = "matching"): Flow<List<HighScore>> =
        highScoreDao.getTopScores(gameType)

    fun getAllTopScores(): Flow<List<HighScore>> = highScoreDao.getAllTopScores()

    suspend fun getHighestScore(gameType: String = "matching"): Int =
        highScoreDao.getHighestScore(gameType) ?: 0

    suspend fun saveScore(
        score: Int,
        matchesCompleted: Int,
        roundsCompleted: Int,
        gameType: String = "matching"
    ): Long {
        return highScoreDao.insertScore(
            HighScore(
                score = score,
                matchesCompleted = matchesCompleted,
                roundsCompleted = roundsCompleted,
                gameType = gameType
            )
        )
    }

    suspend fun clearAll() = highScoreDao.clearAll()
}
