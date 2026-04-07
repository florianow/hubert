package com.hubert.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hubert.data.model.HighScore

@Database(
    entities = [HighScore::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun highScoreDao(): HighScoreDao
}
