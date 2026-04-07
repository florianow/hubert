package com.hubert.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.ui.theme.*

@Composable
fun GameOverScreen(
    score: Int,
    isNewHighScore: Boolean,
    stats: List<Triple<String, String, String>>,  // (label, value, type)
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Game Over title
        Text(
            text = "TIME'S UP!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = WrongRed,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isNewHighScore) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GermanGold.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "NEW HIGH SCORE!",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GermanGold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Score display
        Text(
            text = "$score",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = AccentPurple,
            fontSize = 72.sp
        )
        Text(
            text = "points",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            stats.forEach { (label, value, type) ->
                val color = when (type) {
                    "streak" -> FrenchBlue
                    "matches", "correct" -> CorrectGreen
                    "wrong" -> WrongRed
                    "best" -> GermanGold
                    else -> AccentPurple
                }
                StatBox(label = label, value = value, color = color)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Play again button
        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text(
                text = "PLAY AGAIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Menu button
        OutlinedButton(
            onClick = onBackToMenu,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "MENU",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}
