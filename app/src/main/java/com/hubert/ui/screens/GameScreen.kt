package com.hubert.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.ui.theme.*
import com.hubert.viewmodel.GameUiState

@Composable
fun GameScreen(
    state: GameUiState,
    onSelectFrench: (Int) -> Unit,
    onSelectGerman: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top bar: Score + Streak + Matches
            GameTopBar(state)

            Spacer(modifier = Modifier.height(8.dp))

            // Timer bar
            TimerBar(
                fraction = state.timerFraction,
                timeMs = state.timeRemainingMs
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Francais",
                    style = MaterialTheme.typography.titleMedium,
                    color = FrenchBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Deutsch",
                    style = MaterialTheme.typography.titleMedium,
                    color = GermanGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Word matching grid: French left, German right
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in 0 until state.frenchWords.size) {
                    val frenchCorrect = state.frenchFeedback[i] == true
                    val frenchWrong = state.frenchFeedback[i] == false
                    val germanCorrect = state.germanFeedback[i] == true
                    val germanWrong = state.germanFeedback[i] == false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // French word card
                        WordCard(
                            text = state.frenchWords[i].text,
                            isSelected = state.selectedFrench == i,
                            isCorrectFlash = frenchCorrect,
                            isWrongFlash = frenchWrong,
                            accentColor = FrenchBlue,
                            onClick = { onSelectFrench(i) },
                            modifier = Modifier.weight(1f)
                        )

                        // German word card
                        WordCard(
                            text = state.germanWords[i].text,
                            isSelected = state.selectedGerman == i,
                            isCorrectFlash = germanCorrect,
                            isWrongFlash = germanWrong,
                            accentColor = GermanGold,
                            onClick = { onSelectGerman(i) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Streak display
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.streak >= 2) {
                    Text(
                        text = "Streak x${state.streak}",
                        color = GermanGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Penalty overlay: "-5s" flash
        if (state.showPenalty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-5s",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = WrongRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun GameTopBar(state: GameUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Score
        Column {
            Text(
                text = "Score",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "${state.score}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AccentPurple
            )
        }

        // Streak badge
        if (state.streak >= 2) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GermanGold.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "x${state.streak}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GermanGold
                )
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        // Matches
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Matches",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "${state.totalMatches}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = CorrectGreen
            )
        }
    }
}

@Composable
fun TimerBar(fraction: Float, timeMs: Long) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(50),
        label = "timer"
    )

    val barColor = when {
        fraction < 0.2f -> WrongRed
        fraction < 0.4f -> TimerWarning
        else -> CorrectGreen
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "${timeMs / 1000}.${(timeMs % 1000) / 100}s",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = animatedFraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun WordCard(
    text: String,
    isSelected: Boolean,
    isCorrectFlash: Boolean,
    isWrongFlash: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isCorrectFlash -> 0.95f
            isSelected -> 1.05f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f),
        label = "scale"
    )

    val backgroundColor = when {
        isCorrectFlash -> CorrectGreen.copy(alpha = 0.3f)
        isWrongFlash -> WrongRed.copy(alpha = 0.3f)
        isSelected -> accentColor.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isCorrectFlash -> CorrectGreen
        isWrongFlash -> WrongRed
        isSelected -> accentColor
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected || isCorrectFlash || isWrongFlash) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
