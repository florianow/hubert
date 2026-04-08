package com.hubert.di

import android.content.Context
import androidx.room.Room
import com.hubert.data.local.AppDatabase
import com.hubert.data.local.GameSessionDao
import com.hubert.data.local.HighScoreDao
import com.hubert.data.local.WordAttemptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hubert_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideHighScoreDao(database: AppDatabase): HighScoreDao {
        return database.highScoreDao()
    }

    @Provides
    fun provideGameSessionDao(database: AppDatabase): GameSessionDao {
        return database.gameSessionDao()
    }

    @Provides
    fun provideWordAttemptDao(database: AppDatabase): WordAttemptDao {
        return database.wordAttemptDao()
    }
}
