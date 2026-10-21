package com.hubert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hubert.data.model.WordProgress

@Dao
interface WordProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WordProgress>)

    @Query("SELECT * FROM word_progress WHERE rank IN (:ranks)")
    suspend fun getByRanks(ranks: List<Int>): List<WordProgress>

    @Query("SELECT COUNT(*) FROM word_progress")
    suspend fun count(): Int

    /** Words due for review, most overdue first, then the more frequent ones. */
    @Query("SELECT rank FROM word_progress WHERE dueDay <= :today ORDER BY dueDay ASC, rank ASC LIMIT :limit")
    suspend fun dueRanks(today: Long, limit: Int): List<Int>

    @Query("SELECT COUNT(*) FROM word_progress WHERE dueDay <= :today")
    suspend fun countDue(today: Long): Int

    @Query("SELECT COUNT(*) FROM word_progress WHERE intervalDays >= :knownInterval")
    suspend fun countKnown(knownInterval: Double): Int

    @Query("SELECT rank FROM word_progress WHERE introducedDay = :day")
    suspend fun introducedOn(day: Long): List<Int>

    @Query("SELECT rank FROM word_progress")
    suspend fun allRanks(): List<Int>

    /** Already learned words that are not due — filler for practice rounds. */
    @Query("SELECT rank FROM word_progress WHERE dueDay > :today ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomNotDue(today: Long, limit: Int): List<Int>
}
