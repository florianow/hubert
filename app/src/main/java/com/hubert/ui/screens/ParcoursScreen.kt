package com.hubert.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hubert.data.repository.ParcoursProgress
import com.hubert.data.repository.ParcoursRepository
import com.hubert.data.repository.ParcoursUnit
import com.hubert.ui.theme.*
import com.hubert.data.repository.ParcoursStep
import com.hubert.viewmodel.OpenLesson
import com.hubert.viewmodel.ParcoursQuizBuilder
import com.hubert.viewmodel.ParcoursState
import com.hubert.viewmodel.QuizKind
import com.hubert.viewmodel.QuizState

val ParcoursBlue = Color(0xFF1565C0)

/** Name of the game an étape practises, for the overview. */
fun parcoursGameName(game: String): String = when (game) {
    "conjugation" -> "Conjuguez!"
    "preposition" -> "Préposez!"
    "relative_pronoun" -> "Relativisez!"
    "agreement" -> "Accordez!"
    "numbers" -> "Nombrez!"
    "gender_snap" -> "Classez!"
    else -> game
}

data class ParcoursActions(
    val back: () -> Unit,              // leave the Parcours
    val toOverview: () -> Unit,
    val selectUnit: (ParcoursUnit) -> Unit,
    val openLesson: (ParcoursUnit, Int) -> Unit,
    val lessonNext: () -> Unit,
    val closeLesson: () -> Unit,
    val jumpTo: (ParcoursUnit) -> Unit,
    val setEnabled: (Boolean) -> Unit,
    val startTraining: (ParcoursUnit) -> Unit,
    val startQuiz: (ParcoursUnit, QuizKind) -> Unit,
    val choose: (String) -> Unit,
    val onTyped: (String) -> Unit,
    val submitTyped: () -> Unit,
    val nextQuestion: () -> Unit,
    val closeQuiz: () -> Unit,
    val startParlez: (ParcoursUnit) -> Unit,
    val speak: (String) -> Unit
)

@Composable
fun ParcoursScreen(state: ParcoursState, actions: ParcoursActions) {
    val lesson = state.lesson
    val unit = state.selectedUnit
    val quiz = state.quiz
    when {
        quiz != null -> {
            BackHandler(onBack = actions.closeQuiz)
            if (quiz.finished) QuizResult(quiz, state, actions) else QuizView(quiz, state, actions)
        }
        lesson != null -> {
            BackHandler(onBack = actions.closeLesson)
            LessonView(lesson, actions)
        }
        unit != null -> UnitDetail(unit, state, actions)
        else -> Overview(state, actions)
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = ParcoursBlue)
    }
}

@Composable
private fun Overview(state: ParcoursState, actions: ParcoursActions) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val currentIndex = state.units.indexOfFirst { it.id == state.progress.current }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Header("Parcours", actions.back)
        Text(
            text = "${state.progress.passed.size} von ${state.units.size} Étapes geschafft · " +
                "Jede Étape: Leçon + Mini-Check → Entraînement → Contrôle (ab ${ParcoursRepository.PASS_SCORE} %). " +
                "Bestandene Étapes kommen nach 3, 7, 21 und 60 Tagen als kurze Révision zurück.",
            fontSize = 12.sp,
            color = muted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Menu du jour: Plat du chef aus dem Parcours", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = state.enabled, onCheckedChange = actions.setEnabled)
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var lastLevel = ""
            state.units.forEachIndexed { index, unit ->
                if (unit.level != lastLevel) {
                    lastLevel = unit.level
                    item(key = "level_${unit.level}") {
                        Text(
                            text = unit.level,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = ParcoursBlue,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }
                }
                item(key = unit.id) {
                    UnitRow(index, unit, state.progress, onClick = { actions.selectUnit(unit) })
                }
            }
        }
    }
}

/** Days until the étape's next Révision (≤ 0 = due), null if it has none. */
private fun reviewDays(unit: ParcoursUnit, progress: ParcoursProgress): Long? =
    progress.reviews[unit.id]?.let { it.dueDay - java.time.LocalDate.now().toEpochDay() }

@Composable
private fun UnitRow(index: Int, unit: ParcoursUnit, progress: ParcoursProgress, onClick: () -> Unit) {
    val passed = unit.id in progress.passed
    val current = unit.id == progress.current
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) ParcoursBlue.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
            .then(if (current) Modifier.border(1.5.dp, ParcoursBlue, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        passed -> CorrectGreen
                        current -> ParcoursBlue
                        else -> muted.copy(alpha = 0.15f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (passed) "✓" else "${index + 1}",
                color = if (passed || current) Color.White else muted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(unit.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                text = listOfNotNull(parcoursGameName(unit.game), unit.parlez?.let { "🗣️ ${it.themeFr}" }).joinToString(" · "),
                fontSize = 12.sp,
                color = muted
            )
            reviewDays(unit, progress)?.let { days ->
                Text(
                    text = if (days <= 0) "🔁 Révision fällig" else "🔁 Révision in $days ${if (days == 1L) "Tag" else "Tagen"}",
                    fontSize = 11.sp,
                    color = if (days <= 0) ParcoursBlue else muted
                )
            }
        }
        if (current) Text("jetzt", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ParcoursBlue)
    }
}

