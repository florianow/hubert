package com.hubert.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hubert.data.model.VocabWord
import com.hubert.ui.theme.*
import com.hubert.utils.AzurePronunciationApi
import com.hubert.utils.ClozeGame
import com.hubert.viewmodel.ConjuguezViewModel
import com.hubert.viewmodel.DitesCard
import com.hubert.viewmodel.DitesDirection
import com.hubert.viewmodel.DitesGrade
import com.hubert.viewmodel.DitesState
import kotlin.math.roundToInt

val DitesColor = Color(0xFF00897B)

/** All callbacks of the Dites! screen. */
data class DitesActions(
    val startCloze: () -> Unit,
    val lightning: () -> Unit,
    val clozeTile: (Int) -> Unit,
    val clozeSlot: (Int) -> Unit,
    val backClozeTile: (Int) -> Unit,
    val backClozeSlot: (Int) -> Unit,
    val toggleBackCloze: () -> Unit,
    val reveal: () -> Unit,
    val speakWord: () -> Unit,
    val speakSentence: () -> Unit,
    val toggleTranslation: () -> Unit,
    val nextExample: () -> Unit,
    val toggleConjugation: () -> Unit,
    val toggleRecording: () -> Unit,
    val rate: (DitesGrade) -> Unit,
    val quit: () -> Unit
)

@Composable
fun DitesScreen(state: DitesState, actions: DitesActions) {
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) actions.toggleRecording()
    }
    val record = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            actions.toggleRecording()
        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        val card = state.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.quit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Beenden",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    card == null -> "Dites!"
                    else -> "Dites! · ${state.index + 1}/${state.cards.size} · " +
                        if (card.direction == DitesDirection.FR_DE) "FR → DE" else "DE → FR"
                },
                fontWeight = FontWeight.Bold,
                color = DitesColor
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }

        when {
            state.isFinished -> DitesSummary(state, actions.quit)
            card == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DitesColor)
            }
            !state.isRevealed -> DitesFront(state, card, actions)
            else -> DitesBack(state, card, actions, record)
        }
    }
}

