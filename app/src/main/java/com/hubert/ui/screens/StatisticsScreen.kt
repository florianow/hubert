package com.hubert.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.data.local.DayCount
import com.hubert.data.local.StruggledWord
import com.hubert.data.model.GameSession
import com.hubert.data.repository.Achievement
import com.hubert.data.repository.GameStats
import com.hubert.data.repository.OverviewStats
import com.hubert.ui.theme.*
import com.hubert.viewmodel.GameType
import com.hubert.viewmodel.StatisticsState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    state: StatisticsState,
    onSelectGameType: (GameType) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Statistics",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Custom spinner — avoids Material3 CircularProgressIndicator which
                // crashes due to a keyframes API mismatch with BOM 2024.01.00.
                val spinTransition = rememberInfiniteTransition(label = "spin")
                val rotation by spinTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = LinearEasing)
                    ),
                    label = "rotation"
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Loading",
                    tint = AccentPurple,
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Overview dashboard
                item { OverviewSection(overview = state.overview) }

                // Activity heatmap
                item { HeatmapSection(heatmapData = state.heatmapData) }

                // Game type selector tabs
                item {
                    GameTypeSelector(
                        selected = state.selectedGameType,
                        onSelect = onSelectGameType
                    )
                }

                // Per-game stats
                item { GameStatsSection(stats = state.gameStats) }

                // Score trend chart
                if (state.scoreTrend.isNotEmpty()) {
                    item { ScoreTrendSection(sessions = state.scoreTrend) }
                }

                // Words I struggle with
                if (state.mostMissedWords.isNotEmpty() || state.recentlyStruggledWords.isNotEmpty()) {
                    item {
                        StruggledWordsSection(
                            mostMissed = state.mostMissedWords,
                            recentlyStruggled = state.recentlyStruggledWords
                        )
                    }
                }

                // Achievements
                item { AchievementsSection(achievements = state.achievements) }

                // Bottom spacer
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ─── Overview Dashboard ─────────────────────────────────────────────────────

