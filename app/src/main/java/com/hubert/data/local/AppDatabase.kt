package com.hubert.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hubert.data.model.GameSession
import com.hubert.data.model.HighScore
import com.hubert.data.model.WordAttempt

@Database(
    entities = [HighScore::class, GameSession::class, WordAttempt::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun highScoreDao(): HighScoreDao
    abstract fun gameSessionDao(): GameSessionDao
    abstract fun wordAttemptDao(): WordAttemptDao
}
