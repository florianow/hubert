package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.local.DayCount
import com.hubert.data.local.StruggledWord
import com.hubert.data.model.GameSession
import com.hubert.data.repository.Achievement
import com.hubert.data.repository.GameStats
import com.hubert.data.repository.OverviewStats
import com.hubert.data.repository.StatisticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The 6 game types and their display names / colors.
 */
enum class GameType(val key: String, val displayName: String) {
    MATCHING("matching", "Trouvez!"),
    GENDER_SNAP("gender_snap", "Classez!"),
    GAP_FILL("gap_fill", "Compl\u00E9tez!"),
    SPELLING_BEE("spelling_bee", "\u00C9crivez!"),
    CONJUGATION("conjugation", "Conjuguez!"),
    PRONUNCIATION("pronunciation", "Prononcez!");

    companion object {
        fun fromKey(key: String): GameType? = entries.find { it.key == key }
    }
}

data class StatisticsState(
    val isLoading: Boolean = true,
    val overview: OverviewStats = OverviewStats(
        totalSessions = 0, totalPlayTimeMs = 0, totalCorrect = 0,
        totalWrong = 0, overallAccuracy = 0.0, currentStreak = 0,
        longestStreak = 0, bestStreakEver = 0, favoriteGame = null,
        todaySessions = 0
    ),
    val heatmapData: List<DayCount> = emptyList(),
    val selectedGameType: GameType = GameType.MATCHING,
    val gameStats: GameStats = GameStats(
        gameType = "matching", sessionsPlayed = 0, highScore = 0,
        averageScore = 0.0, totalCorrect = 0, totalWrong = 0,
        accuracy = 0.0, bestStreak = 0, averageDurationMs = 0
    ),
    val scoreTrend: List<GameSession> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    val mostMissedWords: List<StruggledWord> = emptyList(),
    val recentlyStruggledWords: List<StruggledWord> = emptyList()
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsState())
    val uiState: StateFlow<StatisticsState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    fun loadStatistics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val overview = statisticsRepository.getOverviewStats()
            val heatmap = statisticsRepository.getHeatmapData(90)
            val selectedType = _uiState.value.selectedGameType
            val gameStats = statisticsRepository.getGameStats(selectedType.key)
            val trend = statisticsRepository.getScoreTrend(selectedType.key)
            val achievements = statisticsRepository.getAchievements()
            val mostMissed = statisticsRepository.getMostMissedWords(20)
            val recentlyStruggled = statisticsRepository.getRecentlyStruggledWords(daysBack = 7, limit = 15)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    overview = overview,
                    heatmapData = heatmap,
                    gameStats = gameStats,
                    scoreTrend = trend,
                    achievements = achievements,
                    mostMissedWords = mostMissed,
                    recentlyStruggledWords = recentlyStruggled
                )
            }
        }
    }

    fun selectGameType(gameType: GameType) {
        _uiState.update { it.copy(selectedGameType = gameType) }
        viewModelScope.launch {
            val stats = statisticsRepository.getGameStats(gameType.key)
            val trend = statisticsRepository.getScoreTrend(gameType.key)
            _uiState.update {
                it.copy(gameStats = stats, scoreTrend = trend)
            }
        }
    }
}
