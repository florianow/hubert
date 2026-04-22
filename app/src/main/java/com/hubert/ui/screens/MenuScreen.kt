package com.hubert.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
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
    onPronunciationSettings: () -> Unit,
    onStartPreposition: () -> Unit,
    onStartParlez: () -> Unit,
    onParlezSettings: () -> Unit,
    onShowStatistics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Mascot
        Image(
            painter = painterResource(id = R.drawable.hubert_mascot),
            contentDescription = "Hubert the French Axolotl",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // App title
        Text(
            text = "HUBERT",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = AccentPurple,
            letterSpacing = 8.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Francais-Deutsch",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // "Hubert choisit!" — smart pick: plays the least-practiced game
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = AccentPurple.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onHubertChoisit() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = AccentPurple.copy(alpha = 0.12f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Hubert choisit!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Hubert picks what you need to practice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Game mode cards
        GameModeCard(
            title = "Trouvez!",
            description = "Match French words with German translations",
            accentColor = FrenchBlue,
            highScore = matchingHighScore,
            onClick = onStartMatching
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameModeCard(
            title = "Classez!",
            description = "Guess the gender of French nouns",
            accentColor = AccentPurple,
            highScore = genderSnapHighScore,
            onClick = onStartGenderSnap
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameModeCard(
            title = "Compl\u00E9tez!",
            description = "Complete French sentences with the missing word",
            accentColor = CorrectGreen,
            highScore = gapFillHighScore,
            onClick = onStartGapFill
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameModeCard(
            title = "\u00C9crivez!",
            description = "Hear a French word, type it correctly",
            accentColor = GermanGold,
            highScore = spellingBeeHighScore,
            onClick = onStartSpellingBee
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameModeCard(
            title = "Conjuguez!",
            description = "Pick the correct verb conjugation",
            accentColor = FrenchBlue,
            highScore = conjugationHighScore,
            onClick = onStartConjugation
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameModeCard(
            title = "Prononcez!",
            description = "Read French aloud and get pronunciation feedback",
            accentColor = WrongRed,
            highScore = pronunciationHighScore,
            onClick = onStartPronunciation,
            onSettingsClick = onPronunciationSettings
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameModeCard(
            title = "Préposez!",
            description = "Pick the right preposition",
            accentColor = FrenchBlue,
            highScore = prepositionHighScore,
            onClick = onStartPreposition
        )

        Spacer(modifier = Modifier.height(12.dp))

        GameModeCard(
            title = "Parlez!",
            description = "Freie Konversation auf Französisch mit KI-Bewertung",
            accentColor = ParlezTeal,
            highScore = parlezHighScore,
            onClick = onStartParlez,
            onSettingsClick = onParlezSettings
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Statistics button
        OutlinedButton(
            onClick = onShowStatistics,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "STATISTICS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GameModeCard(
    title: String,
    description: String,
    accentColor: Color,
    highScore: Int,
    onClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = accentColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    if (onSettingsClick != null) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = accentColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (highScore > 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$highScore",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GermanGold
                    )
                    Text(
                        text = "best",
                        style = MaterialTheme.typography.labelSmall,
                        color = GermanGold.copy(alpha = 0.7f)
                    )
                }
            } else {
                Text(
                    text = "PLAY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 2.sp
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
        // Back button in top-left corner
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
