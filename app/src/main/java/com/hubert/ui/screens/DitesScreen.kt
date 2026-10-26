package com.hubert.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hubert.ui.theme.*
import com.hubert.utils.AzurePronunciationApi
import com.hubert.utils.ClozeGame
import com.hubert.viewmodel.DitesDirection
import com.hubert.viewmodel.DitesOutcome
import com.hubert.viewmodel.DitesState

val DitesColor = Color(0xFF00897B)

/** All callbacks of a Dites! round. */
data class DitesActions(
    val tile: (Int) -> Unit,
    val slot: (Int) -> Unit,
    val lightning: () -> Unit,
    val giveUp: () -> Unit,
    val speakSentence: () -> Unit,
    val speakWord: () -> Unit,
    val toggleRecording: () -> Unit,
    val next: () -> Unit,
    val quit: () -> Unit
)

/**
 * A Dites! round inside "Vokabeln lernen": the anki_french Lückenspiel, graded by the game.
 * Front sentence with the target underlined, rebuild the other one from tiles (★ = target),
 * then a short solution with audio and optional speaking.
 */
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
    val card = state.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.quit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Beenden",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (card == null) "Dites!" else "Dites! · ${state.index + 1}/${state.cards.size} · " +
                    if (card.direction == DitesDirection.FR_DE) "FR → DE" else "DE → FR",
                fontWeight = FontWeight.Bold,
                color = LernenGreen
            )
            Spacer(Modifier.weight(1f))
            Text("✓ ${state.outcomes.count { it != DitesOutcome.FALSCH }}", color = CorrectGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
        }
        LinearProgressIndicator(
            progress = { (state.index + 1f) / state.cards.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp),
            color = LernenGreen,
            trackColor = LernenGreen.copy(alpha = 0.12f)
        )

        val cloze = state.cloze
        if (card == null || cloze == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LernenGreen) }
            return@Column
        }
        val frenchFirst = card.direction == DitesDirection.FR_DE
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (frenchFirst) "Übersetze ins Deutsche – ★ ist das gesuchte Wort"
                       else "Baue den Satz auf Französisch – ★ ist das gesuchte Wort",
                fontSize = 13.sp,
                color = LernenGreen,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = marked(if (frenchFirst) card.example.fr else card.example.de),
                fontSize = 21.sp,
                lineHeight = 30.sp,
                fontStyle = if (frenchFirst) FontStyle.Normal else FontStyle.Italic,
                textAlign = TextAlign.Center
            )
            if (frenchFirst) {
                IconButton(onClick = actions.speakSentence) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Satz anhören", tint = FrenchBlue)
                }
            }
            Spacer(Modifier.height(16.dp))
            ClozeBox(cloze, isGerman = frenchFirst, actions.tile, actions.slot)

            val outcome = state.outcome
            if (outcome == null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (cloze.hasStar && cloze.slots.any { !it.isStar && !it.revealed }) {
                        TextButton(onClick = actions.lightning) { Text("⚡ nur das Zielwort", color = LernenGreen) }
                    }
                    TextButton(onClick = actions.giveUp) { Text("Auflösen", color = WrongRed) }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Solution(state, outcome, actions, record)
            }
        }
        if (state.outcome != null) {
            Button(
                onClick = actions.next,
                enabled = !state.isRecording && !state.isAssessing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LernenGreen)
            ) { Text(if (state.index + 1 >= state.cards.size) "Fertig" else "Weiter", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

/** Result badge, the word and the French sentence with audio; optional speaking. */
@Composable
private fun Solution(state: DitesState, outcome: DitesOutcome, actions: DitesActions, record: () -> Unit) {
    val card = state.current ?: return
    val color = when (outcome) {
        DitesOutcome.RICHTIG -> CorrectGreen
        DitesOutcome.WACKLIG -> GermanGold
        DitesOutcome.FALSCH -> WrongRed
    }
    Text(outcome.label, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(card.word.french, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FrenchBlue)
        IconButton(onClick = actions.speakWord) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Wort anhören", tint = FrenchBlue)
        }
    }
    Text(card.word.german, fontSize = 18.sp, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = actions.speakSentence, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Satz anhören", tint = FrenchBlue)
        }
        Text(markedWithScores(card.example.fr, state.wordScores), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }

    // Optional: speak the French sentence, Azure scores it (not part of the grade)
    if (state.hasAzure) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = record,
                enabled = !state.isAssessing,
                modifier = Modifier.size(52.dp),
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
                        else -> "Satz nachsprechen (optional)"
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
