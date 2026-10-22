package com.hubert.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.R
import com.hubert.data.repository.LearningOverview
import com.hubert.ui.theme.*

@Composable
fun MenuScreen(
    matchingHighScore: Int,
    genderSnapHighScore: Int,
    gapFillHighScore: Int,
    spellingBeeHighScore: Int,
    conjugationHighScore: Int,
    pronunciationHighScore: Int,
    prepositionHighScore: Int,
    relativisezHighScore: Int,
    nombrezHighScore: Int,
    accordezHighScore: Int,
    parlezHighScore: Int,
    onMenuDuJour: () -> Unit,
    learningOverview: LearningOverview?,
    onShowProgress: () -> Unit,
    onStartLearning: () -> Unit,
    onStartMatching: () -> Unit,
    onStartGenderSnap: () -> Unit,
    onStartGapFill: () -> Unit,
    onStartSpellingBee: () -> Unit,
    onStartConjugation: () -> Unit,
    onStartPronunciation: () -> Unit,
    onStartPreposition: () -> Unit,
    onStartRelativisez: () -> Unit,
    onStartNombrez: () -> Unit,
    onStartAccordez: () -> Unit,
    onStartDites: () -> Unit,
    onStartParlez: () -> Unit,
    onShowSettings: () -> Unit,
    onShowStatistics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 12.dp)
    ) {
        // Compact header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.hubert_mascot),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HUBERT",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = AccentPurple,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "Français — Deutsch",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onShowStatistics) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Statistik",
                    tint = AccentPurple.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onShowSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Einstellungen",
                    tint = AccentPurple.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // "Menu du jour" — guided multi-game session with a final grade
        Button(
            onClick = onMenuDuJour,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MenuWine)
        ) {
            Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Menu du jour",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Express · Complet · À la carte — mit Note am Ende",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        // "Vokabeln lernen" — works through today's due words, game by game
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onStartLearning,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, LernenGreen),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LernenGreen)
        ) {
            Text(
                text = learningOverview?.dueToday?.takeIf { it > 0 }?.let { "🔁 Vokabeln lernen · $it fällig" } ?: "🔁 Vokabeln lernen",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Learning path at a glance — tap for the weekly report
        if (learningOverview != null && learningOverview.known + learningOverview.learning > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onShowProgress)
                    .background(MenuWine.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProgressFigure("${learningOverview.known}", "sicher")
                ProgressFigure("${learningOverview.learning}", "lernend")
                // Tapping the due count starts a review round right away
                ProgressFigure("${learningOverview.dueToday} ▶", "heute fällig", onClick = onStartLearning)
                ProgressFigure("≈ ${learningOverview.estimatedLevel}", "Wortschatz")
            }
        }

        Spacer(Modifier.height(10.dp))

        // 2×4 game grid — fills all remaining space
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridGameCard(
                    title = "Trouvez!",
                    description = "Französisch-Deutsch zuordnen",
                    accentColor = FrenchBlue,
                    highScore = matchingHighScore,
                    onClick = onStartMatching,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                GridGameCard(
                    title = "Classez!",
                    description = "Genus der Nomen erraten",
                    accentColor = AccentPurple,
                    highScore = genderSnapHighScore,
                    onClick = onStartGenderSnap,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridGameCard(
                    title = "Complétez!",
                    description = "Lückensätze vervollständigen",
                    accentColor = CorrectGreen,
                    highScore = gapFillHighScore,
                    onClick = onStartGapFill,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                GridGameCard(
                    title = "Écrivez!",
                    description = "Gehörte Wörter aufschreiben",
                    accentColor = GermanGold,
                    highScore = spellingBeeHighScore,
                    onClick = onStartSpellingBee,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridGameCard(
                    title = "Conjuguez!",
                    description = "Richtige Verbform wählen",
                    accentColor = FrenchBlue,
                    highScore = conjugationHighScore,
                    onClick = onStartConjugation,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                GridGameCard(
                    title = "Préposez!",
                    description = "Richtige Präposition wählen",
                    accentColor = Color(0xFF9C27B0),
                    highScore = prepositionHighScore,
                    onClick = onStartPreposition,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridGameCard(
                    title = "Prononcez!",
                    description = "Aussprache bewerten lassen",
                    accentColor = WrongRed,
                    highScore = pronunciationHighScore,
                    onClick = onStartPronunciation,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                GridGameCard(
                    title = "Parlez!",
                    description = "KI-Gespräch auf Französisch",
                    accentColor = ParlezTeal,
                    highScore = parlezHighScore,
                    onClick = onStartParlez,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridGameCard(
                    title = "Relativisez!",
                    description = "Relativpronomen einsetzen",
                    accentColor = Color(0xFF3F51B5),
                    highScore = relativisezHighScore,
                    onClick = onStartRelativisez,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                GridGameCard(
                    title = "Nombrez!",
                    description = "Zahlen, Uhrzeit & Datum",
                    accentColor = NombrezColor,
                    highScore = nombrezHighScore,
                    onClick = onStartNombrez,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridGameCard(
                    title = "Accordez!",
                    description = "Partizip richtig angleichen",
                    accentColor = AccordezColor,
                    highScore = accordezHighScore,
                    onClick = onStartAccordez,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                GridGameCard(
                    title = "Dites!",
                    description = "Satz übersetzen & sprechen",
                    accentColor = DitesColor,
                    highScore = 0,
                    subScore = "wie Anki, mit Aussprache",
                    onClick = onStartDites,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ProgressFigure(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier
    ) {
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MenuWine)
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
    }
}

@Composable
private fun GridGameCard(
    title: String,
    description: String,
    accentColor: Color,
    highScore: Int,
    subScore: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight(500),
                    color = accentColor
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (highScore > 0 || subScore != null) {
                Column {
                    if (highScore > 0) {
                        Text(
                            text = "$highScore",
                            fontSize = 22.sp,
                            fontWeight = FontWeight(500),
                            color = accentColor
                        )
                    }
                    if (subScore != null) {
                        Text(
                            text = subScore,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = accentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Text(
                    text = "Noch nicht\ngespielt",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
fun CountdownScreen(count: Int, onBack: (() -> Unit)? = null) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 200f),
        label = "countdown_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to menu",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = AccentPurple.copy(alpha = 0.15f),
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$count",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentPurple
                )
            }
        }
    }
}
