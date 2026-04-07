package com.hubert.data.model

/**
 * A vocabulary word pair: French word + German translation.
 * Loaded from bundled JSON asset.
 */
data class VocabWord(
    val rank: Int,
    val french: String,
    val german: String,
    val gender: String? = null,      // "m" or "f" (nouns only)
    val ipa: String? = null,         // IPA pronunciation
    val pos: String? = null,         // part of speech (nm, nf, adj, vi, ...)
    val categories: List<String>? = null  // thematic categories (Tiere, Farben, ...)
)

/**
 * An example sentence with a blanked-out word (for Gap Fill).
 */
data class SentenceEntry(
    val fr: String,    // French sentence (full)
    val de: String,    // German translation (full)
    val blank: String  // The word that was blanked out
)
