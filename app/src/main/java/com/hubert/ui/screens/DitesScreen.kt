package com.hubert.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.hubert.viewmodel.DitesCard
import com.hubert.viewmodel.DitesState

val DitesColor = Color(0xFF00897B)

@Composable
fun DitesScreen(
    state: DitesState,
    onReveal: () -> Unit,
    onListen: () -> Unit,
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
            Text(
                text = if (state.cards.isEmpty()) "Dites!" else "Dites! · ${(state.index + 1).coerceAtMost(state.cards.size)}/${state.cards.size}",
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
            else -> DitesCardView(state, card, onReveal, onListen, record, onRate)
        }
    }
}

@Composable
private fun DitesCardView(
    state: DitesState,
    card: DitesCard,
    onReveal: () -> Unit,
    onListen: () -> Unit,
    onRecord: () -> Unit,
    onRate: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        // Front: German word and sentence — translate it in your head
        Text(card.word.german, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        card.sentenceDe?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, fontSize = 17.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (card.sentenceDe != null) "Übersetze den Satz im Kopf" else "Wie heißt das auf Französisch?",
            fontSize = 12.sp,
            color = DitesColor
        )

        if (!state.isRevealed) {
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DitesColor)
            ) { Text("Aufdecken", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            return@Column
        }

        // Back: French word and sentence
        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.word.french, fontSize = 30.sp, fontWeight = FontWeight.Black, color = FrenchBlue)
            IconButton(onClick = onListen) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Anhören", tint = FrenchBlue)
            }
        }
        card.word.ipa?.let { Text("/$it/", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
        card.sentenceFr?.let { fr ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = sentenceWithScores(fr, card.blank, state.wordScores),
                fontSize = 19.sp,
                textAlign = TextAlign.Center
            )
        }

        // Speak it — Azure scores the pronunciation
        Spacer(Modifier.height(20.dp))
        if (state.hasAzure) {
            FilledIconButton(
                onClick = onRecord,
                enabled = !state.isAssessing,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (state.isRecording) WrongRed else DitesColor)
            ) {
                Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = "Sprechen",
                    modifier = Modifier.size(34.dp))
            }
            Text(
                text = when {
                    state.isRecording -> "Sprich jetzt … zum Beenden tippen"
                    state.isAssessing -> "Bewerte …"
                    state.pronScore != null -> "Aussprache: ${state.pronScore}/100"
                    else -> if (card.sentenceFr != null) "Satz sprechen" else "Wort sprechen"
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

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onRate(false) },
                enabled = !state.isRecording && !state.isAssessing,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Wusste ich nicht", color = WrongRed) }
            Button(
                onClick = { onRate(true) },
                enabled = !state.isRecording && !state.isAssessing,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen)
            ) { Text("Wusste ich", fontWeight = FontWeight.Bold) }
        }
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
