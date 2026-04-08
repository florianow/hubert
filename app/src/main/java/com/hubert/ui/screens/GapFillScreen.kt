package com.hubert.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.hubert.ui.theme.*
import com.hubert.viewmodel.GapFillState

@Composable
fun GapFillScreen(
    state: GapFillState,
    onAnswer: (Int) -> Unit,
    onQuit: () -> Unit
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
            // Top bar: Score + Streak
            GapFillTopBar(state, onQuit = onQuit)

            Spacer(modifier = Modifier.height(8.dp))

            // Timer bar
            TimerBar(
                fraction = state.timerFraction,
                timeMs = state.timeRemainingMs
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.totalCorrect}",
                    color = CorrectGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = " / ",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 16.sp
                )
                Text(
                    text = "${state.totalWrong}",
                    color = WrongRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // French sentence with gap
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = FrenchBlue.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Francais",
                        style = MaterialTheme.typography.labelSmall,
                        color = FrenchBlue.copy(alpha = 0.6f)
                    )
                    if (state.targetWordIpa != null) {
                        Text(
                            text = "/${state.targetWordIpa}/",
                            style = MaterialTheme.typography.bodySmall,
                            color = FrenchBlue.copy(alpha = 0.4f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Sentence with highlighted gap
                    val annotated = buildAnnotatedString {
                        val parts = state.sentenceWithGap.split("___")
                        parts.forEachIndexed { index, part ->
                            append(part)
                            if (index < parts.size - 1) {
                                withStyle(SpanStyle(
                                    color = AccentPurple,
                                    fontWeight = FontWeight.Bold,
                                    background = AccentPurple.copy(alpha = 0.1f)
                                )) {
                                    append(" _____ ")
                                }
                            }
                        }
                    }
                    Text(
                        text = annotated,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // German translation hint
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = GermanGold.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Deutsch",
                        style = MaterialTheme.typography.labelSmall,
                        color = GermanGold.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.germanTranslation,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4 answer choices in a 2x2 grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 0..1) {
                            val idx = row * 2 + col
                            if (idx < state.choices.size) {
                                ChoiceCard(
                                    text = state.choices[idx],
                                    index = idx,
                                    isCorrectAnswer = idx == state.correctIndex,
                                    isSelected = idx == state.selectedIndex,
                                    feedback = state.feedback,
                                    enabled = state.isPlaying && state.feedback == null,
                                    onClick = { onAnswer(idx) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
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
    }
}

@Composable
private fun GapFillTopBar(state: GapFillState, onQuit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quit button
        IconButton(
            onClick = onQuit,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Quit game",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

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

        Text(
            text = "Gap Fill",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

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
            Spacer(modifier = Modifier.width(60.dp))
        }
    }
}

@Composable
private fun ChoiceCard(
    text: String,
    index: Int,
    isCorrectAnswer: Boolean,
    isSelected: Boolean,
    feedback: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine colors based on feedback state
    val backgroundColor: Color
    val borderColor: Color
    val textColor: Color

    if (feedback != null) {
        // Feedback shown
        when {
            isCorrectAnswer -> {
                // Always highlight correct answer
                backgroundColor = CorrectGreen.copy(alpha = 0.2f)
                borderColor = CorrectGreen
                textColor = CorrectGreen
            }
            isSelected -> {
                // Wrong selection
                backgroundColor = WrongRed.copy(alpha = 0.2f)
                borderColor = WrongRed
                textColor = WrongRed
            }
            else -> {
                backgroundColor = MaterialTheme.colorScheme.surface
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            }
        }
    } else {
        backgroundColor = MaterialTheme.colorScheme.surface
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        textColor = MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected || (feedback != null && isCorrectAnswer)) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = if (isSelected || (feedback != null && isCorrectAnswer))
                    FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