// ── Front ───────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.DitesFront(state: DitesState, card: DitesCard, actions: DitesActions) {
    val frenchFirst = card.direction == DitesDirection.FR_DE
    val example = state.example
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        if (example != null) {
            // Sentence in the front language, target word underlined (as on the Anki card)
            Text(
                text = marked(if (frenchFirst) example.fr else example.de),
                fontSize = 21.sp,
                lineHeight = 30.sp,
                fontStyle = if (frenchFirst) FontStyle.Normal else FontStyle.Italic,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            state.cloze?.let { cloze ->
                if (state.clozeStarted) {
                    ClozeBox(cloze, isGerman = frenchFirst, actions.clozeTile, actions.clozeSlot)
                } else {
                    ClozeOverlay(cloze, isGerman = frenchFirst, actions)
                }
            }
            Spacer(Modifier.height(16.dp))
        } else if (frenchFirst) {
            Text(card.word.french, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = FrenchBlue)
        }
        // Hint below the game: the word in the other language
        Text(
            text = "(${if (frenchFirst) card.word.french else card.word.german})",
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
    Button(
        onClick = actions.reveal,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DitesColor)
    ) { Text("Antwort anzeigen", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

/** The game behind a blurred cover with Anki's three choices: ⚡ · 🔊 · start. */
@Composable
private fun ClozeOverlay(cloze: ClozeGame, isGerman: Boolean, actions: DitesActions) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
    ) {
        // Blurred preview of the tiles (blur needs Android 12+, older versions show it dimmed)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(14.dp) else Modifier)
                .clearAndSetSemantics { }
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = cloze.tiles.joinToString("   ") { if (it.isStar) "★" else it.word },
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            // As in the deck: ⚡ only when there is a target, 🔊 only when the hidden sentence is French
            val showLightning = cloze.hasStar
            val showPlay = !isGerman
            if (showLightning || showPlay) {
                Row(modifier = Modifier.fillMaxWidth().height(84.dp)) {
                    if (showLightning) OverlayButton("⚡", "nur das Zielwort", Modifier.weight(1f), actions.lightning)
                    if (showLightning && showPlay) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                    }
                    if (showPlay) OverlayButton("🔊", "anhören", Modifier.weight(1f), actions.speakSentence)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
            OverlayButton("🧩", "Lückenspiel", Modifier.fillMaxWidth().height(120.dp), actions.startCloze)
        }
    }
}

@Composable
private fun OverlayButton(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 30.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

// ── Back ────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.DitesBack(state: DitesState, card: DitesCard, actions: DitesActions, record: () -> Unit) {
    val word = card.word
    val example = state.example
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Rank, top right
        Text(
            text = "#${word.rank}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )
        Text(withArticle(word), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = FrenchBlue, textAlign = TextAlign.Center)
        word.ipa?.let { Text("\\$it\\", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)) }
        Spacer(Modifier.height(8.dp))
        FilledIconButton(
            onClick = actions.speakWord,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onBackground)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Wort anhören",
                tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            word.pos?.let {
                Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textDecoration = TextDecoration.Underline, modifier = Modifier.padding(end = 10.dp, bottom = 4.dp))
            }
            Text(word.german, fontSize = 30.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center)
        }

        // Example box: counter, next, Lückenspiel; the first translation is open, later ones are spoilers
        if (example != null) {
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("${state.exampleIndex + 1}/${card.examples.size}", fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                CircleIcon(enabled = card.examples.size > 1 && !state.isRecording, onClick = actions.nextExample) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Satz")
                }
                Spacer(Modifier.width(8.dp))
                CircleIcon(enabled = true, onClick = actions.toggleBackCloze) {
                    Icon(Icons.Default.Extension, contentDescription = "Lückenspiel",
                        tint = if (state.backCloze != null) DitesColor else LocalContentColor.current)
                }
            }
            Spacer(Modifier.height(8.dp))
            val frenchFirst = card.direction == DitesDirection.FR_DE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                val backCloze = state.backCloze
                if (backCloze != null) {
                    Text(marked(if (frenchFirst) example.fr else example.de), fontSize = 18.sp,
                        fontStyle = if (frenchFirst) FontStyle.Normal else FontStyle.Italic)
                    Spacer(Modifier.height(10.dp))
                    ClozeBox(backCloze, isGerman = frenchFirst, actions.backClozeTile, actions.backClozeSlot)
                } else {
                    val french = @Composable {
                        Row(verticalAlignment = Alignment.Top) {
                            IconButton(onClick = actions.speakSentence, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Satz anhören", modifier = Modifier.size(20.dp))
                            }
                            Text(markedWithScores(example.fr, state.wordScores), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val german = @Composable {
                        Text(marked(example.de), fontSize = 18.sp, fontStyle = FontStyle.Italic)
                    }
                    // Front language first, translation below (tap to show / hide)
                    if (frenchFirst) french() else german()
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.translationRevealed) Color.Transparent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
                            .clickable(onClick = actions.toggleTranslation)
                            .padding(vertical = 4.dp)
                    ) {
                        if (state.translationRevealed) {
                            if (frenchFirst) german() else french()
                        } else {
                            Text("Übersetzung – tippen zum Anzeigen", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }

        // Conjugation for verbs (Hubert's tables), folded like "Alles anzeigen"
        card.conjugation?.let { verb ->
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = actions.toggleConjugation) {
                    Text(if (state.showConjugation) "Konjugation ausblenden" else "Konjugation anzeigen")
                }
            }
            if (state.showConjugation) ConjugationTable(verb.tenses)
        }

        // Speak the French sentence — Azure scores it (Hubert's extra)
        Spacer(Modifier.height(16.dp))
        if (state.hasAzure) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = record,
                    enabled = !state.isAssessing,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (state.isRecording) WrongRed else DitesColor)
                ) {
                    Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = "Sprechen")
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = when {
                            state.isRecording -> "Sprich jetzt … zum Beenden tippen"
                            state.isAssessing -> "Bewerte …"
                            state.pronScore != null -> "Aussprache: ${state.pronScore}/100"
                            example != null -> "Satz sprechen"
                            else -> "Wort sprechen"
                        },
                        fontWeight = if (state.pronScore != null) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            state.pronScore == null -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            state.pronScore >= 85 -> CorrectGreen
                            state.pronScore >= 65 -> GermanGold
                            else -> WrongRed
                        }
                    )
                    state.error?.let { Text(it, fontSize = 12.sp, color = WrongRed) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // Anki's answer buttons with Hubert's next interval
    Row(modifier = Modifier.fillMaxWidth()) {
        DitesGrade.entries.forEach { grade ->
            val color = when (grade) {
                DitesGrade.AGAIN -> WrongRed
                DitesGrade.HARD -> MaterialTheme.colorScheme.onBackground
                DitesGrade.GOOD -> CorrectGreen
                DitesGrade.EASY -> FrenchBlue
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    .clickable(enabled = !state.isRecording && !state.isAssessing) { actions.rate(grade) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(formatInterval(state.intervals[grade], grade), fontSize = 13.sp, color = color)
                Text(grade.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = color)
            }
        }
    }
}

@Composable
private fun CircleIcon(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    OutlinedIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.8f else 0.3f))) {
        content()
    }
}

