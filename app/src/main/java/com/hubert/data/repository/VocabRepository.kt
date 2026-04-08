package com.hubert.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hubert.data.model.ConjugationVerb
import com.hubert.data.model.SentenceEntry
import com.hubert.data.model.VocabWord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads vocabulary words, thematic categories, and example sentences
 * from bundled JSON asset files.
 */
@Singleton
class VocabRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private var cachedWords: List<VocabWord>? = null
    private var cachedCategories: Map<String, List<Int>>? = null
    private var cachedSentences: Map<String, List<SentenceEntry>>? = null
    private var cachedConjugations: List<ConjugationVerb>? = null

    // Indexes built on first access
    private var nounsByGender: Map<String, List<VocabWord>>? = null
    private var wordsByRank: Map<Int, VocabWord>? = null
    private var wordsByCategory: Map<String, List<VocabWord>>? = null
    private var ipaByFrench: Map<String, String>? = null

    // ---------------------------------------------------------------
    // Basic word access
    // ---------------------------------------------------------------

    fun getAllWords(): List<VocabWord> {
        cachedWords?.let { return it }
        val json = context.assets.open("vocab.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<VocabWord>>() {}.type
        val words: List<VocabWord> = gson.fromJson(json, type)
        cachedWords = words
        return words
    }

    fun getRandomWords(count: Int): List<VocabWord> {
        return getAllWords().shuffled().take(count)
    }

    fun getWordByRank(rank: Int): VocabWord? {
        if (wordsByRank == null) {
            wordsByRank = getAllWords().associateBy { it.rank }
        }
        return wordsByRank!![rank]
    }

    /**
     * Look up the IPA transcription for a French word (e.g., verb infinitive).
     */
    fun getIpaForFrench(french: String): String? {
        if (ipaByFrench == null) {
            ipaByFrench = getAllWords()
                .filter { it.ipa != null }
                .associateBy({ it.french }, { it.ipa!! })
        }
        return ipaByFrench!![french]
    }

    // ---------------------------------------------------------------
    // Gender Snap: nouns with known gender
    // ---------------------------------------------------------------

    fun getNouns(): List<VocabWord> {
        // Only return pure nouns (pos is exactly "nm" or "nf") so that
        // words like "être", "je", "bien" etc. are excluded from Gender Snap.
        return getAllWords().filter {
            it.gender != null && it.pos in listOf("nm", "nf")
        }
    }

    fun getRandomNouns(count: Int): List<VocabWord> {
        return getNouns().shuffled().take(count)
    }

    fun getMasculineNouns(): List<VocabWord> {
        if (nounsByGender == null) buildGenderIndex()
        return nounsByGender!!["m"] ?: emptyList()
    }

    fun getFeminineNouns(): List<VocabWord> {
        if (nounsByGender == null) buildGenderIndex()
        return nounsByGender!!["f"] ?: emptyList()
    }

    private fun buildGenderIndex() {
        nounsByGender = getNouns().groupBy { it.gender!! }
    }

    // ---------------------------------------------------------------
    // Categories (for Gap Fill distractors)
    // ---------------------------------------------------------------

    fun getCategories(): Map<String, List<Int>> {
        cachedCategories?.let { return it }
        val json = context.assets.open("categories.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, List<Int>>>() {}.type
        val cats: Map<String, List<Int>> = gson.fromJson(json, type)
        cachedCategories = cats
        return cats
    }

    /**
     * Get all words belonging to a specific category.
     */
    fun getWordsInCategory(category: String): List<VocabWord> {
        if (wordsByCategory == null) buildCategoryIndex()
        return wordsByCategory!![category] ?: emptyList()
    }

    /**
     * Get distractor words from the same category as the target word.
     * Returns words that are NOT the target.
     */
    fun getCategoryDistractors(targetRank: Int, count: Int): List<VocabWord> {
        val target = getWordByRank(targetRank) ?: return getRandomWords(count)
        val cats = target.categories
        if (cats.isNullOrEmpty()) return getRandomWords(count)

        // Collect all words from the same categories, excluding the target
        val pool = cats.flatMap { getWordsInCategory(it) }
            .filter { it.rank != targetRank }
            .distinctBy { it.rank }

        return if (pool.size >= count) {
            pool.shuffled().take(count)
        } else {
            // Fill remaining with random words
            val extra = getRandomWords(count - pool.size)
                .filter { it.rank != targetRank && it.rank !in pool.map { p -> p.rank } }
            (pool + extra).shuffled().take(count)
        }
    }

    private fun buildCategoryIndex() {
        val categories = getCategories()
        val index = mutableMapOf<String, MutableList<VocabWord>>()
        for ((catName, ranks) in categories) {
            val words = ranks.mapNotNull { getWordByRank(it) }
            index[catName] = words.toMutableList()
        }
        wordsByCategory = index
    }

    // ---------------------------------------------------------------
    // Sentences (for Gap Fill)
    // ---------------------------------------------------------------

    fun getSentences(): Map<String, List<SentenceEntry>> {
        cachedSentences?.let { return it }
        val json = context.assets.open("sentences.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, List<SentenceEntry>>>() {}.type
        val sents: Map<String, List<SentenceEntry>> = gson.fromJson(json, type)
        cachedSentences = sents
        return sents
    }

    /**
     * Get a random sentence for a given word rank.
     */
    fun getRandomSentence(rank: Int): SentenceEntry? {
        val sents = getSentences()[rank.toString()] ?: return null
        return sents.randomOrNull()
    }

    /**
     * Get all words that have example sentences available.
     */
    fun getWordsWithSentences(): List<VocabWord> {
        val sentenceRanks = getSentences().keys.map { it.toInt() }.toSet()
        return getAllWords().filter { it.rank in sentenceRanks }
    }

    // ---------------------------------------------------------------
    // Pronunciation game: sentences grouped by difficulty
    // ---------------------------------------------------------------

    /**
     * Get all sentences as a flat list of (rank, SentenceEntry) pairs.
     */
    fun getAllSentencesFlat(): List<Pair<Int, SentenceEntry>> {
        return getSentences().flatMap { (rankStr, entries) ->
            val rank = rankStr.toIntOrNull() ?: return@flatMap emptyList()
            entries.map { rank to it }
        }
    }

    /**
     * Get sentences filtered by word count.
     * @param minWords minimum word count (inclusive)
     * @param maxWords maximum word count (inclusive)
     */
    fun getSentencesByWordCount(minWords: Int, maxWords: Int): List<Pair<Int, SentenceEntry>> {
        return getAllSentencesFlat().filter { (_, entry) ->
            val wordCount = entry.fr.split("\\s+".toRegex()).size
            wordCount in minWords..maxWords
        }
    }

    // ---------------------------------------------------------------
    // Conjugations (for Conjuguez! game)
    // ---------------------------------------------------------------

    fun getConjugations(): List<ConjugationVerb> {
        cachedConjugations?.let { return it }
        val json = context.assets.open("conjugations.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<ConjugationVerb>>() {}.type
        val verbs: List<ConjugationVerb> = gson.fromJson(json, type)
        cachedConjugations = verbs
        return verbs
    }
}
