package com.hubert.ui.screens

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.data.model.VocabWord
import com.hubert.ui.theme.*
import com.hubert.viewmodel.LernRundeKind
import com.hubert.viewmodel.LernRundeState
import com.hubert.viewmodel.LernSpiel
import com.hubert.viewmodel.LernStufe
import com.hubert.viewmodel.LernenState

val LernenGreen = Color(0xFF2E7D32)

private fun spielBeschreibung(spiel: LernSpiel): String = when (spiel) {
    LernSpiel.TROUVEZ -> "Zuordnen – Wörter wiedererkennen"
    LernSpiel.ECOUTEZ -> "Hören – Bedeutung wählen"
    LernSpiel.COMPLETEZ -> "Im Satz – das passende Wort wählen"
    LernSpiel.RAPPEL -> "Abrufen – Französisch selbst tippen"
}

/** Overview before and between the rounds of "Vokabeln lernen". */
@Composable
fun LernenScreen(
    state: LernenState,
    onContinue: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Beenden",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Vokabeln lernen", fontSize = 28.sp, fontWeight = FontWeight.Black, color = LernenGreen)
            Spacer(Modifier.height(16.dp))
            when {
                state.isLoading -> CircularProgressIndicator(color = LernenGreen)
                state.isDone -> {
                    Text("🎉", fontSize = 56.sp)
                    Text(
                        text = "Für heute ist alles wiederholt!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Neue Wörter kommen im Menu du jour dazu.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                else -> {
                    Text(
                        text = "${state.dueTotal}",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        color = LernenGreen
                    )
                    Text(
                        text = if (state.results.isEmpty()) "Wörter heute fällig" else "noch fällig",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LernStufe.entries.forEach { stufe ->
                            StufeChip(stufe.label, state.duePerStufe[stufe] ?: 0)
                        }
                    }
                }
            }

            if (state.results.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Heute geschafft",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                state.results.forEachIndexed { index, r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Runde ${index + 1}", fontSize = 13.sp, modifier = Modifier.width(72.dp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        Text(r.spiel.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("✓ ${r.correct}", color = CorrectGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text("✗ ${r.wrong}", color = WrongRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val next = state.nextRound
        if (!state.isLoading && next != null) {
            Text(
                text = "Nächste Runde: ${next.spiel.title} · ${next.ranks.size} Wörter",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = spielBeschreibung(next.spiel),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LernenGreen)
            ) {
                Text(if (state.results.isEmpty()) "Los geht's" else "Weiter", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        if (state.results.isNotEmpty() || state.isDone) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (state.isDone) "Zurück zum Menü" else "Fertig für heute")
            }
        }
    }
}

@Composable
private fun StufeChip(label: String, count: Int) {
    Column(
        modifier = Modifier
            .background(LernenGreen.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$count", fontWeight = FontWeight.Bold, color = LernenGreen)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

/** Écoutez! (hear → pick the meaning) and Rappel (meaning → type the French word). */
@Composable
fun LernRundeScreen(
    state: LernRundeState,
    onChoice: (String) -> Unit,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onSpeak: () -> Unit,
    onQuit: () -> Unit
) {
    val word = state.current ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onQuit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Beenden",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Spacer(Modifier.weight(1f))
            Text("${state.kind.title} · ${state.index + 1}/${state.words.size}",
                fontWeight = FontWeight.Bold, color = LernenGreen)
            Spacer(Modifier.weight(1f))
            Text("✓ ${state.totalCorrect}", color = CorrectGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
        }
        LinearProgressIndicator(
            progress = { (state.index + 1f) / state.words.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp),
            color = LernenGreen,
            trackColor = LernenGreen.copy(alpha = 0.12f)
        )
        Spacer(Modifier.height(24.dp))
        when (state.kind) {
            LernRundeKind.LISTEN -> ListenRound(state, word, onChoice, onSpeak)
            LernRundeKind.RECALL -> RecallRound(state, word, onTyped, onSubmit, onNext, onSpeak)
        }
    }
}

@Composable
private fun ColumnScope.ListenRound(state: LernRundeState, word: VocabWord, onChoice: (String) -> Unit, onSpeak: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hör zu – was bedeutet das?", fontSize = 14.sp, color = LernenGreen)
        Spacer(Modifier.height(16.dp))
        IconButton(
            onClick = onSpeak,
            modifier = Modifier.size(88.dp).clip(CircleShape)
                .background(FrenchBlue.copy(alpha = 0.1f)).border(2.dp, FrenchBlue.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Nochmal hören",
                tint = FrenchBlue, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (state.feedback != null) word.french else " ",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (state.feedback == false) WrongRed else FrenchBlue
        )
    }
    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.choices.forEach { choice ->
            val isAnswer = choice == word.german
            val isSelected = choice == state.selectedChoice
            val (bg, border) = when {
                state.feedback != null && isAnswer -> CorrectGreen.copy(alpha = 0.18f) to CorrectGreen
                state.feedback != null && isSelected -> WrongRed.copy(alpha = 0.18f) to WrongRed
                else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(bg).border(1.5.dp, border, RoundedCornerShape(14.dp))
                    .clickable(enabled = state.feedback == null) { onChoice(choice) },
                contentAlignment = Alignment.Center
            ) {
                Text(choice, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun RecallRound(
    state: LernRundeState,
    word: VocabWord,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onSpeak: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.index) { runCatching { focus.requestFocus() } }
    val isNoun = word.gender != null && word.pos in listOf("nm", "nf")
    val hint = when {
        isNoun && word.gender == "m" -> "Nomen, männlich"
        isNoun -> "Nomen, weiblich"
        word.pos?.startsWith("v") == true -> "Verb"
        word.pos?.startsWith("adj") == true -> "Adjektiv"
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Wie heißt das auf Französisch?", fontSize = 14.sp, color = LernenGreen)
        Spacer(Modifier.height(16.dp))
        Text(word.german, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        hint?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.typedText,
            onValueChange = onTyped,
            enabled = state.feedback == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, textAlign = TextAlign.Center),
            placeholder = { Text("auf Französisch …", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.feedback == null) onSubmit() else onNext() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LernenGreen, cursorColor = LernenGreen)
        )
        if (state.feedback != null) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (if (state.feedback == true) "✓ " else "✗ ") + word.french,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.feedback == true) CorrectGreen else WrongRed
                )
                IconButton(onClick = onSpeak) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Anhören", tint = FrenchBlue)
                }
            }
            word.ipa?.let { Text("/$it/", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { if (state.feedback == null) onSubmit() else onNext() },
            enabled = state.feedback != null || state.typedText.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LernenGreen)
        ) {
            Text(if (state.feedback == null) "Prüfen" else "Weiter", fontWeight = FontWeight.Bold)
        }
    }
}
