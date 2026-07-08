package com.hubert.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
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
import com.hubert.data.local.WordStreak
import com.hubert.ui.theme.*
import com.hubert.viewmodel.TrouvezState

private fun categoryEmoji(name: String): String = when (name) {
    "Familie" -> "👨‍👩‍👧"
    "Status und Beziehungen" -> "🤝"
    "Körper und Gesundheit" -> "💪"
    "Emotionen und Charakter" -> "😊"
    "Haus und Wohnung" -> "🏠"
    "Essen und Trinken" -> "🍽️"
    "Kleidung" -> "👗"
    "Wetter" -> "⛅"
    "Stadt und Reise" -> "🏙️"
    "Transport" -> "🚗"
    "Natur" -> "🌿"
    "Tiere" -> "🐾"
    "Gesellschaft" -> "👥"
    "Berufe" -> "💼"
    "Arbeit und Wirtschaft" -> "📊"
    "Bildung und Wissenschaft" -> "🎓"
    "Recht" -> "⚖️"
    "Nationalitäten" -> "🌍"
    "Sport" -> "⚽"
    "Kunst und Kultur" -> "🎭"
    "Technologie und Medien" -> "💻"
    "Farben" -> "🌈"
    "Materialien" -> "🪨"
    "Gegensätze" -> "↔️"
    "Zeit" -> "⏰"
    "Bewegungsverben" -> "🏃"
    "Kommunikationsverben" -> "💬"
    "Falsche Freunde" -> "⚠️"
    "Grundverben" -> "🔧"
    "Denken und Wissen" -> "🧠"
    "Kommunikationsverben" -> "💬"
    "Geld und Finanzen" -> "💶"
    "Politik und Staat" -> "🏛️"
    "Redewendungen und Ausdrücke" -> "🗣️"
    else -> "📚"
}

@Composable
fun TrouvezModeScreen(
    state: TrouvezState,
    onThemenTraining: () -> Unit,
    onFreierModus: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
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
                Text(
                    text = "Trouvez!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FrenchBlue
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            ModeCard(
                emoji = "📚",
                title = "Themen-Training",
                description = "Lerne Wörter zu einem bestimmten Thema — Essen, Sport, Familie und mehr.",
                accentColor = FrenchBlue,
                onClick = onThemenTraining
            )

            Spacer(modifier = Modifier.height(16.dp))

            ModeCard(
                emoji = "📌",
                title = "Freier Modus",
                description = if (state.pinnedRanks.isEmpty()) "Pinne Wörter die du üben willst und spiele mit deiner eigenen Liste."
                              else "${state.pinnedRanks.size} Wörter gepinnt · Spiele mit deiner eigenen Liste.",
                accentColor = GermanGold,
                highScore = if (state.highScore > 0) "${state.highScore}" else null,
                onClick = onFreierModus
            )
        }
    }
}

@Composable
private fun ModeCard(
    emoji: String,
    title: String,
    description: String,
    accentColor: Color,
    highScore: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 36.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (highScore != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🏆 $highScore",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun TrouvezCategoryScreen(
    state: TrouvezState,
    onSelectCategory: (String) -> Unit,
    onBack: () -> Unit
) {
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
                        text = "Themen-Training",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FrenchBlue
                    )
                    Text(
                        text = "Thema wählen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(state.availableCategories) { category ->
                    val catHs = state.categoryHighScores[category] ?: 0
                    val wordCount = state.categorySizes[category] ?: 0
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clickable { onSelectCategory(category) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = categoryEmoji(category), fontSize = 20.sp)
                                if (catHs > 0) {
                                    Text(
                                        text = "🏆 $catHs",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = FrenchBlue
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (wordCount > 0) {
                                    Text(
                                        text = "$wordCount Wörter",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordRow(
    french: String,
    german: String,
    isPinned: Boolean,
    accentColor: Color,
    accuracy: WordStreak? = null,
    isMastered: Boolean = false,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (accuracy != null && accuracy.streak > 0) {
                val flame = if (isMastered) "🧊" else "🔥"
                Text(
                    text = "$flame×${accuracy.streak}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMastered) FrenchBlue else GermanGold
                )
            } else if (isMastered) {
                Text(
                    text = "🧊",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FrenchBlue
                )
            }
            Icon(
                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = null,
                tint = if (isPinned) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.size(22.dp)
            )
        }
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
    var showMasteryInfo by remember { mutableStateOf(false) }

    if (showMasteryInfo) {
        AlertDialog(
            onDismissRequest = { showMasteryInfo = false },
            title = { Text("🧊 Eiswürfel-Status") },
            text = {
                Text(
                    "Ein Wort bekommt den 🧊 wenn du es 50 Mal korrekt zugeordnet hast.\n\n" +
                    "Das bedeutet: du kennst dieses Wort sehr gut. Du kannst es entpinnen — " +
                    "es bleibt trotzdem als gemeistert markiert.\n\n" +
                    "Der Zähler läuft weiter. Nur ein falsches Zuordnen setzt ihn zurück."
                )
            },
            confirmButton = {
                TextButton(onClick = { showMasteryInfo = false }) { Text("OK") }
            }
        )
    }

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
                Column(modifier = Modifier.weight(1f)) {
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
                IconButton(onClick = { showMasteryInfo = true }, modifier = Modifier.size(40.dp)) {
                    Text("🧊", fontSize = 20.sp)
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
                // When searching: results first (visible above keyboard), pinned below
                if (state.searchQuery.isNotBlank()) {
                    items(state.searchResults) { word ->
                        val isPinned = word.rank in state.pinnedRanks
                        WordRow(
                            french = word.french,
                            german = word.german,
                            isPinned = isPinned,
                            accentColor = accentColor,
                            accuracy = state.wordStreaks[word.french],
                            isMastered = word.french in state.masteredWords,
                            onClick = { onTogglePin(word.rank) }
                        )
                    }
                    if (state.pinnedWords.isNotEmpty()) {
                        val searchRanks = state.searchResults.map { it.rank }.toSet()
                        val remaining = state.pinnedWords.filter { it.rank !in searchRanks }
                        if (remaining.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Gepinnte Wörter",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accentColor.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                                )
                            }
                            items(remaining) { word ->
                                WordRow(
                                    french = word.french,
                                    german = word.german,
                                    isPinned = true,
                                    accentColor = accentColor,
                                    accuracy = state.wordStreaks[word.french],
                                    onClick = { onTogglePin(word.rank) }
                                )
                            }
                        }
                    }
                } else {
                    // No search: just show pinned words
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
                                accuracy = state.wordStreaks[word.french],
                                onClick = { onTogglePin(word.rank) }
                            )
                        }
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

            Spacer(modifier = Modifier.height(8.dp))

            // Active category badge
            if (state.selectedCategory != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = FrenchBlue.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${categoryEmoji(state.selectedCategory)} ${state.selectedCategory}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = FrenchBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
            ) {
                for (i in 0 until state.frenchWords.size) {
                    val frenchCorrect = state.frenchFeedback[i] == true
                    val frenchWrong = state.frenchFeedback[i] == false
                    val germanCorrect = state.germanFeedback[i] == true
                    val germanWrong = state.germanFeedback[i] == false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
