package com.hubert.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hubert.data.model.GameSession
import com.hubert.data.model.HighScore
import com.hubert.data.model.MenuSession
import com.hubert.data.model.WordAttempt

@Database(
    entities = [HighScore::class, GameSession::class, WordAttempt::class, MenuSession::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun highScoreDao(): HighScoreDao
    abstract fun gameSessionDao(): GameSessionDao
    abstract fun wordAttemptDao(): WordAttemptDao
    abstract fun menuSessionDao(): MenuSessionDao

    companion object {
        /** v5: adds menu_sessions for "Menu du jour" without wiping existing stats. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `menu_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL,
                        `overallScore` INTEGER NOT NULL,
                        `stageScores` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** v6: menus come in three types (Express / Complet / À la carte). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `menu_sessions` ADD COLUMN `menuType` TEXT NOT NULL DEFAULT 'A_LA_CARTE'")
            }
        }
    }
}
