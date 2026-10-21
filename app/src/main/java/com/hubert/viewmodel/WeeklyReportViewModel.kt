package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.local.GameSessionDao
import com.hubert.data.local.MenuSessionDao
import com.hubert.data.local.WordAttemptDao
import com.hubert.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.roundToInt

/** Accuracy of one game in the report week, compared with the week before. */
data class GameWeekStat(
    val gameName: String,
    val answers: Int,
    val accuracy: Double,          // 0.0–1.0
    val previousAccuracy: Double?  // null when not played the week before
)

/** A question missed in the report week. */
data class MissedItem(
    val answer: String,            // what was correct
    val context: String,           // the question it was asked in
    val gameName: String,
    val wrong: Int
)

data class WeeklyReport(
    val weekStart: LocalDate,
    val activeDays: List<Boolean>,     // Monday … Sunday
    val sessions: Int,
    val playMinutes: Int,
    val menus: Int,
    val averageGrade: Double?,         // /20, null without menus
    val bestGrade: Double?,
    val previousAverageGrade: Double?,
    val gradeTrend: List<Pair<LocalDate, Double?>>,   // average menu grade of the last 8 weeks
    val games: List<GameWeekStat>,
    val missed: List<MissedItem>
) {
    val isEmpty: Boolean get() = sessions == 0
}

data class WeeklyReportState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val weekOffset: Int = -1,          // 0 = this week, -1 = last week, …
    val report: WeeklyReport? = null
)

/**
 * "Rapport de la semaine": shown once automatically when a new week starts (for the
 * week just finished), and on demand from the Menu du jour card. Makes long-term
 * progress visible: menu grades per week, accuracy per game vs. the week before.
 */
@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    private val gameSessionDao: GameSessionDao,
    private val wordAttemptDao: WordAttemptDao,
    private val menuSessionDao: MenuSessionDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyReportState())
    val uiState: StateFlow<WeeklyReportState> = _uiState.asStateFlow()

    companion object {
        const val TREND_WEEKS = 8
    }

    /** On app start: show last week's report once, if there was any practice last week. */
    fun showIfNewWeek() {
        viewModelScope.launch {
            val lastWeek = weekStart(-1)
            if (settingsRepository.reportSeenWeek.first() == lastWeek.toString()) return@launch
            val hadPractice = gameSessionDao.getSessionsBetween(millis(lastWeek), millis(lastWeek.plusWeeks(1))).isNotEmpty()
            settingsRepository.markReportSeen(lastWeek.toString())
            if (hadPractice) open(-1)
        }
    }

    fun open(weekOffset: Int = -1) {
        _uiState.update { it.copy(isVisible = true, isLoading = true, weekOffset = weekOffset) }
        viewModelScope.launch {
            val report = buildReport(weekStart(weekOffset))
            _uiState.update { it.copy(isLoading = false, report = report) }
        }
    }

    fun previousWeek() = open(_uiState.value.weekOffset - 1)

    fun nextWeek() {
        if (_uiState.value.weekOffset < 0) open(_uiState.value.weekOffset + 1)
    }

    fun close() {
        _uiState.update { WeeklyReportState() }
    }

    // ── Report building ─────────────────────────────────────────────────

    private fun weekStart(offset: Int): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun toDate(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    private suspend fun averageGrade(start: LocalDate): Double? {
        val menus = menuSessionDao.getBetween(millis(start), millis(start.plusWeeks(1)))
        if (menus.isEmpty()) return null
        return roundHalf(menus.map { it.overallScore }.average() / 5.0)
    }

    private fun roundHalf(grade: Double): Double = (grade * 2).roundToInt() / 2.0

    private suspend fun buildReport(start: LocalDate): WeeklyReport {
        val from = millis(start)
        val to = millis(start.plusWeeks(1))
        val previousStart = start.minusWeeks(1)

        val sessions = gameSessionDao.getSessionsBetween(from, to)
        val days = sessions.map { toDate(it.timestamp) }.toSet()
        val menus = menuSessionDao.getBetween(from, to)

        val accuracy = wordAttemptDao.getAccuracyByGameType(from, to)
        val previousAccuracy = wordAttemptDao.getAccuracyByGameType(millis(previousStart), from)
            .associateBy { it.gameType }
        val games = accuracy
            .filter { it.total > 0 }
            .map { a ->
                val prev = previousAccuracy[a.gameType]?.takeIf { it.total > 0 }
                GameWeekStat(
                    gameName = GameType.fromKey(a.gameType)?.displayName ?: a.gameType,
                    answers = a.total,
                    accuracy = a.correct.toDouble() / a.total,
                    previousAccuracy = prev?.let { it.correct.toDouble() / it.total }
                )
            }
            .sortedByDescending { it.answers }

        val missed = wordAttemptDao.getMissedBetween(from, to, limit = 10).map { m ->
            val gameName = GameType.fromKey(m.gameType)?.displayName ?: m.gameType
            when (m.gameType) {
                // Question is the French word, answer the translation / article
                "matching", "gender_snap" -> MissedItem(m.question, m.correctAnswer, gameName, m.totalWrong)
                // Question is a German hint or a sentence; the answer is what to learn
                else -> MissedItem(m.correctAnswer, m.question, gameName, m.totalWrong)
            }
        }

        val trend = (TREND_WEEKS - 1 downTo 0).map { back ->
            val week = start.minusWeeks(back.toLong())
            week to averageGrade(week)
        }

        return WeeklyReport(
            weekStart = start,
            activeDays = (0..6).map { start.plusDays(it.toLong()) in days },
            sessions = sessions.size,
            playMinutes = (sessions.sumOf { it.durationMs } / 60_000).toInt(),
            menus = menus.size,
            averageGrade = averageGrade(start),
            bestGrade = menus.maxOfOrNull { roundHalf(it.overallScore / 5.0) },
            previousAverageGrade = averageGrade(previousStart),
            gradeTrend = trend,
            games = games,
            missed = missed
        )
    }
}
