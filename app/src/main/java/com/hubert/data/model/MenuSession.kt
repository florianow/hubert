package com.hubert.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One completed "Menu du jour" run (several games in a row + final grade).
 */
@Entity(tableName = "menu_sessions")
data class MenuSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "A_LA_CARTE")
    val menuType: String,           // MenuType name: EXPRESS, COMPLET, A_LA_CARTE
    val category: String,           // thematic category the menu was built around
    val overallScore: Int,          // 0–100 (grade on /20 = overallScore / 5)
    val stageScores: String,        // "matching:82,gap_fill:64,..." per-course scores 0–100
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