private fun formatInterval(days: Double?, grade: DitesGrade): String = when {
    days == null -> " "
    grade == DitesGrade.AGAIN -> "morgen"
    days < 1.5 -> "1 T"
    days < 30 -> "${days.roundToInt()} T"
    days < 365 -> String.format("%.1f Mo", days / 30.0).replace('.', ',')
    else -> String.format("%.1f J", days / 365.0).replace('.', ',')
}

@Composable
private fun ConjugationTable(tenses: Map<String, List<String>>) {
    val persons = listOf("je", "tu", "il/elle", "nous", "vous", "ils/elles")
    val order = listOf("present", "passe_compose", "imparfait", "futur", "conditionnel", "subjonctif")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        order.filter { tenses[it]?.any { f -> f.isNotBlank() } == true }.forEach { tense ->
            Text(ConjuguezViewModel.TENSE_DISPLAY[tense] ?: tense, fontWeight = FontWeight.Bold, color = DitesColor, fontSize = 14.sp)
            tenses.getValue(tense).forEachIndexed { i, form ->
                if (form.isNotBlank()) {
                    Row {
                        Text(persons.getOrElse(i) { "" }, fontSize = 13.sp, modifier = Modifier.width(72.dp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
                        Text(form, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── Shared pieces ───────────────────────────────────────────────────────

/** "le/la/l'" for nouns; nothing where the data can't tell (h muet/aspiré). */
private fun withArticle(word: VocabWord): String {
    val isNoun = word.gender != null && word.pos in listOf("nm", "nf")
    if (!isNoun) return word.french
    val first = word.french.first().lowercaseChar()
    return when {
        first in "aeiouâéèêîôûœ" -> "l'${word.french}"
        first == 'h' -> word.french
        word.gender == "m" -> "le ${word.french}"
        else -> "la ${word.french}"
    }
}

private val MARKED = Regex("\\*([^*]+)\\*")

/** Deck sentence with its *target* underlined (the deck uses a dotted line). */
private fun marked(sentence: String) = buildAnnotatedString {
    var pos = 0
    MARKED.findAll(sentence).forEach { m ->
        append(sentence.substring(pos, m.range.first))
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = Color(0xFF29B6F6))) { append(m.groupValues[1]) }
        pos = m.range.last + 1
    }
    append(sentence.substring(pos))
}

/** French sentence: target underlined; after speaking, weak words red and the rest green. */
private fun markedWithScores(sentence: String, scores: List<AzurePronunciationApi.WordResult>) = buildAnnotatedString {
    if (scores.isEmpty()) {
        append(marked(sentence))
        return@buildAnnotatedString
    }
    val weak = scores.filter { it.accuracyScore < 70 || it.errorType != "None" }.map { it.word.lowercase() }.toSet()
    sentence.replace("*", "").split(" ").forEachIndexed { i, token ->
        if (i > 0) append(" ")
        val bare = token.trim(',', '.', '!', '?', ';', ':', '«', '»', '"').lowercase()
        val isWeak = bare in weak || bare.substringAfter('\'') in weak || bare.substringAfter('’') in weak
        withStyle(SpanStyle(color = if (isWeak) WrongRed else CorrectGreen)) { append(token) }
    }
}

/** The Lückenspiel: hidden word blocks, alphabetical tiles, ★ for the target word. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeBox(cloze: ClozeGame, isGerman: Boolean, onTile: (Int) -> Unit, onSlot: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            cloze.segments.forEachIndexed { i, segment ->
                // No gap before punctuation that follows a word directly ("ongle.", "l'")
                val next = cloze.segments.getOrNull(i + 1)
                val gap = if (next is ClozeGame.Segment.Text && next.text.firstOrNull()?.isWhitespace() != true) 0.dp else 4.dp
                when (segment) {
                    is ClozeGame.Segment.Text -> {
                        val t = segment.text.trim()
                        if (t.isNotEmpty()) Text(t, fontSize = 18.sp, modifier = Modifier.padding(end = 4.dp))
                    }
                    is ClozeGame.Segment.Slot -> if (segment.revealed) {
                        Text(
                            text = segment.word,
                            fontSize = 18.sp,
                            fontStyle = if (isGerman) FontStyle.Italic else FontStyle.Normal,
                            fontWeight = if (segment.isStar) FontWeight.Bold else FontWeight.Normal,
                            color = if (segment.isStar) DitesColor else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(end = gap)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(end = maxOf(gap, 2.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))
                                .clickable { onSlot(i) }
                                // Hidden word must not leak to screen readers (or UI tests)
                                .clearAndSetSemantics { contentDescription = "verdecktes Wort, tippen für Hinweis" }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            // Invisible text keeps the block as wide as the hidden word
                            Text(segment.word, fontSize = 18.sp, color = Color.Transparent)
                        }
                    }
                }
            }
        }
        if (!cloze.isFinished) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                cloze.tiles.forEachIndexed { i, tile ->
                    if (!tile.used) {
                        OutlinedButton(
                            onClick = { onTile(i) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (cloze.errorTile == i) 2.dp else 1.dp,
                                if (cloze.errorTile == i) WrongRed else MaterialTheme.colorScheme.outline
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (tile.isStar) Text("★", fontSize = 18.sp, color = DitesColor)
                            else Text(tile.word, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DitesSummary(state: DitesState, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.cards.isEmpty()) {
            Text("Gerade keine Wörter zum Üben.", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Neue Wörter kommen aus Anki oder dem Menu du jour.", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        } else {
            Text("Bien joué !", fontSize = 30.sp, fontWeight = FontWeight.Black, color = DitesColor)
            Spacer(Modifier.height(12.dp))
            DitesGrade.entries.forEach { grade ->
                val n = state.grades.count { it == grade }
                if (n > 0) Text("${grade.label}: $n", fontSize = 16.sp)
            }
            state.averagePronScore?.let {
                Spacer(Modifier.height(6.dp))
                Text("Aussprache im Schnitt: $it/100", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (it >= 85) CorrectGreen else if (it >= 65) GermanGold else WrongRed)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DitesColor)) { Text("Zurück zum Menü") }
    }
}
