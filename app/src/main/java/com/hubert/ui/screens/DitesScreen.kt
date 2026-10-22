package com.hubert.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hubert.ui.theme.*
import com.hubert.utils.AzurePronunciationApi
import com.hubert.data.model.VocabWord
import com.hubert.viewmodel.DitesCard
import com.hubert.viewmodel.DitesDirection
import com.hubert.viewmodel.DitesState

val DitesColor = Color(0xFF00897B)

@Composable
fun DitesScreen(
    state: DitesState,
    onReveal: () -> Unit,
    onSpeakWord: () -> Unit,
    onSpeakSentence: () -> Unit,
    onToggleTranslation: () -> Unit,
    onNextSentence: () -> Unit,
    onToggleRecording: () -> Unit,
    onRate: (knewIt: Boolean) -> Unit,
    onQuit: () -> Unit
) {
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onToggleRecording()
    }
    val record = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onToggleRecording()
        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

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
            val card = state.current
            Text(
                text = when {
                    state.cards.isEmpty() || card == null -> "Dites!"
                    else -> "Dites! · ${state.index + 1}/${state.cards.size} · " +
                        if (card.direction == DitesDirection.FR_DE) "FR → DE" else "DE → FR"
                },
                fontWeight = FontWeight.Bold,
                color = DitesColor
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }

        val card = state.current
        when {
            state.isFinished -> DitesSummary(state, onQuit)
            card == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DitesColor)
            }
            else -> DitesCardView(state, card, onReveal, onSpeakWord, onSpeakSentence, onToggleTranslation, onNextSentence, record, onRate)
        }
    }
}

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

