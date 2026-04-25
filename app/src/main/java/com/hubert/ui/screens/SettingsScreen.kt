package com.hubert.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hubert.data.repository.AppSettings
import com.hubert.ui.theme.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (geminiKey: String, azureKey: String, azureRegion: String) -> Unit,
    onBack: () -> Unit
) {
    var geminiKey   by remember(settings.geminiKey)   { mutableStateOf(settings.geminiKey) }
    var azureKey    by remember(settings.azureKey)    { mutableStateOf(settings.azureKey) }
    var azureRegion by remember(settings.azureRegion) { mutableStateOf(settings.azureRegion.ifBlank { "northeurope" }) }

    var showGemini by remember { mutableStateOf(false) }
    var showAzure  by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                text = "Einstellungen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = AccentPurple,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Gemini section ──────────────────────────────────────────────────
            SectionCard(
                title = "Google Gemini",
                subtitle = "Benötigt für: Parlez!",
                accentColor = ParlezTeal,
                hasKey = settings.hasGemini
            ) {
                Text(
                    text = "API Key erstellen: aistudio.google.com/app/apikey",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showGemini) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showGemini = !showGemini }) {
                            Text(if (showGemini) "Verstecken" else "Anzeigen", fontSize = 11.sp)
                        }
                    }
                )
            }

            // ── Azure section ───────────────────────────────────────────────────
            SectionCard(
                title = "Azure Speech Services",
                subtitle = "Benötigt für: Prononcez!, Parlez!",
                accentColor = WrongRed,
                hasKey = settings.hasAzure
            ) {
                Text(
                    text = "API Key erstellen: portal.azure.com → Speech Services",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = azureKey,
                    onValueChange = { azureKey = it },
                    label = { Text("Azure Speech API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showAzure) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showAzure = !showAzure }) {
                            Text(if (showAzure) "Verstecken" else "Anzeigen", fontSize = 11.sp)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = azureRegion,
                    onValueChange = { azureRegion = it },
                    label = { Text("Region") },
                    singleLine = true,
                    placeholder = { Text("northeurope") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Save button
        Button(
            onClick = { onSave(geminiKey, azureKey, azureRegion) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text("Speichern", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    hasKey: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasKey) accentColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (hasKey) "✓ Gesetzt" else "Nicht gesetzt",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasKey) accentColor
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun NeedsApiKeyDialog(
    gameName: String,
    neededKeys: List<String>,
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "API-Key benötigt",
                fontWeight = FontWeight.Bold,
                color = AccentPurple
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "$gameName benötigt externe KI-Dienste. Bitte richte zuerst deine API-Keys ein:",
                    style = MaterialTheme.typography.bodyMedium
                )
                neededKeys.forEach { key ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentPurple.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "· $key",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple
                        )
                    }
                }
                Text(
                    text = "Die Keys sind kostenlos erhältlich und werden nur auf deinem Gerät gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGoToSettings,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text("Einstellungen öffnen", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
