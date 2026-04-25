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
import androidx.compose.material.icons.filled.Psychology
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
    parlezHighScore: Int,
    onHubertChoisit: () -> Unit,
    onStartMatching: () -> Unit,
    onStartGenderSnap: () -> Unit,
    onStartGapFill: () -> Unit,
    onStartSpellingBee: () -> Unit,
    onStartConjugation: () -> Unit,
    onStartPronunciation: () -> Unit,
    onStartPreposition: () -> Unit,
    onStartParlez: () -> Unit,
    onShowSettings: () -> Unit
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
            IconButton(onClick = onShowSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Einstellungen",
                    tint = AccentPurple.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // "Hubert choisit!" — solid full-width button
        Button(
            onClick = onHubertChoisit,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B5CE7))
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Hubert choisit!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Hubert wählt was du üben solltest",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
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
        }
    }
}

@Composable
private fun GridGameCard(
    title: String,
    description: String,
    accentColor: Color,
    highScore: Int,
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
            if (highScore > 0) {
                Text(
                    text = "$highScore",
                    fontSize = 22.sp,
                    fontWeight = FontWeight(500),
                    color = accentColor
                )
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
