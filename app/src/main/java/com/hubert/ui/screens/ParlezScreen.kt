package com.hubert.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hubert.data.model.ParlezTopic
import com.hubert.data.model.VocabHint
import com.hubert.ui.theme.*
import com.hubert.viewmodel.*
import kotlin.math.roundToInt

// ── Topic selection ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParlezTopicSelectionScreen(
    state: ParlezState,
    onSelectNiveau: (String) -> Unit,
    onSelectTopic: (ParlezTopic) -> Unit,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit
) {
    val niveaux = listOf("A1", "A2", "B1", "B2")
    val filtered = if (state.availableTopics.isEmpty()) emptyList()
                   else state.availableTopics.filter { it.niveau == state.selectedNiveau }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                text = "Parlez!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = ParlezTeal,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = ParlezTeal.copy(alpha = 0.7f))
            }
        }

        Text(
            text = "Wähle dein Niveau:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Niveau chips
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            niveaux.forEach { n ->
                val selected = n == state.selectedNiveau
                FilterChip(
                    selected = selected,
                    onClick = { onSelectNiveau(n) },
                    label = { Text(n, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ParlezTeal,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Wähle ein Thema:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered) { topic ->
                val isSelected = topic.id == state.selectedTopic?.id
                val topicBest = state.topicHighScores[topic.id]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) ParlezTeal else ParlezTeal.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectTopic(topic) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) ParlezTeal.copy(alpha = 0.12f)
                                         else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topic.themeFr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ParlezTeal
                            )
                            Text(
                                text = topic.themeDe,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = topic.descriptionDe,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                            if (topic.scenarioDe.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = topic.scenarioDe,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ParlezTeal.copy(alpha = 0.7f)
                                )
                            }
                            if (topicBest != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val scoreColor = when {
                                    topicBest >= 80 -> CorrectGreen
                                    topicBest >= 50 -> GermanGold
                                    else            -> WrongRed
                                }
                                Text(
                                    text = "Bester: $topicBest / 100",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ParlezTeal.copy(alpha = if (isSelected) 0.25f else 0.1f)
                        ) {
                            Text(
                                text = topic.niveau,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ParlezTeal
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Start button
        Button(
            onClick = onStart,
            enabled = state.selectedTopic != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ParlezTeal)
        ) {
            Text(
                text = if (state.selectedTopic != null)
                           "Gespräch starten — ${state.selectedTopic.themeFr}"
                       else "Thema auswählen",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

// ── Conversation screen ────────────────────────────────────────────────────────

@Composable
fun ParlezConversationScreen(
    state: ParlezState,
    onToggleRecording: () -> Unit,
    onToggleHints: () -> Unit,
    onRequestContextHints: () -> Unit,
    onSpeakHint: (String) -> Unit,
    onFinish: () -> Unit,
    onQuit: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // ── Microphone permission ──────────────────────────────────────────────────
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
        if (granted) onToggleRecording()
    }
    val onMicClick: () -> Unit = {
        if (hasRecordPermission) onToggleRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Mic button pulse animation when recording
    val scale by animateFloatAsState(
        targetValue = if (state.isRecording) 1.15f else 1f,
        animationSpec = if (state.isRecording)
            infiniteRepeatable(tween(600), RepeatMode.Reverse)
        else spring(),
        label = "mic_scale"
    )

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onQuit) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Beenden",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Text(
                text = state.selectedTopic?.themeFr ?: "Parlez!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ParlezTeal,
                modifier = Modifier.weight(1f)
            )
        }

        // Timer bar — same as other game modes
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            TimerBar(fraction = state.timerFraction, timeMs = state.timeRemainingMs)
        }

        // Scenario hint
        if (state.selectedTopic?.scenarioDe?.isNotBlank() == true) {
            Text(
                text = state.selectedTopic.scenarioDe,
                style = MaterialTheme.typography.labelSmall,
                color = ParlezTeal.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Divider()

        // Chat messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages) { msg ->
                ChatBubble(msg)
            }
            if (state.isProcessing) {
                item { TypingIndicator() }
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }
        }

        // Error message
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = WrongRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        Divider()

        // ── Hint panel ────────────────────────────────────────────────────────
        HintPanel(state = state, onToggleHints = onToggleHints, onRequestContextHints = onRequestContextHints, onSpeakHint = onSpeakHint)

        Divider()

        // Mic button panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(
                            when {
                                state.isRecording  -> WrongRed
                                state.isProcessing -> ParlezTeal.copy(alpha = 0.4f)
                                else               -> ParlezTeal
                            }
                        )
                        .clickable(enabled = !state.isProcessing) { onMicClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mikrofon",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        state.isRecording  -> "Loslassen zum Senden"
                        state.isProcessing -> "Hubert denkt..."
                        else               -> "Halten zum Sprechen"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // Fixed-height slot so layout never shifts when button appears
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            if (state.timerExpired) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ParlezTeal)
                ) { Text("Gespräch beenden", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun HintPanel(
    state: ParlezState,
    onToggleHints: () -> Unit,
    onRequestContextHints: () -> Unit,
    onSpeakHint: (String) -> Unit
) {
    val topic = state.selectedTopic
    val vocabHints = topic?.vocabHints ?: emptyList()
    var expandedHints by remember { mutableStateOf(setOf<Int>()) }
    val canRequestHints = state.hintsUsed == 0 && !state.isLoadingHints

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row — toggle button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleHints() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tipps",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ParlezTeal
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "?" button for dynamic context hints (once per conversation)
                if (!state.timerExpired) {
                    Surface(
                        shape = CircleShape,
                        color = if (canRequestHints) ParlezTeal else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = canRequestHints) { onRequestContextHints() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.isLoadingHints) {
                                val pulse by rememberInfiniteTransition(label = "hint_pulse")
                                    .animateFloat(
                                        initialValue = 0.4f, targetValue = 1f,
                                        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                                        label = "pulse"
                                    )
                                Text("?", color = Color.White.copy(alpha = pulse),
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            } else {
                                Text(
                                    text = if (state.hintsUsed > 0) "✓" else "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (state.showHints) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = ParlezTeal.copy(alpha = 0.7f)
                )
            }
        }

        // Expanded content
        if (state.showHints) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // Dynamic context hints (from Gemini)
                if (state.contextHints.isNotEmpty()) {
                    Text(
                        text = "Was du sagen könntest:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    state.contextHints.forEach { hint ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ParlezTeal.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "« $hint »",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = ParlezTeal,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (canRequestHints && !state.isLoadingHints) {
                    Text(
                        text = "Tippe auf ? für Satzvorschläge passend zur aktuellen Frage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }

                // Vocab hints from topic (always shown, tap to reveal German)
                if (vocabHints.isNotEmpty()) {
                    Text(
                        text = "Vokabeln zum Thema:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        vocabHints.forEachIndexed { index, hint ->
                            val isExpanded = index in expandedHints
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isExpanded) ParlezTeal.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isExpanded) ParlezTeal.copy(alpha = 0.5f)
                                    else ParlezTeal.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.clickable {
                                    onSpeakHint(hint.fr)
                                    expandedHints = if (isExpanded) expandedHints - index
                                                    else expandedHints + index
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = hint.fr,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isExpanded) ParlezTeal
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isExpanded) {
                                        Text(
                                            text = hint.de,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ParlezTeal.copy(alpha = 0.7f)
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
}

@Composable
private fun ChatBubble(msg: ParlezChatMessage) {
    val isHubert = msg.isHubert
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isHubert) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isHubert) 4.dp else 16.dp,
                bottomEnd = if (isHubert) 16.dp else 4.dp
            ),
            color = if (isHubert) ParlezTeal.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "typing_alpha"
    )
    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Surface(
                shape = CircleShape,
                color = ParlezTeal.copy(alpha = alpha),
                modifier = Modifier.size(8.dp)
            ) {}
        }
    }
}

// ── Evaluating (loading) screen ────────────────────────────────────────────────

@Composable
fun ParlezEvaluatingScreen() {
    // Custom pulsing dots — avoids indeterminate ProgressIndicator which crashes on BOM 2024.01.00
    val infiniteTransition = rememberInfiniteTransition(label = "eval_loading")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "eval_pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { i ->
                    val dotAlpha by rememberInfiniteTransition(label = "dot$i").animateFloat(
                        initialValue = 0.2f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(durationMillis = 600, delayMillis = i * 150),
                            RepeatMode.Reverse
                        ),
                        label = "dot_alpha_$i"
                    )
                    Surface(
                        shape = CircleShape,
                        color = ParlezTeal.copy(alpha = dotAlpha),
                        modifier = Modifier.size(14.dp)
                    ) {}
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Hubert bewertet dein Gespräch...",
                style = MaterialTheme.typography.titleMedium,
                color = ParlezTeal.copy(alpha = pulse),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Das kann einen Moment dauern.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

// ── Result screen ──────────────────────────────────────────────────────────────

@Composable
fun ParlezResultScreen(
    state: ParlezState,
    onPlayAgain: () -> Unit,
    onSelectOtherSituation: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val evaluation = state.evaluation
    val scoreColor = when {
        state.score >= 80 -> CorrectGreen
        state.score >= 50 -> GermanGold
        else              -> WrongRed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "PARLEZ! — Résultat",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = ParlezTeal,
            letterSpacing = 2.sp
        )
        if (state.selectedTopic != null) {
            Text(
                text = state.selectedTopic.themeFr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big score
        if (state.isNewHighScore) {
            Text("NEUER REKORD!", color = GermanGold, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
        }
        Text(
            text = "${state.score}",
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            color = scoreColor
        )
        Text(
            text = "/ 100",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Category scores
        if (evaluation != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScoreRow("Vocabulaire", evaluation.scores.vocabulaire.score, evaluation.scores.vocabulaire.commentaire)
                    ScoreRow("Grammaire",   evaluation.scores.grammaire.score,   evaluation.scores.grammaire.commentaire)
                    ScoreRow("Cohérence",   evaluation.scores.coherence.score,   evaluation.scores.coherence.commentaire)
                    ScoreRow("Fluidité",    evaluation.scores.fluidite.score,    evaluation.scores.fluidite.commentaire)
                    ScoreRow("Effort",      evaluation.scores.effort.score,      evaluation.scores.effort.commentaire)
                    if (evaluation.ausspracheScore > 0) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        AusspracheRow(evaluation.ausspracheScore, evaluation.ausspracheWords)
                    }
                }
            }

            // Errors
            if (evaluation.erreurs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader("Fehler")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WrongRed.copy(alpha = 0.06f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        evaluation.erreurs.filter { it.original.isNotBlank() }.forEach { err ->
                            Column {
                                Text(
                                    text = "✗ \"${err.original}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WrongRed
                                )
                                Text(
                                    text = "✓ \"${err.correction}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CorrectGreen
                                )
                                if (err.explication.isNotBlank()) {
                                    Text(
                                        text = "→ ${err.explication}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // New words
            if (evaluation.motsAppris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader("Neue Vokabeln")
                Text(
                    text = evaluation.motsAppris.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ParlezTeal,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Advice
            if (evaluation.conseil.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ParlezTeal.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tipp von Hubert", fontWeight = FontWeight.Bold, color = ParlezTeal,
                            style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(evaluation.conseil, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Error message (always show if present)
        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = WrongRed.copy(alpha = 0.1f))
            ) {
                Text(
                    text = state.errorMessage,
                    color = WrongRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ParlezTeal)
                ) { Text("Nochmal", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onSelectOtherSituation,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ParlezTeal)
                ) { Text("Andere Situation") }
            }
            OutlinedButton(
                onClick = onBackToMenu,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Verlassen") }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ScoreRow(label: String, score: Int, commentaire: String) {
    val color = when {
        score >= 16 -> CorrectGreen
        score >= 10 -> GermanGold
        else        -> WrongRed
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = "$score / 20",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = score / 20f,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        if (commentaire.isNotBlank()) {
            Text(
                text = commentaire,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AusspracheRow(
    score: Int,
    words: List<com.hubert.utils.AzurePronunciationApi.WordResult>
) {
    val color = when {
        score >= 80 -> CorrectGreen
        score >= 50 -> GermanGold
        else        -> WrongRed
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Aussprache", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Ø über alle Wörter · Azure Speech",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            Text(
                text = "$score / 100",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = score / 100f,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        // Problematic words breakdown
        if (words.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Verbesserungswürdig:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            words.forEach { w ->
                val wordColor = when {
                    w.accuracyScore >= 75 -> GermanGold
                    else                  -> WrongRed
                }
                val label = when (w.errorType) {
                    "Mispronunciation" -> "falsch ausgesprochen"
                    "Omission"        -> "ausgelassen"
                    "Insertion"       -> "eingefügt"
                    else              -> "ungenau"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = w.word,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = wordColor
                        )
                        Text(
                            text = "· $label",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    Text(
                        text = "${w.accuracyScore.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = wordColor
                    )
                }
            }
        } else if (score > 0) {
            Text(
                text = "Keine auffälligen Wörter — gut gemacht!",
                style = MaterialTheme.typography.labelSmall,
                color = CorrectGreen.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = "── $text ──",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
