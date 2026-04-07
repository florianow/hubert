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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.ui.theme.*
import com.hubert.viewmodel.GenderSnapState

@Composable
fun GenderSnapScreen(
    state: GenderSnapState,
    onAnswer: (Boolean) -> Unit  // true = masculine (le), false = feminine (la)
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
            GenderSnapTopBar(state)

            Spacer(modifier = Modifier.height(8.dp))

            // Timer bar
            TimerBar(
                fraction = state.timerFraction,
                timeMs = state.timeRemainingMs
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stats: correct / wrong
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

            // Current word display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val word = state.currentWord
                if (word != null) {
                    val feedbackColor = when (state.feedback) {
                        true -> CorrectGreen
                        false -> WrongRed
                        null -> MaterialTheme.colorScheme.surface
                    }
                    val borderColor = when (state.feedback) {
                        true -> CorrectGreen
                        false -> WrongRed
                        null -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(
                                width = if (state.feedback != null) 3.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(20.dp)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.feedback != null)
                                feedbackColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Show correct article on feedback
                            if (state.feedback != null) {
                                Text(
                                    text = if (word.gender == "m") "le" else "la",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (state.feedback == true) CorrectGreen
                                    else WrongRed
                                )
                            }
                            Text(
                                text = word.french,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = word.german,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Le / La buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GenderButton(
                    text = "le",
                    subtitle = "masculin",
                    color = FrenchBlue,
                    enabled = state.isPlaying && state.feedback == null,
                    onClick = { onAnswer(true) },
                    modifier = Modifier.weight(1f)
                )
                GenderButton(
                    text = "la",
                    subtitle = "féminin",
                    color = AccentPurple,
                    enabled = state.isPlaying && state.feedback == null,
                    onClick = { onAnswer(false) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Streak display
            Spacer(modifier = Modifier.height(12.dp))
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
private fun GenderSnapTopBar(state: GenderSnapState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        // Title
        Text(
            text = "le ou la ?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

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
            Spacer(modifier = Modifier.width(60.dp))
        }
    }
}

@Composable
private fun GenderButton(
    text: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = if (enabled) color else color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) color.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) color else color.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (enabled) color.copy(alpha = 0.7f) else color.copy(alpha = 0.3f)
            )
        }
    }
}
