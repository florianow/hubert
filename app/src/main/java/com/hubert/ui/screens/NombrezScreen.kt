package com.hubert.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.ui.theme.*
import com.hubert.viewmodel.NombrezQuestion
import com.hubert.viewmodel.NombrezState
import com.hubert.viewmodel.NombrezTopic

val NombrezColor = Color(0xFF009688)

@Composable
fun NombrezSelectionScreen(
    state: NombrezState,
    onToggle: (NombrezTopic) -> Unit,
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
                    text = "Nombrez!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NombrezColor
                )
                Text(
                    text = "Zahlen, Uhrzeit & Datum — wähle Themen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NombrezTopic.entries.forEach { topic ->
                val selected = topic in state.selectedTopics
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggle(topic) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) NombrezColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) NombrezColor.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topic.label,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) NombrezColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = topic.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Icon(
                            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (selected) NombrezColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
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
            colors = ButtonDefaults.buttonColors(containerColor = NombrezColor)
        ) {
            Text("START", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun NombrezScreen(
    state: NombrezState,
    onTypedTextChanged: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    onChoice: (String) -> Unit,
    onReplay: () -> Unit,
    onQuit: () -> Unit
) {
    val question = state.question ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        NombrezTopBar(state, onQuit)
        Spacer(Modifier.height(8.dp))
        TimerBar(fraction = state.timerFraction, timeMs = state.timeRemainingMs)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("${state.totalCorrect}", color = CorrectGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(" / ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 16.sp)
            Text("${state.totalWrong}", color = WrongRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = question.instruction,
            style = MaterialTheme.typography.labelLarge,
            color = NombrezColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        PromptCard(question, state.feedback, onReplay)
        Spacer(Modifier.height(16.dp))

        if (question.isTyped) {
            TypedAnswer(state, onTypedTextChanged, onSubmitTyped)
        } else {
            ChoiceGrid(question, state, onChoice, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PromptCard(question: NombrezQuestion, feedback: Boolean?, onReplay: () -> Unit) {
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
            when {
                feedback != null -> Text(
                    text = question.reveal,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (feedback) CorrectGreen else WrongRed,
                    textAlign = TextAlign.Center
                )
                question.audioOnly -> Text(
                    text = "?",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = FrenchBlue.copy(alpha = 0.4f)
                )
                else -> Text(
                    text = question.prompt,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            if (question.speech != null) {
                Spacer(Modifier.height(12.dp))
                IconButton(
                    onClick = onReplay,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(FrenchBlue.copy(alpha = 0.1f))
                        .border(2.dp, FrenchBlue.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Nochmal hören",
                        tint = FrenchBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun TypedAnswer(state: NombrezState, onTypedTextChanged: (String) -> Unit, onSubmit: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val enabled = state.isPlaying && state.feedback == null
    LaunchedEffect(state.question) {
        if (enabled) runCatching { focusRequester.requestFocus() }
    }
    OutlinedTextField(
        value = state.typedText,
        onValueChange = onTypedTextChanged,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp).focusRequester(focusRequester),
        placeholder = {
            Text(
                text = "Zahl eintippen…",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        },
        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NombrezColor, cursorColor = NombrezColor)
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onSubmit,
        enabled = enabled && state.typedText.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NombrezColor)
    ) {
        Text("CHECK", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
private fun ChoiceGrid(question: NombrezQuestion, state: NombrezState, onChoice: (String) -> Unit, modifier: Modifier) {
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
                        Text(text = choice, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun NombrezTopBar(state: NombrezState, onQuit: () -> Unit) {
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
        Text("Nombrez!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
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
