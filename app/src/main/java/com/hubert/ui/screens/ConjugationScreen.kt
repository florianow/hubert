package com.hubert.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.hubert.ui.theme.*
import com.hubert.viewmodel.ConjugationState
import com.hubert.viewmodel.ConjugationViewModel
import com.hubert.viewmodel.QuestionMode
import com.hubert.viewmodel.TenseInfo

@Composable
fun ConjugationScreen(
    state: ConjugationState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onSpeak: (String) -> Unit,
    onQuit: () -> Unit,
    onPauseTimer: () -> Unit = {},
    onResumeTimer: () -> Unit = {},
    onUseInfoView: (String) -> Boolean = { true },
    onTypedTextChanged: (String) -> Unit = {},
    onSubmitTyped: () -> Unit = {},
) {
    // Tense info dialog state
    var showTenseInfo by remember { mutableStateOf(false) }

    // Show tense info dialog when tense badge is tapped
    if (showTenseInfo) {
        val tenseKey = if (state.isMoodQuestion) "mood_question"
            else ConjugationViewModel.TENSE_DISPLAY.entries
                .firstOrNull { it.value == state.tenseName }?.key
        val tenseInfo = tenseKey?.let { ConjugationViewModel.TENSE_INFO[it] }

        if (tenseInfo != null) {
            TenseInfoDialog(
                tenseName = state.tenseName,
                tenseInfo = tenseInfo,
                onDismiss = {
                    showTenseInfo = false
                    onResumeTimer()
                }
            )
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
            // Top bar with quit, score, title
            ConjugationTopBar(state, onQuit = onQuit)

            Spacer(modifier = Modifier.height(8.dp))

            // Timer bar
            TimerBar(fraction = state.timerFraction, timeMs = state.timeRemainingMs)

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

            Spacer(modifier = Modifier.height(12.dp))

            // Question area: sentence view or drill view
            if (state.sentenceFr != null) {
                // Sentence view: show sentence with gap
                SentenceQuestionCard(
                    sentenceFr = state.sentenceFr,
                    sentenceDe = state.sentenceDe,
                    infinitive = state.infinitive,
                    german = state.german,
                    ipa = state.ipa,
                    personLabel = state.personLabel,
                    onSpeak = { onSpeak(state.infinitive) }
                )
            } else {
                // Drill view: plain, auxiliary, or verb-form
                DrillQuestionCard(
                    infinitive = state.infinitive,
                    german = state.german,
                    ipa = state.ipa,
                    personLabel = state.personLabel,
                    pcQuestionType = state.pcQuestionType,
                    participleShown = state.participleShown,
                    auxiliaryHint = state.auxiliaryHint,
                    onSpeak = { onSpeak(state.infinitive) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tense badge — tap to see tense explanation (limited uses)
            val currentTenseKey = if (state.isMoodQuestion) "mood_question"
                else ConjugationViewModel.TENSE_DISPLAY.entries
                    .firstOrNull { it.value == state.tenseName }?.key
            val infoRemaining = currentTenseKey?.let { state.infoUsesRemaining[it] } ?: 0
            val infoEnabled = infoRemaining > 0
            val context = LocalContext.current

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = AccentPurple.copy(alpha = if (infoEnabled) 0.15f else 0.06f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (infoEnabled) {
                                if (currentTenseKey != null && onUseInfoView(currentTenseKey)) {
                                    onPauseTimer()
                                    showTenseInfo = true
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "No more info views this round",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.tenseName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (infoEnabled) AccentPurple else AccentPurple.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (infoEnabled) Icons.Default.Info else Icons.Default.Lock,
                            contentDescription = if (infoEnabled) "Tense info" else "Info exhausted",
                            tint = AccentPurple.copy(alpha = if (infoEnabled) 0.5f else 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                        if (infoEnabled) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$infoRemaining",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentPurple.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Answer area: pick or type depending on question mode
            if (state.questionMode == QuestionMode.TYPE) {
                TypeAnswerSection(
                    typedText = state.typedText,
                    feedback = state.feedback,
                    correctForm = state.choices.getOrNull(state.correctIndex) ?: "",
                    enabled = state.isPlaying && state.feedback == null,
                    onTextChanged = onTypedTextChanged,
                    onSubmit = onSubmitTyped,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                // 4 answer choices in 2x2 grid
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
                                    ConjugationChoiceCard(
                                        text = state.choices[idx],
                                        isCorrectAnswer = idx == state.correctIndex,
                                        isSelected = idx == state.selectedIndex,
                                        feedback = state.feedback,
                                        enabled = state.isPlaying && state.feedback == null,
                                        onClick = { onAnswer(idx) },
                                        onSpeak = { onSpeak(state.choices[idx]) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Streak display + Next button
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.awaitingNext) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.feedback == true) CorrectGreen else AccentPurple
                        )
                    ) {
                        Text(
                            text = "NEXT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                } else if (state.streak >= 2) {
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
private fun ConjugationTopBar(state: ConjugationState, onQuit: () -> Unit) {
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
            text = "Conjuguez!",
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
private fun SentenceQuestionCard(
    sentenceFr: String,
    sentenceDe: String?,
    infinitive: String,
    german: String,
    ipa: String? = null,
    @Suppress("UNUSED_PARAMETER") personLabel: String,
    onSpeak: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sentence with highlighted gap
                val annotated = buildAnnotatedString {
                    val parts = sentenceFr.split("___")
                    parts.forEachIndexed { index, part ->
                        append(part)
                        if (index < parts.size - 1) {
                            withStyle(
                                SpanStyle(
                                    color = AccentPurple,
                                    fontWeight = FontWeight.Bold,
                                    background = AccentPurple.copy(alpha = 0.1f)
                                )
                            ) {
                                append(" _____ ")
                            }
                        }
                    }
                }
                Text(
                    text = annotated,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Infinitive hint with speaker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val hintText = if (ipa != null) "$infinitive /$ipa/ ($german)" else "$infinitive ($german)"
                    Text(
                        text = hintText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Listen to $infinitive",
                        tint = FrenchBlue.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onSpeak() }
                    )
                }
            }
        }

        // German translation
        if (sentenceDe != null) {
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
}

@Composable
private fun DrillQuestionCard(
    infinitive: String,
    german: String,
    ipa: String? = null,
    personLabel: String,
    pcQuestionType: String? = null,
    participleShown: String? = null,
    auxiliaryHint: String? = null,
    onSpeak: () -> Unit
) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Infinitive with speaker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = infinitive,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Listen to $infinitive",
                    tint = FrenchBlue.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onSpeak() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // German meaning
            Text(
                text = "($german)",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            if (ipa != null) {
                Text(
                    text = "/$ipa/",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Prompt line — depends on question type
            when (pcQuestionType) {
                "auxiliary" -> {
                    // Auxiliary question: "je ___ mangé?"
                    // Player must pick the correct avoir/être form
                    Text(
                        text = "$personLabel ___ $participleShown ?",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "avoir ou être ?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
                "verb_form" -> {
                    // Verb form question: "j'ai ___?"
                    // Player must pick the correct participle (vs other tense forms)
                    Text(
                        text = "$auxiliaryHint ___ ?",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    // Other tenses: "je → ?"
                    Text(
                        text = "$personLabel \u2192 ?",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ConjugationChoiceCard(
    text: String,
    isCorrectAnswer: Boolean,
    isSelected: Boolean,
    feedback: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor: Color
    val borderColor: Color
    val textColor: Color

    if (feedback != null) {
        when {
            isCorrectAnswer -> {
                backgroundColor = CorrectGreen.copy(alpha = 0.2f)
                borderColor = CorrectGreen
                textColor = CorrectGreen
            }
            isSelected -> {
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
                .padding(8.dp)
        ) {
            // Speaker icon in top-right corner
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Listen to $text",
                tint = textColor.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clickable { onSpeak() }
            )

            // Conjugation text centered
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = if (isSelected || (feedback != null && isCorrectAnswer))
                    FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun TenseSelectionScreen(
    state: ConjugationState,
    onToggleTense: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    // Ordered list of tenses for consistent display
    val tenseOrder = listOf(
        "present", "passe_compose", "imparfait", "futur", "conditionnel",
        "subjonctif", "passe_simple", "imperatif"
    )

    // Dialog state for tense info
    var showInfoForTense by remember { mutableStateOf<String?>(null) }

    // Info dialog
    if (showInfoForTense != null) {
        val tenseName = state.availableTenses[showInfoForTense] ?: showInfoForTense!!
        val tenseInfo = ConjugationViewModel.TENSE_INFO[showInfoForTense]

        if (tenseInfo != null) {
            TenseInfoDialog(
                tenseName = tenseName,
                tenseInfo = tenseInfo,
                onDismiss = { showInfoForTense = null }
            )
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
                .padding(24.dp)
        ) {
            // Top bar with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to menu",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Conjuguez!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccentPurple
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select tenses to practice",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tense chips with info buttons — scrollable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (tenseKey in tenseOrder) {
                    val displayName = state.availableTenses[tenseKey] ?: continue
                    val isSelected = tenseKey in state.selectedTenses

                    TenseChip(
                        label = displayName,
                        isSelected = isSelected,
                        onClick = { onToggleTense(tenseKey) },
                        onInfoClick = { showInfoForTense = tenseKey }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Start button
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple
                )
            ) {
                Text(
                    text = "START",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TenseChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        AccentPurple.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        AccentPurple
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val textColor = if (isSelected) {
        AccentPurple
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info about $label",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentPurple,
                    uncheckedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
private fun TenseInfoDialog(
    tenseName: String,
    tenseInfo: TenseInfo,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                // Title
                Text(
                    text = tenseName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccentPurple
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                // Main description
                Text(
                    text = tenseInfo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sections
                tenseInfo.sections.forEachIndexed { index, section ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Section title (bold)
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Optional section description
                    if (section.description != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = section.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                    }

                    // Examples: French (blue) + German (muted)
                    for ((fr, de) in section.examples) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(
                                    fontWeight = FontWeight.Medium,
                                    color = FrenchBlue
                                )) {
                                    append(fr)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = de,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            lineHeight = 18.sp
                        )
                    }

                    // Optional conjugation table
                    if (section.table != null && section.table.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val headerRow = section.table.first()
                        val dataRows = section.table.drop(1)
                        val columnCount = headerRow.size

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            // Header row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AccentPurple.copy(alpha = 0.12f))
                            ) {
                                headerRow.forEachIndexed { colIdx, cell ->
                                    Text(
                                        text = cell,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentPurple,
                                        textAlign = if (colIdx == 0) TextAlign.Start else TextAlign.Center,
                                        modifier = Modifier
                                            .weight(if (colIdx == 0) 1.2f else 1f)
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Data rows
                            dataRows.forEachIndexed { rowIdx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (rowIdx % 2 == 0) Color.Transparent
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                        )
                                ) {
                                    row.forEachIndexed { colIdx, cell ->
                                        Text(
                                            text = cell,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (colIdx == 0) FontWeight.Medium else FontWeight.Normal,
                                            color = if (colIdx == 0) MaterialTheme.colorScheme.onSurface
                                                    else FrenchBlue,
                                            textAlign = if (colIdx == 0) TextAlign.Start else TextAlign.Center,
                                            modifier = Modifier
                                                .weight(if (colIdx == 0) 1.2f else 1f)
                                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                        )
                                    }
                                    // Pad missing cells if row is shorter than header
                                    repeat(columnCount - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                }

                // OK button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = AccentPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeAnswerSection(
    typedText: String,
    feedback: Boolean?,
    correctForm: String,
    enabled: Boolean,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (feedback == null) {
            OutlinedTextField(
                value = typedText,
                onValueChange = onTextChanged,
                placeholder = { Text("Écrivez la forme conjuguée…") },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Button(
                onClick = onSubmit,
                enabled = enabled && typedText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text(
                    text = "VÉRIFIER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        } else {
            // Show feedback
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (feedback) CorrectGreen.copy(alpha = 0.15f)
                                     else WrongRed.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (feedback) {
                        Text(
                            text = typedText.trim(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = CorrectGreen
                        )
                    } else {
                        Text(
                            text = typedText.trim(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = WrongRed,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                        Text(
                            text = correctForm,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = CorrectGreen
                        )
                    }
                }
            }
        }
    }
}
