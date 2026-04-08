package com.hubert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.ui.theme.*
import com.hubert.viewmodel.AnswerRecord

/**
 * @param title  "TIME'S UP!" for timer-based games, "GAME OVER" for points-based
 * @param totalCorrect  number of correct answers
 * @param totalWrong    number of wrong answers (0 for Word Match which uses time penalty)
 * @param bestStreak    best consecutive correct answers
 * @param highScore     all-time high score for this game type
 * @param durationMs    wall-clock game duration in milliseconds
 * @param answerHistory per-question answer log for detailed review
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameOverScreen(
    title: String = "TIME'S UP!",
    score: Int,
    isNewHighScore: Boolean,
    totalCorrect: Int,
    totalWrong: Int,
    bestStreak: Int,
    highScore: Int,
    durationMs: Long,
    answerHistory: List<AnswerRecord> = emptyList(),
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val totalAttempted = totalCorrect + totalWrong
    val accuracyPct = if (totalAttempted > 0) (totalCorrect * 100f / totalAttempted) else 0f

    // Track which detail view to show: null = none, true = correct, false = wrong
    var showDetailFilter by remember { mutableStateOf<Boolean?>(null) }

    if (showDetailFilter != null) {
        val isShowingCorrect = showDetailFilter!!
        val filtered = answerHistory.filter { it.isCorrect == isShowingCorrect }
        AnswerDetailScreen(
            title = if (isShowingCorrect) "Correct Answers" else "Wrong Answers",
            answers = filtered,
            accentColor = if (isShowingCorrect) CorrectGreen else WrongRed,
            onBack = { showDetailFilter = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = WrongRed,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isNewHighScore) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GermanGold.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "NEW HIGH SCORE!",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GermanGold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Score display
        Text(
            text = "$score",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = AccentPurple,
            fontSize = 72.sp
        )
        Text(
            text = "points",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Run Statistics card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = FrenchBlue.copy(alpha = 0.06f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Run Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FrenchBlue
                )

                // Accuracy bar (hide for games that don't track wrong answers)
                if (totalAttempted > 0 && totalWrong > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Accuracy",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "%.0f%%".format(accuracyPct),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    accuracyPct >= 80f -> CorrectGreen
                                    accuracyPct >= 50f -> GermanGold
                                    else -> WrongRed
                                }
                            )
                        }
                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (accuracyPct / 100f).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            accuracyPct >= 80f -> CorrectGreen
                                            accuracyPct >= 50f -> GermanGold
                                            else -> WrongRed
                                        }
                                    )
                            )
                        }
                    }
                }

                // Questions attempted
                RunStatRow(
                    label = "Questions attempted",
                    value = "$totalAttempted",
                    color = FrenchBlue
                )

                // Correct / Wrong chips — tappable to see details
                val hasHistory = answerHistory.isNotEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RunStatChip(
                        label = if (hasHistory && totalCorrect > 0) "Correct  >" else "Correct",
                        value = "$totalCorrect",
                        color = CorrectGreen,
                        onClick = if (hasHistory && totalCorrect > 0) {
                            { showDetailFilter = true }
                        } else null
                    )
                    if (totalWrong > 0) {
                        RunStatChip(
                            label = if (hasHistory) "Wrong  >" else "Wrong",
                            value = "$totalWrong",
                            color = WrongRed,
                            onClick = if (hasHistory) {
                                { showDetailFilter = false }
                            } else null
                        )
                    }
                }

                // Best Streak
                RunStatRow(
                    label = "Best Streak",
                    value = "$bestStreak",
                    color = AccentPurple
                )

                // Duration
                RunStatRow(
                    label = "Duration",
                    value = formatDuration(durationMs),
                    color = FrenchBlue
                )

                // High Score
                RunStatRow(
                    label = "High Score",
                    value = "$highScore",
                    color = GermanGold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Play again button
        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text(
                text = "PLAY AGAIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Menu button
        OutlinedButton(
            onClick = onBackToMenu,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "MENU",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Full-screen detail view showing individual answers (correct or wrong).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnswerDetailScreen(
    title: String,
    answers: List<AnswerRecord>,
    accentColor: Color,
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
                    text = "$title (${answers.size})",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(answers) { answer ->
                AnswerCard(answer = answer, accentColor = accentColor)
            }
        }
    }
}

@Composable
private fun AnswerCard(
    answer: AnswerRecord,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Icon(
                imageVector = if (answer.isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Question
                Text(
                    text = answer.question,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Your answer
                Row {
                    Text(
                        text = "You: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = answer.yourAnswer,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (answer.isCorrect) CorrectGreen else WrongRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Correct answer (only show if wrong)
                if (!answer.isCorrect) {
                    Row {
                        Text(
                            text = "Correct: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = answer.correctAnswer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = CorrectGreen,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStatRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun RunStatChip(
    label: String,
    value: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
