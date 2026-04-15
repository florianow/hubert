package com.hubert.data.repository

import com.hubert.data.local.DayCount
import com.hubert.data.local.GameSessionDao
import com.hubert.data.local.GameTypeDuration
import com.hubert.data.local.StruggledWord
import com.hubert.data.local.WordAttemptDao
import com.hubert.data.model.GameSession
import com.hubert.data.model.WordAttempt
import com.hubert.viewmodel.AnswerRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregated statistics data for the dashboard.
 */
data class OverviewStats(
    val totalSessions: Int,
    val totalPlayTimeMs: Long,
    val totalCorrect: Int,
    val totalWrong: Int,
    val overallAccuracy: Double,        // 0.0 - 1.0
    val currentStreak: Int,             // consecutive days ending today
    val longestStreak: Int,
    val bestStreakEver: Int,             // best in-game streak across all sessions
    val favoriteGame: String?,          // most played game type
    val todaySessions: Int
)

/**
 * Per-game statistics.
 */
data class GameStats(
    val gameType: String,
    val sessionsPlayed: Int,
    val highScore: Int,
    val averageScore: Double,
    val totalCorrect: Int,
    val totalWrong: Int,
    val accuracy: Double,               // 0.0 - 1.0
    val bestStreak: Int,
    val averageDurationMs: Long
)