@Composable
private fun OverviewSection(overview: OverviewStats) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentPurple.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AccentPurple
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Top row: streak + accuracy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBubble(
                    value = "${overview.currentStreak}",
                    label = "day streak",
                    icon = "\uD83D\uDD25",
                    color = GermanGold
                )
                StatBubble(
                    value = "${(overview.overallAccuracy * 100).toInt()}%",
                    label = "accuracy",
                    icon = "\uD83C\uDFAF",
                    color = CorrectGreen
                )
                StatBubble(
                    value = "${overview.totalSessions}",
                    label = "sessions",
                    icon = "\uD83C\uDFAE",
                    color = FrenchBlue
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: play time + correct + best streak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBubble(
                    value = formatDuration(overview.totalPlayTimeMs),
                    label = "play time",
                    icon = "\u23F1\uFE0F",
                    color = AccentPurple
                )
                StatBubble(
                    value = "${overview.totalCorrect}",
                    label = "correct",
                    icon = "\u2705",
                    color = CorrectGreen
                )
                StatBubble(
                    value = "${overview.bestStreakEver}",
                    label = "best streak",
                    icon = "\u26A1",
                    color = GermanGold
                )
            }

            // Favorite game
            if (overview.favoriteGame != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val gameName = GameType.fromKey(overview.favoriteGame)?.displayName
                    ?: overview.favoriteGame
                Text(
                    text = "Favorite game: $gameName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StatBubble(
    value: String,
    label: String,
    icon: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

// ─── Activity Heatmap ───────────────────────────────────────────────────────

@Composable
private fun HeatmapSection(heatmapData: List<DayCount>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Activity (last 90 days)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Build a map of day -> count
            val dayCountMap = heatmapData.associate { it.day to it.count }
            val today = LocalDate.now()
            // Generate last 90 days
            val days = (89 downTo 0).map { today.minusDays(it.toLong()) }

            // Organize into weeks (columns) with rows = day of week
            // Monday=0 .. Sunday=6
            val cellSize = 12.dp
            val cellSpacing = 2.dp

            // Day labels on the left
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Top
            ) {
                // Day-of-week labels
                Column(
                    modifier = Modifier.padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    val dayLabels = listOf("M", "", "W", "", "F", "", "S")
                    dayLabels.forEach { label ->
                        Box(
                            modifier = Modifier.size(cellSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                // Grid: one column per week
                val weeks = days.groupBy { day ->
                    // Week number relative to start
                    val daysSinceStart = ChronoUnit.DAYS.between(days.first(), day)
                    // Adjust to start weeks on Monday
                    val firstDayOffset = days.first().dayOfWeek.value - 1 // Monday=0
                    (daysSinceStart + firstDayOffset) / 7
                }.toSortedMap()

                weeks.forEach { (_, weekDays) ->
                    Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
                        // Fill in blanks for days before the first day in the week
                        val startDow = weekDays.first().dayOfWeek.value - 1 // Monday=0
                        repeat(startDow) {
                            Spacer(modifier = Modifier.size(cellSize))
                        }

                        weekDays.forEach { day ->
                            val dateStr = day.toString() // "2026-04-08"
                            val count = dayCountMap[dateStr] ?: 0
                            val bgColor = heatmapColor(count)

                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(bgColor)
                            )
                        }
                    }
                }
            }

            // Legend
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                listOf(0, 1, 3, 5, 8).forEach { level ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(heatmapColor(level))
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun heatmapColor(count: Int): Color {
    return when {
        count == 0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        count <= 1 -> CorrectGreen.copy(alpha = 0.25f)
        count <= 3 -> CorrectGreen.copy(alpha = 0.45f)
        count <= 5 -> CorrectGreen.copy(alpha = 0.65f)
        else -> CorrectGreen.copy(alpha = 0.9f)
    }
}

// ─── Game Type Selector ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTypeSelector(
    selected: GameType,
    onSelect: (GameType) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(GameType.entries.toList()) { gameType ->
            val isSelected = gameType == selected
            val color = gameTypeColor(gameType)

            FilterChip(
                selected = isSelected,
                onClick = { onSelect(gameType) },
                label = {
                    Text(
                        text = gameType.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.15f),
                    selectedLabelColor = color
                )
            )
        }
    }
}

private fun gameTypeColor(gameType: GameType): Color {
    return when (gameType) {
        GameType.MATCHING -> FrenchBlue
        GameType.GENDER_SNAP -> AccentPurple
        GameType.GAP_FILL -> CorrectGreen
        GameType.SPELLING_BEE -> GermanGold
        GameType.CONJUGATION -> FrenchBlue
        GameType.PRONUNCIATION -> WrongRed
        GameType.PREPOSITION -> FrenchBlue
    }
}

// ─── Per-Game Stats ─────────────────────────────────────────────────────────

@Composable
private fun GameStatsSection(stats: GameStats) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (stats.sessionsPlayed == 0) {
                Text(
                    text = "No games played yet for this mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                // Stats grid: 2 columns
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        StatRow(label = "Games played", value = "${stats.sessionsPlayed}")
                        StatRow(label = "High score", value = "${stats.highScore}")
                        StatRow(label = "Avg score", value = "${stats.averageScore.toInt()}")
                        StatRow(label = "Best streak", value = "x${stats.bestStreak}")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatRow(label = "Correct", value = "${stats.totalCorrect}")
                        StatRow(label = "Wrong", value = "${stats.totalWrong}")
                        StatRow(
                            label = "Accuracy",
                            value = "${(stats.accuracy * 100).toInt()}%"
                        )
                        StatRow(
                            label = "Avg time",
                            value = formatDuration(stats.averageDurationMs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Score Trend Chart ──────────────────────────────────────────────────────

@Composable
private fun ScoreTrendSection(sessions: List<GameSession>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Score Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            val scores = sessions.map { it.score.toFloat() }
            val maxScore = scores.maxOrNull() ?: 1f
            val minScore = scores.minOrNull() ?: 0f
            val range = (maxScore - minScore).coerceAtLeast(1f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val width = size.width
                val height = size.height
                val padding = 4f

                if (scores.size == 1) {
                    // Single point — draw a dot in the center
                    drawCircle(
                        color = FrenchBlue,
                        radius = 4f,
                        center = Offset(width / 2, height / 2)
                    )
                    return@Canvas
                }

                val stepX = (width - padding * 2) / (scores.size - 1).coerceAtLeast(1)

                // Build path
                val path = Path()
                scores.forEachIndexed { index, score ->
                    val x = padding + index * stepX
                    val y = height - padding - ((score - minScore) / range) * (height - padding * 2)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // Draw gradient fill
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(padding + (scores.size - 1) * stepX, height)
                    lineTo(padding, height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            FrenchBlue.copy(alpha = 0.2f),
                            FrenchBlue.copy(alpha = 0.02f)
                        )
                    )
                )

                // Draw line
                drawPath(
                    path = path,
                    color = FrenchBlue,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                )

                // Draw dots on last few points
                val dotsToShow = minOf(scores.size, 10)
                val startIdx = scores.size - dotsToShow
                for (i in startIdx until scores.size) {
                    val x = padding + i * stepX
                    val y = height - padding - ((scores[i] - minScore) / range) * (height - padding * 2)
                    drawCircle(
                        color = FrenchBlue,
                        radius = 3f,
                        center = Offset(x, y)
                    )
                }
            }

            // Min / max labels
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${scores.size} games",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = "High: ${maxScore.toInt()}  Avg: ${scores.average().toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ─── Words I Struggle With ──────────────────────────────────────────────────

@Composable
private fun StruggledWordsSection(
    mostMissed: List<StruggledWord>,
    recentlyStruggled: List<StruggledWord>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Words I Struggle With",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WrongRed
            )

            // Recently struggled (last 7 days)
            if (recentlyStruggled.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Last 7 days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                recentlyStruggled.take(8).forEach { word ->
                    StruggledWordRow(word)
                }
            }

            // All-time most missed
            if (mostMissed.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "All time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                mostMissed.take(10).forEach { word ->
                    StruggledWordRow(word)
                }
            }
        }
    }
}

@Composable
private fun StruggledWordRow(word: StruggledWord) {
    val accuracyPercent = ((1.0 - word.errorRate) * 100).toInt()
    val accuracyColor = when {
        accuracyPercent >= 70 -> GermanGold
        accuracyPercent >= 40 -> TimerWarning
        else -> WrongRed
    }
    val gameLabel = GameType.fromKey(word.gameType)?.displayName ?: word.gameType

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = word.question,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$gameLabel  \u2022  ${word.correctAnswer}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Accuracy + attempt count
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$accuracyPercent%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = accuracyColor
            )
            Text(
                text = "${word.totalWrong}/${word.totalAttempts} wrong",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

// ─── Achievements ───────────────────────────────────────────────────────────

@Composable
private fun AchievementsSection(achievements: List<Achievement>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val unlockedCount = achievements.count { it.isUnlocked }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$unlockedCount/${achievements.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GermanGold,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Achievement grid: 2 columns
            achievements.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { achievement ->
                        AchievementCard(
                            achievement = achievement,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill empty space if odd number
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    modifier: Modifier = Modifier
) {
    val alpha = if (achievement.isUnlocked) 1f else 0.4f
    val bgAlpha = if (achievement.isUnlocked) 0.1f else 0.03f
    val borderColor = if (achievement.isUnlocked) GermanGold.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GermanGold.copy(alpha = bgAlpha),
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = achievement.icon,
                fontSize = 24.sp,
                modifier = Modifier.alpha(alpha)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = achievement.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp
            )
            if (achievement.progress != null && !achievement.isUnlocked) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = achievement.progress,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GermanGold.copy(alpha = 0.8f)
                )
            }
            if (achievement.isUnlocked) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "\u2713",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CorrectGreen
                )
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}


