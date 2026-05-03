package com.hubert.viewmodel

/**
 * Represents a single question/answer from a game run.
 * Used to show detailed right/wrong review on the game-over screen.
 */
data class AnswerRecord(
    val question: String,     // What was being asked (e.g. "le ou la: maison", "Je ___ mangé")
    val yourAnswer: String,   // What the user picked/typed
    val correctAnswer: String,// The correct answer
    val isCorrect: Boolean,
    val explanation: String = "" // Optional explanation why the answer is correct (e.g. Préposez!)
)
