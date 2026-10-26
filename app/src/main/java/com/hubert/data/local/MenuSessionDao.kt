package com.hubert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hubert.data.model.MenuSession

@Dao
interface MenuSessionDao {

    @Insert
    suspend fun insert(session: MenuSession): Long

    @Query("SELECT * FROM menu_sessions WHERE menuType = :menuType ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(menuType: String): MenuSession?

    @Query("SELECT * FROM menu_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MenuSession>

    @Query("SELECT * FROM menu_sessions WHERE timestamp >= :fromMs AND timestamp < :toMs ORDER BY timestamp ASC")
    suspend fun getBetween(fromMs: Long, toMs: Long): List<MenuSession>

    @Query("SELECT * FROM menu_sessions WHERE menuType = :menuType AND timestamp < :beforeMs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBefore(menuType: String, beforeMs: Long): MenuSession?

    @Query("SELECT COUNT(*) FROM menu_sessions")
    suspend fun getCount(): Int
}