@Composable
private fun UnitDetail(unit: ParcoursUnit, state: ParcoursState, actions: ParcoursActions) {
    BackHandler(onBack = actions.toOverview)
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val index = state.units.indexOf(unit)
    val p = state.progress
    val current = unit.id == p.current
    val passed = unit.id in p.passed
    val lessonRead = unit.id in p.lessonsRead
    val trained = unit.id in p.trained
    val next = when {
        !lessonRead -> ParcoursStep.LECON
        !trained -> ParcoursStep.ENTRAINEMENT
        else -> ParcoursStep.CONTROLE
    }
    val startStep: (ParcoursStep) -> Unit = { step ->
        when (step) {
            ParcoursStep.LECON -> actions.openLesson(unit, 0)
            ParcoursStep.ENTRAINEMENT -> actions.startTraining(unit)
            ParcoursStep.CONTROLE -> actions.startQuiz(unit, QuizKind.CONTROL)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Header("Étape ${index + 1} · ${unit.level}", actions.toOverview)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            Text(unit.title, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(
                text = if (passed) "✓ Bestanden" + (p.control[unit.id]?.let { " mit $it %" } ?: "") else "Ziel: Contrôle ab ${ParcoursRepository.PASS_SCORE} %",
                fontSize = 13.sp,
                color = if (passed) CorrectGreen else muted,
                fontWeight = if (passed) FontWeight.Bold else FontWeight.Normal
            )
            state.notice?.let { notice ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = notice,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ParcoursBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ParcoursBlue.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            StepCard(
                number = 1, step = ParcoursStep.LECON, done = lessonRead, highlighted = !passed && next == ParcoursStep.LECON,
                detail = "${unit.lessons.size} ${if (unit.lessons.size == 1) "Leçon" else "Leçons"} lesen, danach 5 kurze Fragen dazu",
                button = if (lessonRead) "Nochmal" else "Lesen",
                onClick = { startStep(ParcoursStep.LECON) }
            )
            StepCard(
                number = 2, step = ParcoursStep.ENTRAINEMENT, done = trained, highlighted = !passed && next == ParcoursStep.ENTRAINEMENT,
                detail = "${parcoursGameName(unit.game)} nur mit dieser Grammatik · ab ${ParcoursRepository.TRAINING_MIN_CORRECT} richtigen Antworten",
                button = "Spielen",
                onClick = { startStep(ParcoursStep.ENTRAINEMENT) }
            )
            StepCard(
                number = 3, step = ParcoursStep.CONTROLE, done = passed, highlighted = !passed && next == ParcoursStep.CONTROLE,
                detail = "${QuizKind.CONTROL.size} Aufgaben, bestanden ab ${ParcoursRepository.PASS_SCORE} %" +
                    (p.control[unit.id]?.let { " · bisher bestes: $it %" } ?: ""),
                button = "Starten",
                onClick = { startStep(ParcoursStep.CONTROLE) }
            )
            reviewDays(unit, p)?.let { days ->
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { actions.startQuiz(unit, QuizKind.REVIEW) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (days <= 0) "🔁 Révision jetzt (fällig)" else "🔁 Révision (fällig in $days ${if (days == 1L) "Tag" else "Tagen"})",
                        color = ParcoursBlue
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Leçons", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            unit.lessons.forEachIndexed { i, lesson ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { actions.openLesson(unit, i) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📖", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(lesson.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("›", fontSize = 18.sp, color = muted)
                }
            }
            if (unit.parlez != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { actions.startParlez(unit) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("🗣️ Parlez! – „${unit.parlez.themeFr}“", color = ParcoursBlue)
                }
                Text(
                    text = "Freies Gespräch mit Hubert, das genau diese Grammatik braucht (optional).",
                    fontSize = 11.sp,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (current && !passed) {
            Button(
                onClick = { startStep(next) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ParcoursBlue)
            ) {
                Text("Weiter: ${next.icon} ${next.label}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else if (!current) {
            OutlinedButton(
                onClick = { actions.jumpTo(unit) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (passed) "Diese Étape wiederholen" else "Hier weitermachen", color = ParcoursBlue)
            }
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    step: ParcoursStep,
    done: Boolean,
    highlighted: Boolean,
    detail: String,
    button: String,
    onClick: () -> Unit
) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) ParcoursBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .then(if (highlighted) Modifier.border(1.5.dp, ParcoursBlue, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (done) CorrectGreen else if (highlighted) ParcoursBlue else muted.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (done) "✓" else "$number",
                color = if (done || highlighted) Color.White else muted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${step.icon} ${step.label}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(detail, fontSize = 12.sp, color = muted)
        }
        Spacer(Modifier.width(8.dp))
        Text(button, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ParcoursBlue)
    }
}

// ── Quiz ────────────────────────────────────────────────────────────────

@Composable
private fun QuizView(quiz: QuizState, state: ParcoursState, actions: ParcoursActions) {
    val item = quiz.item ?: return
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val index = state.units.indexOfFirst { it.id == quiz.unit.id }
    val answered = quiz.given != null
    val right = answered && ParcoursQuizBuilder.isRight(item, quiz.given!!)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Header("${quiz.kind.title} · Étape ${index + 1}", actions.closeQuiz)
        LinearProgressIndicator(
            progress = { (quiz.index + if (answered) 1 else 0).toFloat() / quiz.items.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = ParcoursBlue
        )
        Text(
            text = "Frage ${quiz.index + 1} von ${quiz.items.size} · ${quiz.correct} richtig",
            fontSize = 12.sp,
            color = muted,
            modifier = Modifier.padding(top = 4.dp)
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(20.dp))
            Text(item.instruction, fontSize = 14.sp, color = muted)
            Spacer(Modifier.height(8.dp))
            if (item.audioOnly && !answered) {
                OutlinedButton(onClick = { item.speech?.let(actions.speak) }, shape = RoundedCornerShape(14.dp)) {
                    Text("🔊 Nochmal hören", color = ParcoursBlue, fontSize = 16.sp)
                }
            } else {
                Text(
                    text = if (answered) item.solved else item.prompt,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                    modifier = Modifier.clickable(enabled = answered) { item.speech?.let(actions.speak) }
                )
            }
            if (item.de.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.de, fontSize = 14.sp, color = muted)
            }
            Spacer(Modifier.height(20.dp))
            if (item.isTyped) {
                OutlinedTextField(
                    value = quiz.typed,
                    onValueChange = actions.onTyped,
                    enabled = !answered,
                    singleLine = true,
                    label = { Text("Zahl eintippen") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { actions.submitTyped() }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!answered) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = actions.submitTyped,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ParcoursBlue)
                    ) { Text("Prüfen") }
                }
            } else {
                item.options.forEach { option ->
                    val isAnswer = option == item.answer
                    val isGiven = option == quiz.given
                    val color = when {
                        answered && isAnswer -> CorrectGreen
                        answered && isGiven -> WrongRed
                        else -> null
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(color?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, color ?: muted.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .clickable(enabled = !answered) { actions.choose(option) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(option, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            if (answered) {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background((if (right) CorrectGreen else WrongRed).copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (right) "✓ Richtig" else "✗ Richtig ist: ${item.answer}",
                        fontWeight = FontWeight.Bold,
                        color = if (right) CorrectGreen else WrongRed
                    )
                    if (item.rule.isNotBlank()) {
                        Text(item.rule, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (answered) {
            Button(
                onClick = actions.nextQuestion,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ParcoursBlue)
            ) {
                Text(if (quiz.isLast) "Auswertung" else "Weiter", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun QuizResult(quiz: QuizState, state: ParcoursState, actions: ParcoursActions) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val index = state.units.indexOfFirst { it.id == quiz.unit.id }
    val good = quiz.passed
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Header("${quiz.kind.title} · Étape ${index + 1}", actions.closeQuiz)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${quiz.correct}/${quiz.items.size}",
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                color = if (good) CorrectGreen else GermanGoldDark,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "${quiz.percent} % richtig",
                fontSize = 14.sp,
                color = muted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = quiz.outcome,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ParcoursBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ParcoursBlue.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            )
            if (quiz.mistakes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Zum Nachlesen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                quiz.mistakes.forEach { m ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable { m.item.speech?.let(actions.speak) }
                            .padding(12.dp)
                    ) {
                        Text(m.item.solved, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("deine Antwort: ${m.given}", fontSize = 12.sp, color = WrongRed)
                        if (m.item.rule.isNotBlank()) Text(m.item.rule, fontSize = 12.sp, color = muted)
                    }
                }
            }
        }
        when {
            quiz.kind == QuizKind.CHECK -> Button(
                onClick = { actions.closeQuiz(); actions.startTraining(quiz.unit) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ParcoursBlue)
            ) { Text("Weiter: 🎯 Entraînement", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            quiz.kind == QuizKind.CONTROL && !good -> Button(
                onClick = { actions.startQuiz(quiz.unit, QuizKind.CONTROL) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ParcoursBlue)
            ) { Text("Nochmal versuchen", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            else -> {}
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = actions.closeQuiz,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Zur Étape", color = ParcoursBlue) }
    }
}

/** A grammar note from anki_french, rendered as HTML; French taps speak, German taps reveal. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LessonView(open: OpenLesson, actions: ParcoursActions) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val page = remember(open.html, dark) { lessonPage(open.html, dark) }
    val speak = rememberUpdatedState(actions.speak)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.closeLesson) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                text = "Leçon ${open.index + 1}/${open.unit.lessons.size} · ${open.unit.title}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ParcoursBlue,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "Tippe auf Französisch zum Anhören, auf ein blaues 👆-Kästchen für die Übersetzung.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun speak(text: String) {
                            post { speak.value(text) }
                        }
                    }, "Hubert")
                }
            },
            update = { view ->
                if (view.tag != page) {
                    view.tag = page
                    view.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
                }
            }
        )
        Button(
            onClick = actions.lessonNext,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ParcoursBlue)
        ) {
            Text(if (open.isLast) "Verstanden ✓" else "Weiter ›", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

private fun lessonPage(html: String, dark: Boolean): String {
    val text = if (dark) "#E8E8E8" else "#1F1F1F"
    val muted = if (dark) "#A0A0A0" else "#666666"
    val card = if (dark) "#262A33" else "#FFFFFF"
    val line = if (dark) "#3A3F4A" else "#E6E6E6"
    val fr = if (dark) "#82B1FF" else "#1565C0"
    val attention = if (dark) "#4A3520" else "#FFF3E0"
    val tip = if (dark) "#1F3A24" else "#E8F5E9"
    val hidden = if (dark) "#22304A" else "#EAF2FD"
    return """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body { font-family: sans-serif; font-size: 16px; line-height: 1.5; color: $text; background: transparent; margin: 0; padding: 4px 18px 24px; }
.section-title { font-size: 24px; font-weight: 800; margin: 12px 0 8px; }
.section-title[data-topic]::after { content: attr(data-topic); font-size: 12px; font-weight: 700; color: #fff; background: $fr; border-radius: 8px; padding: 2px 8px; margin-left: 8px; vertical-align: middle; }
h3 { font-size: 18px; margin: 22px 0 6px; }
h4 { font-size: 15px; margin: 16px 0 4px; }
p, li { margin: 6px 0; }
ul { padding-left: 20px; }
.examples { background: $card; border-left: 3px solid $fr; border-radius: 10px; padding: 8px 12px; margin: 10px 0; }
.examples .fr, .examples > div > .fr { font-weight: 600; color: $fr; margin-top: 4px; }
.examples .de { color: $muted; margin-bottom: 4px; }
.fr { cursor: pointer; }
span.fr { color: $fr; }
.spoiler { border: 1.5px dashed transparent; border-radius: 8px; padding: 1px 6px; transition: color .2s, text-shadow .2s, background .2s; }
.spoiler:not(.clicked) { color: transparent !important; text-shadow: 0 0 3px $muted; background: $hidden; border-color: $fr; cursor: pointer; }
.spoiler:not(.clicked)::before { content: "👆 "; color: $fr; text-shadow: none; }
div.spoiler { margin: 2px 0; }
.attention, .tip { border-radius: 10px; padding: 8px 12px; margin: 10px 0; }
.attention { background: $attention; }
.attention::before { content: "⚠️ "; }
.tip { background: $tip; }
.tip::before { content: "💡 "; }
table { border-collapse: collapse; width: 100%; margin: 8px 0; background: $card; border-radius: 10px; overflow: hidden; }
td, th { padding: 6px 8px; border-bottom: 1px solid $line; text-align: left; vertical-align: top; }
.condensed { color: $muted; font-size: 12px; white-space: nowrap; }
.ipa { color: $muted; font-family: monospace; font-size: 14px; }
u { text-decoration-color: $fr; text-decoration-thickness: 2px; text-underline-offset: 3px; }
a { color: inherit; text-decoration: none; border-bottom: 1px dotted $muted; }
del { color: $muted; }
.rounded-border { border: 1px solid $line; border-radius: 6px; padding: 0 4px; }
</style></head><body>
$html
<script>
document.addEventListener('click', function (e) {
  var a = e.target.closest('a'); if (a) { e.preventDefault(); }
  var sp = e.target.closest('.spoiler');
  if (sp && !sp.classList.contains('clicked')) { sp.classList.add('clicked'); return; }
  var fr = e.target.closest('.fr');
  if (fr) { Hubert.speak(fr.innerText); }
});
</script>
</body></html>
"""
}