@Composable
private fun DitesCardView(
    state: DitesState,
    card: DitesCard,
    onReveal: () -> Unit,
    onSpeakWord: () -> Unit,
    onSpeakSentence: () -> Unit,
    onToggleTranslation: () -> Unit,
    onNextSentence: () -> Unit,
    onRecord: () -> Unit,
    onRate: (Boolean) -> Unit
) {
    val frenchFirst = card.direction == DitesDirection.FR_DE
    val sentence = state.sentence
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // ── Front: the word (and its sentence) in the front language ──
        if (frenchFirst) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.word.french, fontSize = 32.sp, fontWeight = FontWeight.Black, color = FrenchBlue)
                IconButton(onClick = onSpeakWord) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Anhören", tint = FrenchBlue)
                }
            }
        } else {
            Text(card.word.german, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        if (!state.isRevealed) {
            sentence?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (frenchFirst) it.fr else it.de,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (frenchFirst) "Was bedeutet das?" else "Wie heißt das auf Französisch? Übersetze den Satz im Kopf.",
                fontSize = 12.sp,
                color = DitesColor,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DitesColor)
            ) { Text("Aufdecken", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            return@Column
        }

        // ── Back: the word block ──
        if (!frenchFirst) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(withArticle(card.word), fontSize = 30.sp, fontWeight = FontWeight.Black, color = FrenchBlue)
                IconButton(onClick = onSpeakWord) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Anhören", tint = FrenchBlue)
                }
            }
        } else {
            Text(withArticle(card.word), fontSize = 16.sp, color = FrenchBlue)
            Spacer(Modifier.height(4.dp))
            Text(card.word.german, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        card.word.ipa?.let { Text("/$it/", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }

        // ── Back: example sentence box (front language, translation as spoiler) ──
        if (sentence != null) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DitesColor.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Beispiel ${state.sentenceIndex + 1}/${card.sentences.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onSpeakSentence, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Satz anhören", tint = FrenchBlue)
                    }
                    if (card.sentences.size > 1) {
                        TextButton(onClick = onNextSentence, enabled = !state.isRecording && !state.isAssessing) {
                            Text("›", fontSize = 22.sp, color = DitesColor)
                        }
                    }
                }
                // Visible line: the front language
                if (frenchFirst) {
                    Text(sentenceWithScores(sentence.fr, sentence.blank, state.wordScores), fontSize = 18.sp)
                } else {
                    Text(sentence.de, fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                // Spoiler line: tap to reveal the translation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (state.translationRevealed) Color.Transparent
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                        )
                        .clickable(onClick = onToggleTranslation)
                        .padding(8.dp)
                ) {
                    if (state.translationRevealed) {
                        if (frenchFirst) {
                            Text(sentence.de, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
                        } else {
                            Text(sentenceWithScores(sentence.fr, sentence.blank, state.wordScores), fontSize = 18.sp)
                        }
                    } else {
                        Text(
                            text = "Übersetzung – tippen zum Anzeigen",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }

        // ── Speak the French sentence — Azure scores it ──
        Spacer(Modifier.height(18.dp))
        if (state.hasAzure) {
            FilledIconButton(
                onClick = onRecord,
                enabled = !state.isAssessing,
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (state.isRecording) WrongRed else DitesColor)
            ) {
                Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = "Sprechen",
                    modifier = Modifier.size(32.dp))
            }
            Text(
                text = when {
                    state.isRecording -> "Sprich jetzt … zum Beenden tippen"
                    state.isAssessing -> "Bewerte …"
                    state.pronScore != null -> "Aussprache: ${state.pronScore}/100"
                    sentence == null -> "Wort sprechen"
                    frenchFirst -> "Satz sprechen"
                    else -> "Satz auf Französisch sprechen"
                },
                fontSize = 14.sp,
                fontWeight = if (state.pronScore != null) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    state.pronScore == null -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    state.pronScore >= 85 -> CorrectGreen
                    state.pronScore >= 65 -> GermanGold
                    else -> WrongRed
                },
                modifier = Modifier.padding(top = 6.dp)
            )
            state.error?.let { Text(it, fontSize = 12.sp, color = WrongRed) }
        } else {
            Text("Für die Aussprache-Bewertung den Azure-Key in den Einstellungen eintragen.",
                fontSize = 12.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }

        // ── Rating: like Anki, it is about the word ──
        Spacer(Modifier.height(24.dp))
        Text("Wusstest du das Wort?", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onRate(false) },
                enabled = !state.isRecording && !state.isAssessing,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Nein", color = WrongRed, fontWeight = FontWeight.Bold) }
            Button(
                onClick = { onRate(true) },
                enabled = !state.isRecording && !state.isAssessing,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen)
            ) { Text("Ja", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The sentence with the target word bold; after speaking, mispronounced words turn red. */
private fun sentenceWithScores(
    sentence: String,
    blank: String?,
    scores: List<AzurePronunciationApi.WordResult>
) = buildAnnotatedString {
    val weak = scores.filter { it.accuracyScore < 70 || it.errorType != "None" }
        .map { it.word.lowercase() }
        .toSet()
    sentence.split(" ").forEachIndexed { i, token ->
        if (i > 0) append(" ")
        val bare = token.trim(',', '.', '!', '?', ';', ':', '«', '»', '"').lowercase()
        val isTarget = blank != null && bare == blank.lowercase()
        val isWeak = bare in weak || bare.substringAfter('\'') in weak
        withStyle(
            SpanStyle(
                fontWeight = if (isTarget) FontWeight.Bold else null,
                color = when {
                    isWeak -> WrongRed
                    scores.isNotEmpty() -> CorrectGreen
                    else -> Color.Unspecified
                }
            )
        ) { append(token) }
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
            Text("✓ ${state.knownCount} gewusst · ✗ ${state.unknownCount} nicht", fontSize = 16.sp)
            state.averagePronScore?.let {
                Text("Aussprache im Schnitt: $it/100", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (it >= 85) CorrectGreen else if (it >= 65) GermanGold else WrongRed)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DitesColor)) { Text("Zurück zum Menü") }
    }
}