/**
 * Achievement definition and unlock state.
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,                   // emoji
    val isUnlocked: Boolean,
    val progress: String?               // e.g. "4/10" for partial progress, null if binary
)

@Singleton
class StatisticsRepository @Inject constructor(
    private val dao: GameSessionDao,
    private val wordAttemptDao: WordAttemptDao
) {

    // ── Session saving (used by all ViewModels) ─────────────────────────

    suspend fun saveSession(
        gameType: String,
        score: Int,
        totalCorrect: Int,
        totalWrong: Int,
        bestStreak: Int,
        durationMs: Long
    ): Long {
        return dao.insert(
            GameSession(
                gameType = gameType,
                score = score,
                totalCorrect = totalCorrect,
                totalWrong = totalWrong,
                bestStreak = bestStreak,
                durationMs = durationMs
            )
        )
    }

    suspend fun getHighestScore(gameType: String): Int {
        return dao.getHighestScore(gameType) ?: 0
    }

    // ── Hubert choisit! ─────────────────────────────────────────────────

    /**
     * Returns the game type with the least total play time.
     * Games never played get 0ms and are preferred.
     * Ties are broken randomly.
     */
    suspend fun getLeastPlayedGameType(): String {
        val allGameTypes = listOf(
            "matching", "gender_snap", "gap_fill",
            "spelling_bee", "conjugation", "pronunciation", "preposition"
        )
        val durations = dao.getTotalDurationPerGameType()
        val durationMap = durations.associate { it.gameType to it.totalMs }

        // Fill in 0 for any game type not yet played
        val full = allGameTypes.map { it to (durationMap[it] ?: 0L) }
        val minDuration = full.minOf { it.second }

        // Among all tied at minimum, pick randomly
        val candidates = full.filter { it.second == minDuration }.map { it.first }
        return candidates.random()
    }

    // ── Overview ─────────────────────────────────────────────────────────

    suspend fun getOverviewStats(): OverviewStats {
        val totalSessions = dao.getTotalSessionCount()
        val totalPlayTimeMs = dao.getTotalPlayTimeMs()
        val totalCorrect = dao.getTotalCorrect()
        val totalWrong = dao.getTotalWrong()
        val total = totalCorrect + totalWrong
        val accuracy = if (total > 0) totalCorrect.toDouble() / total else 0.0

        val days = dao.getDistinctDays()
        val (currentStreak, longestStreak) = calculateStreaks(days)

        val bestStreak = dao.getOverallBestStreak() ?: 0
        val favoriteGame = dao.getMostPlayedGameType()

        // Today's sessions
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todaySessions = dao.getSessionsSince(todayStart).size

        return OverviewStats(
            totalSessions = totalSessions,
            totalPlayTimeMs = totalPlayTimeMs,
            totalCorrect = totalCorrect,
            totalWrong = totalWrong,
            overallAccuracy = accuracy,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            bestStreakEver = bestStreak,
            favoriteGame = favoriteGame,
            todaySessions = todaySessions
        )
    }

    // ── Per-game stats ──────────────────────────────────────────────────

    suspend fun getGameStats(gameType: String): GameStats {
        val sessions = dao.getSessionCount(gameType)
        val highScore = dao.getHighestScore(gameType) ?: 0
        val avgScore = dao.getAverageScore(gameType)
        val totalCorrect = dao.getTotalCorrectForGame(gameType)
        val totalWrong = dao.getTotalWrongForGame(gameType)
        val total = totalCorrect + totalWrong
        val accuracy = if (total > 0) totalCorrect.toDouble() / total else 0.0
        val bestStreak = dao.getBestStreak(gameType) ?: 0
        val avgDuration = dao.getAverageDurationMs(gameType)

        return GameStats(
            gameType = gameType,
            sessionsPlayed = sessions,
            highScore = highScore,
            averageScore = avgScore,
            totalCorrect = totalCorrect,
            totalWrong = totalWrong,
            accuracy = accuracy,
            bestStreak = bestStreak,
            averageDurationMs = avgDuration
        )
    }

    // ── Heatmap data ────────────────────────────────────────────────────

    suspend fun getHeatmapData(daysBack: Int = 90): List<DayCount> {
        val fromMs = LocalDate.now().minusDays(daysBack.toLong())
            .atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        return dao.getSessionCountByDay(fromMs)
    }

    // ── Score trend ─────────────────────────────────────────────────────

    suspend fun getScoreTrend(gameType: String): List<GameSession> {
        return dao.getSessionsForGame(gameType)
    }

    // ── Top scores ──────────────────────────────────────────────────────

    fun getTopScores(gameType: String): Flow<List<GameSession>> {
        return dao.getTopScores(gameType)
    }

    fun getAllTopScores(): Flow<List<GameSession>> {
        return dao.getAllTopScores()
    }

    // ── Achievements ────────────────────────────────────────────────────

    suspend fun getAchievements(): List<Achievement> {
        val overview = getOverviewStats()
        val allGameTypes = listOf("matching", "gender_snap", "gap_fill", "spelling_bee", "conjugation", "pronunciation")

        // Check which game types have been played
        val gamesPlayed = allGameTypes.count { dao.getSessionCount(it) > 0 }

        // Per-game accuracy for specific achievements
        val genderAccuracy = getGameStats("gender_snap").accuracy
        val spellingAccuracy = getGameStats("spelling_bee").accuracy
        val genderSessions = dao.getSessionCount("gender_snap")
        val spellingSessions = dao.getSessionCount("spelling_bee")

        return listOf(
            Achievement(
                id = "first_steps",
                title = "First Steps",
                description = "Play all 6 game modes",
                icon = "\uD83D\uDC63",       // footprints
                isUnlocked = gamesPlayed >= 6,
                progress = if (gamesPlayed < 6) "$gamesPlayed/6" else null
            ),
            Achievement(
                id = "century",
                title = "Century",
                description = "Score 100+ in any game",
                icon = "\uD83D\uDCAF",       // 100
                isUnlocked = allGameTypes.any { (dao.getHighestScore(it) ?: 0) >= 100 },
                progress = null
            ),
            Achievement(
                id = "streak_3",
                title = "Getting Started",
                description = "3-day play streak",
                icon = "\uD83D\uDD25",       // fire
                isUnlocked = overview.longestStreak >= 3,
                progress = if (overview.longestStreak < 3) "${overview.currentStreak}/3" else null
            ),
            Achievement(
                id = "streak_7",
                title = "Week Warrior",
                description = "7-day play streak",
                icon = "\uD83D\uDD25",       // fire
                isUnlocked = overview.longestStreak >= 7,
                progress = if (overview.longestStreak < 7) "${overview.currentStreak}/7" else null
            ),
            Achievement(
                id = "streak_30",
                title = "Streak Master",
                description = "30-day play streak",
                icon = "\u2B50",             // star
                isUnlocked = overview.longestStreak >= 30,
                progress = if (overview.longestStreak < 30) "${overview.currentStreak}/30" else null
            ),
            Achievement(
                id = "perfect_10",
                title = "Perfect 10",
                description = "Get 10 correct in a row",
                icon = "\uD83C\uDFAF",       // target
                isUnlocked = overview.bestStreakEver >= 10,
                progress = if (overview.bestStreakEver < 10) "${overview.bestStreakEver}/10" else null
            ),
            Achievement(
                id = "sessions_10",
                title = "Regular",
                description = "Play 10 sessions",
                icon = "\uD83C\uDFAE",       // game controller
                isUnlocked = overview.totalSessions >= 10,
                progress = if (overview.totalSessions < 10) "${overview.totalSessions}/10" else null
            ),
            Achievement(
                id = "sessions_50",
                title = "Dedicated",
                description = "Play 50 sessions",
                icon = "\uD83C\uDFC6",       // trophy
                isUnlocked = overview.totalSessions >= 50,
                progress = if (overview.totalSessions < 50) "${overview.totalSessions}/50" else null
            ),
            Achievement(
                id = "sessions_100",
                title = "Centurion",
                description = "Play 100 sessions",
                icon = "\uD83D\uDC51",       // crown
                isUnlocked = overview.totalSessions >= 100,
                progress = if (overview.totalSessions < 100) "${overview.totalSessions}/100" else null
            ),
            Achievement(
                id = "marathon",
                title = "Marathon",
                description = "1 hour total play time",
                icon = "\u23F1\uFE0F",       // stopwatch
                isUnlocked = overview.totalPlayTimeMs >= 3_600_000,
                progress = if (overview.totalPlayTimeMs < 3_600_000)
                    "${overview.totalPlayTimeMs / 60_000}m/60m" else null
            ),
            Achievement(
                id = "gender_expert",
                title = "Gender Expert",
                description = "90%+ accuracy in Classez! (10+ games)",
                icon = "\uD83C\uDDEB\uD83C\uDDF7",  // French flag
                isUnlocked = genderSessions >= 10 && genderAccuracy >= 0.9,
                progress = if (genderSessions < 10) "$genderSessions/10 games"
                else if (genderAccuracy < 0.9) "${(genderAccuracy * 100).toInt()}%/90%"
                else null
            ),
            Achievement(
                id = "spelling_ace",
                title = "Spelling Ace",
                description = "90%+ accuracy in \u00C9crivez! (10+ games)",
                icon = "\uD83D\uDCDD",       // memo
                isUnlocked = spellingSessions >= 10 && spellingAccuracy >= 0.9,
                progress = if (spellingSessions < 10) "$spellingSessions/10 games"
                else if (spellingAccuracy < 0.9) "${(spellingAccuracy * 100).toInt()}%/90%"
                else null
            ),
            Achievement(
                id = "accuracy_80",
                title = "Sharp Mind",
                description = "80%+ overall accuracy (50+ sessions)",
                icon = "\uD83E\uDDE0",       // brain
                isUnlocked = overview.totalSessions >= 50 && overview.overallAccuracy >= 0.8,
                progress = if (overview.totalSessions < 50) "${overview.totalSessions}/50 games"
                else if (overview.overallAccuracy < 0.8) "${(overview.overallAccuracy * 100).toInt()}%/80%"
                else null
            )
        )
    }

    // ── Word attempt tracking ─────────────────────────────────────────

    /**
     * Save a list of AnswerRecords from a game session as WordAttempt entities.
     */
    suspend fun saveWordAttempts(gameType: String, answers: List<AnswerRecord>) {
        if (answers.isEmpty()) return
        val now = System.currentTimeMillis()
        val attempts = answers.map { record ->
            WordAttempt(
                gameType = gameType,
                question = record.question,
                yourAnswer = record.yourAnswer,
                correctAnswer = record.correctAnswer,
                isCorrect = record.isCorrect,
                timestamp = now
            )
        }
        wordAttemptDao.insertAll(attempts)
    }

    /**
     * Get the most-missed words across all sessions (minimum 2 attempts).
     */
    suspend fun getMostMissedWords(limit: Int = 20): List<StruggledWord> {
        return wordAttemptDao.getMostMissedWords(minAttempts = 2, limit = limit)
    }

    /**
     * Get words the player recently struggled with (last 7 days).
     */
    suspend fun getRecentlyStruggledWords(daysBack: Int = 7, limit: Int = 15): List<StruggledWord> {
        val sinceMs = LocalDate.now().minusDays(daysBack.toLong())
            .atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        return wordAttemptDao.getRecentlyStruggledWords(sinceMs = sinceMs, limit = limit)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Calculate current and longest streak from a descending list of
     * distinct date strings ("2026-04-08", "2026-04-07", ...).
     */
    private fun calculateStreaks(daysDesc: List<String>): Pair<Int, Int> {
        if (daysDesc.isEmpty()) return Pair(0, 0)

        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val dates = daysDesc.map { LocalDate.parse(it, formatter) }
        val today = LocalDate.now()

        // Current streak: consecutive days ending today (or yesterday)
        var currentStreak = 0
        var expected = today
        for (date in dates) {
            if (date == expected) {
                currentStreak++
                expected = expected.minusDays(1)
            } else if (date == today.minusDays(1) && currentStreak == 0) {
                // Allow streak to start from yesterday (if no session today yet)
                expected = today.minusDays(1)
                if (date == expected) {
                    currentStreak++
                    expected = expected.minusDays(1)
                }
            } else {
                break
            }
        }

        // Longest streak: scan all dates
        var longestStreak = if (dates.isNotEmpty()) 1 else 0
        var runLength = 1
        for (i in 1 until dates.size) {
            if (dates[i] == dates[i - 1].minusDays(1)) {
                runLength++
                longestStreak = maxOf(longestStreak, runLength)
            } else {
                runLength = 1
            }
        }

        return Pair(currentStreak, maxOf(longestStreak, currentStreak))
    }
}
