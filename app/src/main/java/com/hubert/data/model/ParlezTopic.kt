package com.hubert.data.model

data class ParlezTopic(
    val id: String,
    val themeFr: String,
    val themeDe: String,
    val niveau: String,            // "A1", "A2", "B1", "B2"
    val descriptionDe: String,
    val scenarioDe: String = "",   // Situation + what the player should do (shown in UI)
    val hubertRoleFr: String = "", // Hubert's role in French (fed to system prompt)
    val starterFr: String,         // Hubert's opening line
    val vocabHints: List<String>
)
