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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.hubert.viewmodel.TodayPlan
import com.hubert.ui.theme.*

private val GRID_CARD_HEIGHT = 104.dp

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
    today: TodayPlan?,
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

        // The day's plan: ① Vokabeln lernen → ② Menu du jour; single games below stay free
        TodayCard(today = today, dueFallback = learningOverview?.dueToday, onStartLearning = onStartLearning, onMenuDuJour = onMenuDuJour)


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
                ProgressFigure("≈ ${learningOverview.estimatedLevel}", "Wortschatz")
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Freies Spiel",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        // 2×4 game grid — fills all remaining space
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GRID_CARD_HEIGHT),
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
                    .height(GRID_CARD_HEIGHT),
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
                    .height(GRID_CARD_HEIGHT),
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
                    .height(GRID_CARD_HEIGHT),
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
                    .height(GRID_CARD_HEIGHT),
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
                    .height(GRID_CARD_HEIGHT),
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
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "Heute": the two steps of the day with their status, plus the streak. */
@Composable
private fun TodayCard(today: TodayPlan?, dueFallback: Int?, onStartLearning: () -> Unit, onMenuDuJour: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MenuWine.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .background(MenuWine.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Heute", fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (today != null && today.streak > 0) {
                Text(
                    text = "🔥 ${today.streak} ${if (today.streak == 1) "Tag" else "Tage"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MenuWine
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val due = today?.dueWords ?: dueFallback ?: 0
        TodayStep(
            number = "1",
            title = "Vokabeln lernen",
            status = if (due > 0) "$due fällig" else "✓ alles wiederholt",
            done = today?.vocabDone == true,
            color = LernenGreen,
            onClick = onStartLearning
        )
        Spacer(Modifier.height(6.dp))
        val menu = today?.menuToday
        TodayStep(
            number = "2",
            title = "Menu du jour",
            status = when {
                menu != null -> "✓ ${menuTypeLabel(menu.menuType)} · ${formatGrade(menu.overallScore)}/20"
                today != null && today.category.isNotEmpty() -> "Thema: ${today.category}"
                else -> "Express · Complet · À la carte"
            },
            done = menu != null,
            color = MenuWine,
            onClick = onMenuDuJour
        )
        if (today?.complete == true) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tagesziel erreicht 🎉 – alles Weitere ist Bonus.",
                fontSize = 12.sp,
                color = muted
            )
        }
    }
}

@Composable
private fun TodayStep(number: String, title: String, status: String, done: Boolean, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (done) color.copy(alpha = 0.08f) else color)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) color else Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (done) "✓" else number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (done) color else Color.White,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            fontSize = 12.sp,
            color = if (done) color else Color.White.copy(alpha = 0.85f)
        )
        Text(" ▶", fontSize = 12.sp, color = if (done) color.copy(alpha = 0.6f) else Color.White)
    }
}

private fun menuTypeLabel(type: String): String = when (type) {
    "EXPRESS" -> "Express"
    "COMPLET" -> "Complet"
    else -> "À la carte"
}

/** Menu score 0–100 as a grade out of 20, e.g. "15,5". */
private fun formatGrade(score: Int): String {
    val grade = score / 5.0
    return if (grade % 1.0 == 0.0) grade.toInt().toString() else String.format(java.util.Locale.GERMAN, "%.1f", grade)
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
