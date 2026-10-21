package com.hubert.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.hubert.ui.theme.*
import com.hubert.viewmodel.AccordGroup
import com.hubert.viewmodel.AccordQuestion
import com.hubert.viewmodel.AccordezState

val AccordezColor = Color(0xFFE91E63)

@Composable
fun AccordezSelectionScreen(
    state: AccordezState,
    onToggle: (AccordGroup) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    text = "Accordez!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccordezColor
                )
                Text(
                    text = "Angleichung des Partizips — wähle Regeln",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccordGroup.entries.forEach { group ->
                val selected = group in state.selectedGroups
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggle(group) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) AccordezColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) AccordezColor.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.label,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) AccordezColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = group.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Icon(
                            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (selected) AccordezColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        if (state.highScore > 0) {
            Text(
                text = "Highscore: ${state.highScore}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccordezColor)
        ) {
            Text("START", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun AccordezScreen(
    state: AccordezState,
    onChoice: (String) -> Unit,
    onQuit: () -> Unit
) {
    val question = state.question ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        AccordezTopBar(state, onQuit)
        Spacer(Modifier.height(8.dp))
        TimerBar(fraction = state.timerFraction, timeMs = state.timeRemainingMs)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("${state.totalCorrect}", color = CorrectGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(" / ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 16.sp)
            Text("${state.totalWrong}", color = WrongRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        SentenceCard(question, state.feedback)
        Spacer(Modifier.height(16.dp))
        AccordChoices(question, state, onChoice, Modifier.weight(1f))
    }
}

@Composable
private fun SentenceCard(question: AccordQuestion, feedback: Boolean?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (feedback) {
                true -> CorrectGreen.copy(alpha = 0.12f)
                false -> WrongRed.copy(alpha = 0.12f)
                null -> FrenchBlue.copy(alpha = 0.08f)
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val parts = question.sentence.split("___")
            val sentence = buildAnnotatedString {
                append(parts.first())
                withStyle(
                    SpanStyle(
                        color = if (feedback == null) AccordezColor else if (feedback) CorrectGreen else WrongRed,
                        fontWeight = FontWeight.Bold,
                        background = AccordezColor.copy(alpha = 0.10f)
                    )
                ) { append(if (feedback == null) " ___ " else question.answer) }
                append(parts.getOrElse(1) { "" })
            }
            Text(
                text = sentence,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = question.hint,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (feedback != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = question.rule.explanation,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun AccordChoices(question: AccordQuestion, state: AccordezState, onChoice: (String) -> Unit, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        question.choices.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { choice ->
                    val isAnswer = choice == question.answer
                    val isSelected = choice == state.selectedChoice
                    val (bg, border, text) = when {
                        state.feedback != null && isAnswer -> Triple(CorrectGreen.copy(alpha = 0.2f), CorrectGreen, CorrectGreen)
                        state.feedback != null && isSelected -> Triple(WrongRed.copy(alpha = 0.2f), WrongRed, WrongRed)
                        state.feedback != null -> Triple(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        else -> Triple(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg)
                            .border(if (isSelected || (state.feedback != null && isAnswer)) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
                            .clickable(enabled = state.feedback == null) { onChoice(choice) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = choice, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = text, textAlign = TextAlign.Center)
                    }
                }
                // Participles like "pris" have only 3 forms — keep the grid aligned
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccordezTopBar(state: AccordezState, onQuit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onQuit, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Quit game",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Column {
            Text("Score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Text("${state.score}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AccentPurple)
        }
        Text("Accordez!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        if (state.streak >= 2) {
            Surface(shape = RoundedCornerShape(20.dp), color = GermanGold.copy(alpha = 0.15f)) {
                Text(
                    text = "x${state.streak}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GermanGold
                )
            }
        } else {
            Spacer(Modifier.width(60.dp))
        }
    }
}
