package com.hubert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.ui.theme.*
import com.hubert.viewmodel.GameWeekStat
import com.hubert.viewmodel.MissedItem
import com.hubert.viewmodel.WeeklyReport
import com.hubert.viewmodel.WeeklyReportState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

private val DAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.")

private fun formatGradeShort(grade: Double): String =
    if (grade % 1.0 == 0.0) grade.toInt().toString() else grade.toString().replace('.', ',')

@Composable
fun WeeklyReportScreen(
    state: WeeklyReportState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header: close + week navigation
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Schließen")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPreviousWeek) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Woche davor")
            }
            val start = state.report?.weekStart
            Text(
                text = start?.let { "KW ${it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${it.format(DATE_FORMAT)}–${it.plusDays(6).format(DATE_FORMAT)}" } ?: "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            IconButton(onClick = onNextWeek, enabled = state.weekOffset < 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Woche danach")
            }
        }

        val report = state.report
        when {
            state.isLoading || report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MenuWine)
            }
            report.isEmpty -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.weekOffset == 0) "Diese Woche noch nicht gespielt — à table !"
                           else "In dieser Woche wurde nicht gespielt.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            else -> ReportContent(report, isCurrentWeek = state.weekOffset == 0)
        }
    }
}

@Composable
private fun ReportContent(report: WeeklyReport, isCurrentWeek: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Rapport de la semaine",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = MenuWine
            )
            if (isCurrentWeek) {
                Text(
                    text = "Diese Woche bisher",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(16.dp))

            // Headline numbers
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Tage aktiv", "${report.activeDays.count { it }}/7", Modifier.weight(1f))
                StatTile("Minuten", "${report.playMinutes}", Modifier.weight(1f))
                StatTile("Menüs", "${report.menus}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            ActiveDaysRow(report.activeDays)
            Spacer(Modifier.height(20.dp))
        }

        // Menu grades
        item {
            SectionTitle("Menu du jour")
            if (report.averageGrade != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatGradeShort(report.averageGrade),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "/20 im Schnitt · ${mentionFor(report.averageGrade)}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                    )
                }
                val prev = report.previousAverageGrade
                Text(
                    text = buildString {
                        report.bestGrade?.let { append("Beste Note: ${formatGradeShort(it)}/20") }
                        if (prev != null) {
                            val delta = report.averageGrade - prev
                            append(" · ")
                            append(
                                when {
                                    delta > 0 -> "▲ +${formatGradeShort(delta)} zur Vorwoche"
                                    delta < 0 -> "▼ ${formatGradeShort(delta)} zur Vorwoche"
                                    else -> "gleich wie Vorwoche"
                                }
                            )
                        }
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            } else {
                Text(
                    text = "Diese Woche kein Menü gespielt.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(12.dp))
            GradeTrendChart(report.gradeTrend, highlight = report.weekStart)
            Spacer(Modifier.height(20.dp))
            SectionTitle("Trefferquote pro Spiel")
            Text(
                text = "verglichen mit der Vorwoche",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))
        }

        items(report.games) { GameRow(it) }

        if (report.missed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionTitle("Am häufigsten falsch")
                Spacer(Modifier.height(4.dp))
            }
            items(report.missed) { MissedRow(it) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MenuWine.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun ActiveDaysRow(activeDays: List<Boolean>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        activeDays.forEachIndexed { index, active ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (active) MenuWine else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (active) Text("✓", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.White)
                }
                Text(
                    text = DAY_LABELS[index],
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Average menu grade per week (single series, 0–20). The report week is drawn in full
 * colour, other weeks lighter; tapping a bar shows its value. Weeks without a menu get a
 * flat stub instead of a bar.
 */
@Composable
private fun GradeTrendChart(trend: List<Pair<LocalDate, Double?>>, highlight: LocalDate) {
    var selected by remember(trend) { mutableStateOf(highlight) }
    val chartHeight = 96.dp
    val ink = MaterialTheme.colorScheme.onBackground

    Column {
        Text(
            text = "Notenschnitt der letzten ${trend.size} Wochen",
            fontSize = 12.sp,
            color = ink.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(chartHeight + 36.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            trend.forEach { (week, grade) ->
                val isSelected = week == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selected = week },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (isSelected) {
                        Text(
                            text = grade?.let { formatGradeShort(it) } ?: "–",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink
                        )
                    }
                    val fraction = ((grade ?: 0.0) / 20.0).toFloat().coerceIn(0.03f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(chartHeight * fraction)
                            .background(
                                when {
                                    grade == null -> ink.copy(alpha = 0.10f)
                                    week == highlight -> MenuWine
                                    else -> MenuWine.copy(alpha = 0.40f)
                                },
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                    Text(
                        text = "${week.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}",
                        fontSize = 10.sp,
                        color = ink.copy(alpha = if (isSelected) 0.9f else 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameRow(stat: GameWeekStat) {
    val percent = (stat.accuracy * 100).roundToInt()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stat.gameName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "${stat.answers} Antworten",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        stat.previousAccuracy?.let { prev ->
            val delta = percent - (prev * 100).roundToInt()
            if (delta != 0) {
                Text(
                    text = if (delta > 0) "▲ +$delta" else "▼ $delta",
                    fontSize = 12.sp,
                    color = if (delta > 0) CorrectGreen else WrongRed,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Text(text = "$percent %", fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MissedRow(item: MissedItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.answer, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FrenchBlue)
            Text(
                text = "${item.context} · ${item.gameName}",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Text(
            text = "${item.wrong}×",
            fontSize = 13.sp,
            color = WrongRed
        )
    }
}
