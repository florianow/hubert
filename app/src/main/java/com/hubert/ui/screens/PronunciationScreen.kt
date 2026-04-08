package com.hubert.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.hubert.ui.theme.*
import com.hubert.viewmodel.PronunciationState
import com.hubert.viewmodel.RunStats
import com.hubert.viewmodel.WordScore

// ─── Settings Dialog ─────────────────────────────────────────────────────────

@Composable
fun AzureSettingsDialog(
    currentKey: String,
    currentRegion: String,
    onSave: (key: String, region: String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(currentKey) }
    var region by remember { mutableStateOf(currentRegion) }
    var keyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Azure Speech Settings",
                fontWeight = FontWeight.Bold,
                color = AccentPurple
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Prononcez! uses Azure Speech Services to assess your pronunciation. " +
                        "Enter your API key and region below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("Region") },
                    placeholder = { Text("e.g. westeurope") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(key.trim(), region.trim()) },
                enabled = key.isNotBlank() && region.isNotBlank()
            ) {
                Text("SAVE", color = AccentPurple, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

// ─── Main Game Screen ────────────────────────────────────────────────────────

@Composable
fun PronunciationScreen(
    state: PronunciationState,
    onToggleRecording: () -> Unit,
    onNext: () -> Unit,
    onSpeak: (String) -> Unit,
    onQuit: () -> Unit
) {
    val context = LocalContext.current
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (granted) {
            onToggleRecording()
        }
    }

    // Wrapper that checks permission before toggling recording
    val onMicClick: () -> Unit = {
        if (hasRecordPermission) {
            onToggleRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
            // Top bar: quit, score, title, streak
            PronunciationTopBar(state = state, onQuit = onQuit)

            Spacer(modifier = Modifier.height(8.dp))

            // Points bar
            PronunciationPointsBar(points = state.points)

            Spacer(modifier = Modifier.height(8.dp))

            // Correct / Wrong tally
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

            Spacer(modifier = Modifier.height(12.dp))

            // Difficulty badge
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val badgeColor = when (state.difficultyLabel) {
                    "Facile" -> CorrectGreen
                    "Moyen" -> GermanGold
                    "Difficile" -> WrongRed
                    else -> AccentPurple
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = state.difficultyLabel,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sentence card (French + German + speaker icon)
            SentenceCard(
                sentenceFr = state.sentenceFr,
                sentenceDe = state.sentenceDe,
                highlightWord = state.highlightWord,
                onSpeak = { onSpeak(state.sentenceFr) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Results area (per-word breakdown + PronScore) — only after assessment
            if (state.pronScore != null) {
                ResultsOverlay(
                    pronScore = state.pronScore,
                    wordScores = state.wordScores,
                    isCorrect = state.feedback == true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Error message
            if (state.errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = WrongRed.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = state.errorMessage,
                        modifier = Modifier.padding(12.dp),
                        color = WrongRed,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Mic button or Next button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (state.awaitingNext) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.feedback == true) CorrectGreen else AccentPurple
                        )
                    ) {
                        Text(
                            text = "NEXT",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                } else {
                    MicButton(
                        isRecording = state.isRecording,
                        isProcessing = state.isProcessing,
                        enabled = state.isPlaying && state.feedback == null && !state.isProcessing,
                        onClick = onMicClick
                    )
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

        // Feedback flash overlay
        if (state.feedback != null) {
            val flashColor = if (state.feedback) CorrectGreen else WrongRed
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(flashColor.copy(alpha = 0.08f))
            )
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun PronunciationTopBar(state: PronunciationState, onQuit: () -> Unit) {
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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            text = "Prononcez!",
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

// ─── Points Bar (same pattern as Conjuguez!) ─────────────────────────────────

@Composable
private fun PronunciationPointsBar(points: Int) {
    val maxPoints = PronunciationState.STARTING_POINTS
    val barColor by animateColorAsState(
        targetValue = when {
            points <= 3 -> WrongRed
            points <= 6 -> GermanGold
            else -> CorrectGreen
        },
        label = "points_color"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayPoints = points.coerceAtMost(20)
            for (i in 0 until displayPoints) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(if (points <= maxPoints) 12.dp else 10.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
            if (points > 20) {
                Text(
                    text = "+${points - 20}",
                    color = barColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

// ─── Sentence Card ───────────────────────────────────────────────────────────

@Composable
private fun SentenceCard(
    sentenceFr: String,
    sentenceDe: String,
    highlightWord: String,
    onSpeak: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // French sentence with highlighted vocab word + speaker
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // French sentence with the key vocab word highlighted bold
                val annotated = buildAnnotatedString {
                    if (highlightWord.isNotBlank()) {
                        // Case-insensitive highlight of the vocab word in the sentence
                        val lowerSentence = sentenceFr.lowercase()
                        val lowerWord = highlightWord.lowercase()
                        var searchStart = 0
                        var found = false

                        while (searchStart < lowerSentence.length) {
                            val idx = lowerSentence.indexOf(lowerWord, searchStart)
                            if (idx == -1) {
                                append(sentenceFr.substring(searchStart))
                                break
                            }
                            // Append text before the match
                            append(sentenceFr.substring(searchStart, idx))
                            // Append the highlighted match
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Black,
                                    color = AccentPurple
                                )
                            ) {
                                append(sentenceFr.substring(idx, idx + highlightWord.length))
                            }
                            searchStart = idx + highlightWord.length
                            found = true
                        }

                        if (!found && length == 0) {
                            append(sentenceFr)
                        }
                    } else {
                        append(sentenceFr)
                    }
                }

                Text(
                    text = annotated,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Speaker icon — tap to hear TTS
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Listen to sentence",
                    tint = FrenchBlue.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onSpeak() }
                )
            }
        }

        // German translation
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = GermanGold.copy(alpha = 0.08f)
            )
        ) {
            Text(
                text = sentenceDe,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

// ─── Mic Button ──────────────────────────────────────────────────────────────

@Composable
private fun MicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // Pulsing animation when recording
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val buttonScale = if (isRecording) pulseScale else 1f

    val buttonColor = when {
        isProcessing -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isRecording -> WrongRed
        enabled -> FrenchBlue
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    }

    val borderColor = when {
        isRecording -> WrongRed.copy(alpha = 0.5f)
        enabled -> FrenchBlue.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .scale(buttonScale)
                .size(80.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .border(3.dp, borderColor, CircleShape)
                .clickable(enabled = enabled && !isProcessing) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                // Custom spinner — avoids Material3 CircularProgressIndicator which
                // crashes on some devices due to a keyframes API mismatch (NoSuchMethodError).
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
                    contentDescription = "Processing",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            } else {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop recording" else "Start recording",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                isProcessing -> "Analyzing..."
                isRecording -> "Tap to stop"
                else -> "Tap to speak"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

// ─── Results Overlay ─────────────────────────────────────────────────────────

@Composable
private fun ResultsOverlay(
    pronScore: Double,
    wordScores: List<WordScore>,
    isCorrect: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) CorrectGreen.copy(alpha = 0.08f)
            else WrongRed.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Big PronScore number
            Text(
                text = "${pronScore.toInt()}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = if (isCorrect) CorrectGreen else WrongRed
            )
            Text(
                text = "PronScore",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Per-word breakdown — words colored by accuracy
            if (wordScores.isNotEmpty()) {
                // Use a FlowRow-like layout by wrapping text
                val annotated = buildAnnotatedString {
                    wordScores.forEachIndexed { index, ws ->
                        val wordColor = when {
                            ws.accuracyScore >= 80 -> CorrectGreen
                            ws.accuracyScore >= 60 -> GermanGold
                            else -> WrongRed
                        }
                        withStyle(
                            SpanStyle(
                                color = wordColor,
                                fontWeight = if (ws.errorType != "None")
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        ) {
                            append(ws.word)
                        }
                        if (index < wordScores.size - 1) {
                            append(" ")
                        }
                    }
                }
                Text(
                    text = annotated,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

// ─── Pronunciation Game Over Screen ──────────────────────────────────────────

@Composable
fun PronunciationGameOverScreen(
    score: Int,
    isNewHighScore: Boolean,
    runStats: RunStats,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Game Over title
        Text(
            text = "GAME OVER",
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

        // Run statistics card
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

                // Sentences attempted
                RunStatRow(
                    label = "Sentences attempted",
                    value = "${runStats.sentencesAttempted}",
                    color = FrenchBlue
                )

                // Correct / Wrong
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RunStatChip(
                        label = "Correct",
                        value = "${runStats.sentencesCorrect}",
                        color = CorrectGreen
                    )
                    RunStatChip(
                        label = "Wrong",
                        value = "${runStats.sentencesWrong}",
                        color = WrongRed
                    )
                }

                // Avg PronScore
                RunStatRow(
                    label = "Avg PronScore",
                    value = "%.1f".format(runStats.avgPronScore),
                    color = AccentPurple
                )

                // Best / Worst PronScore
                if (runStats.sentencesAttempted > 0) {
                    RunStatRow(
                        label = "Best PronScore",
                        value = "%.1f".format(runStats.bestPronScore),
                        color = CorrectGreen
                    )
                    if (runStats.bestSentence.isNotBlank()) {
                        Text(
                            text = runStats.bestSentence,
                            style = MaterialTheme.typography.bodySmall,
                            color = CorrectGreen.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    RunStatRow(
                        label = "Worst PronScore",
                        value = "%.1f".format(runStats.worstPronScore),
                        color = WrongRed
                    )
                    if (runStats.worstSentence.isNotBlank()) {
                        Text(
                            text = runStats.worstSentence,
                            style = MaterialTheme.typography.bodySmall,
                            color = WrongRed.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Most mispronounced words (top 5)
                val topErrors = runStats.wordErrors.entries
                    .sortedByDescending { it.value }
                    .take(5)
                if (topErrors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Most mispronounced",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GermanGold
                    )
                    topErrors.forEach { (word, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = word,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${count}x",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = WrongRed
                            )
                        }
                    }
                }
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

// ─── Helper composables ──────────────────────────────────────────────────────

@Composable
private fun RunStatRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun RunStatChip(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
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
