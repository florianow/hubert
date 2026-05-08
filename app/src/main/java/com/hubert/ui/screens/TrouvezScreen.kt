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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import com.hubert.ui.theme.*
import com.hubert.viewmodel.TrouvezState

@Composable
private fun WordRow(
    french: String,
    german: String,
    isPinned: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = french,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = german,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Icon(
            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = null,
            tint = if (isPinned) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            modifier = Modifier.size(22.dp)
        )
    }
    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
fun TrouvezPinScreen(
    state: TrouvezState,
    onSearch: (String) -> Unit,
    onTogglePin: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val accentColor = FrenchBlue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Trouvez!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = if (state.pinnedRanks.isEmpty()) "Wörter pinnen zum Üben"
                               else "${state.pinnedRanks.size} Wörter gepinnt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                placeholder = { Text("Suchen (fr / de)…") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Default.Close, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                // Pinned words section (always visible)
                if (state.pinnedWords.isNotEmpty()) {
                    item {
                        Text(
                            text = "Gepinnte Wörter",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                    }
                    items(state.pinnedWords) { word ->
                        WordRow(
                            french = word.french,
                            german = word.german,
                            isPinned = true,
                            accentColor = accentColor,
                            onClick = { onTogglePin(word.rank) }
                        )
                    }
                }

                // Search results
                if (state.searchQuery.isNotBlank()) {
                    item {
                        Text(
                            text = "Suchergebnisse",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                    }
                    items(state.searchResults.filter { it.rank !in state.pinnedRanks }) { word ->
                        WordRow(
                            french = word.french,
                            german = word.german,
                            isPinned = false,
                            accentColor = accentColor,
                            onClick = { onTogglePin(word.rank) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(
                    text = if (state.pinnedRanks.isEmpty()) "START" else "START · ${state.pinnedRanks.size} gepinnt",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun TrouvezScreen(
    state: TrouvezState,
    onSelectFrench: (Int) -> Unit,
    onSelectGerman: (Int) -> Unit,
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
            // Top bar: Score + Streak + Matches
            GameTopBar(state, onQuit = onQuit)

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
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            subtitle = state.frenchWords[i].ipa?.let { "/$it/" },
                            isSelected = state.selectedFrench == i,
                            isCorrectFlash = frenchCorrect,
                            isWrongFlash = frenchWrong,
                            isMatched = state.frenchWords[i].matched,
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
                            isMatched = state.germanWords[i].matched,
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

        // Penalty overlay: "-2s" flash
        if (state.showPenalty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-2s",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = WrongRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun GameTopBar(state: TrouvezState, onQuit: () -> Unit) {
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
    subtitle: String? = null,
    isSelected: Boolean,
    isCorrectFlash: Boolean,
    isWrongFlash: Boolean,
    isMatched: Boolean = false,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Matched cards fade out completely (slot stays as invisible placeholder)
    val alpha by animateFloatAsState(
        targetValue = if (isMatched) 0f else 1f,
        animationSpec = tween(durationMillis = 800),
        label = "matchedAlpha"
    )

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
        isMatched -> CorrectGreen.copy(alpha = 0.15f)
        isCorrectFlash -> CorrectGreen.copy(alpha = 0.3f)
        isWrongFlash -> WrongRed.copy(alpha = 0.3f)
        isSelected -> accentColor.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isMatched -> CorrectGreen.copy(alpha = 0.3f)
        isCorrectFlash -> CorrectGreen
        isWrongFlash -> WrongRed
        isSelected -> accentColor
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected || isCorrectFlash || isWrongFlash) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .then(if (!isMatched) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
