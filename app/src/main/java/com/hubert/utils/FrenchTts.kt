package com.hubert.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads French words aloud using Android's built-in Text-to-Speech.
 * Initializes with French locale for correct pronunciation.
 */
@Singleton
class FrenchTts @Inject constructor(
    @ApplicationContext context: Context
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.FRENCH)
                isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                // Slightly faster speech for game flow
                tts?.setSpeechRate(1.1f)
            }
        }
    }

    /**
     * Speak a French word. Non-blocking, plays audio in background.
     * Uses QUEUE_FLUSH so a new word interrupts any still-playing word.
     */
    fun speak(frenchWord: String) {
        if (isReady) {
            tts?.speak(frenchWord, TextToSpeech.QUEUE_FLUSH, null, frenchWord)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
