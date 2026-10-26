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
    val blank: String,  // The word that was blanked out
    val deBlank: String? = null  // The German counterpart of the target word (Anki highlight)
)

/**
 * An example sentence pair from the Anki deck, with the target word marked as *word* in both
 * languages (examples.json).
 */
data class Example(val fr: String, val de: String) {
    val frPlain: String get() = fr.replace("*", "")
    val dePlain: String get() = de.replace("*", "")
    val frTarget: String? get() = Regex("\\*([^*]+)\\*").find(fr)?.groupValues?.get(1)
    val deTarget: String? get() = Regex("\\*([^*]+)\\*").find(de)?.groupValues?.get(1)
}

/**
 * A verb with its conjugation forms across tenses.
 * Loaded from bundled conjugations.json asset.
 */
data class ConjugationVerb(
    val rank: Int,
    val infinitive: String,
    val german: String,
    val tenses: Map<String, List<String>>,  // tense name -> [6 person forms]
    val sentences: Map<String, Map<String, SentenceMatch>>? = null,  // tense -> person_idx -> sentence
    val auxiliary: String? = null  // "avoir" or "etre" — used for passé composé display
)

/**
 * A matched example sentence for a specific conjugation form.
 */
data class SentenceMatch(
    val fr: String,
    val de: String,
    val blank: String
)
