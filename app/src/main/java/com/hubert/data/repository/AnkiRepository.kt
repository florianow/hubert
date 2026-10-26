package com.hubert.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Learning state of one word in the player's Anki deck. */
data class AnkiCard(
    val rank: Int,
    val type: Int,            // 0 new, 1 learning, 2 review, 3 relearning
    val intervalDays: Int     // review interval (days); 0 while new/learning
) {
    val isStarted: Boolean get() = type != 0
}

data class AnkiDeck(val id: Long, val name: String)

/**
 * Read-only access to AnkiDroid's public ContentProvider (FlashCardsContract). Hubert never
 * writes to Anki: Anki stays the place for deep learning, Hubert only adapts its own
 * practice to what the player is learning there.
 */
@Singleton
class AnkiRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ANKI_PACKAGE = "com.ichi2.anki"
        const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private val AUTHORITY_URI: Uri = Uri.parse("content://com.ichi2.anki.flashcards")
        private val NOTES_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")
        private val CARDS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "cards")
        private val DECKS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")
        private val SCHEDULE_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "schedule")
        private const val MAX_QUEUE = 2000
        private const val MAX_NEED = 3
        private const val FIELD_SEPARATOR = '\u001f'
    }

    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(ANKI_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun decks(): List<AnkiDeck> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AnkiDeck>()
        context.contentResolver.query(DECKS_URI, arrayOf("deck_id", "deck_name"), null, null, null)?.use { c ->
            while (c.moveToNext()) result += AnkiDeck(c.getLong(0), c.getString(1))
        }
        result.sortedBy { it.name }
    }

    /** The French deck if there is an obvious one ("Französisch 5000", "French …"). */
    fun guessDeck(decks: List<AnkiDeck>): AnkiDeck? =
        decks.firstOrNull { it.name.contains("franz", ignoreCase = true) || it.name.contains("french", ignoreCase = true) }

    /**
     * All cards of [deckName], mapped to Hubert's vocab ranks. The anki_french notes carry
     * the frequency rank as a numeric field ("Rang"); [rankByFrench] is the fallback for
     * modified notes.
     */
    suspend fun cards(deckName: String, rankByFrench: Map<String, Int>): List<AnkiCard> = withContext(Dispatchers.IO) {
        val search = "\"deck:$deckName\""
        val rankByNote = rankByNote(deckName, rankByFrench)
        val cards = mutableMapOf<Int, AnkiCard>()
        context.contentResolver.query(CARDS_URI, arrayOf("note_id", "type", "interval"), search, null, null)?.use { c ->
            while (c.moveToNext()) {
                val rank = rankByNote[c.getLong(0)] ?: continue
                val card = AnkiCard(rank, c.getInt(1), c.getInt(2).coerceAtLeast(0))
                // Several card templates per note: keep the most advanced one
                val existing = cards[rank]
                if (existing == null || card.intervalDays > existing.intervalDays || (!existing.isStarted && card.isStarted)) {
                    cards[rank] = card
                }
            }
        }
        cards.values.toList()
    }

    /**
     * Today's queue of [deckName] as Anki would show it (new, learning and review cards within
     * the deck's daily limits), mapped to ranks in queue order. Read-only: AnkiDroid selects the
     * deck for the query and restores the previous selection afterwards.
     */
    suspend fun queuedRanks(deckName: String, rankByFrench: Map<String, Int>): List<Int> = withContext(Dispatchers.IO) {
        val deck = decks().firstOrNull { it.name == deckName } ?: return@withContext emptyList()
        val rankByNote = rankByNote(deckName, rankByFrench)
        val ranks = mutableListOf<Int>()
        context.contentResolver.query(
            SCHEDULE_URI, arrayOf("note_id"), "limit=?,deckID=?", arrayOf(MAX_QUEUE.toString(), deck.id.toString()), null
        )?.use { c ->
            while (c.moveToNext()) rankByNote[c.getLong(0)]?.let { ranks += it }
        }
        ranks.distinct()
    }

    /**
     * Words of [deckName] already answered in Anki today ("rated:1", Anki's day). They left the
     * queue there, but still belong to today's words when Hubert takes its snapshot afterwards.
     */
    suspend fun reviewedTodayRanks(deckName: String, rankByFrench: Map<String, Int>): List<Int> = withContext(Dispatchers.IO) {
        val rankByNote = rankByNote(deckName, rankByFrench)
        val ranks = mutableListOf<Int>()
        context.contentResolver.query(CARDS_URI, arrayOf("note_id"), "\"deck:$deckName\" rated:1", null, null)?.use { c ->
            while (c.moveToNext()) rankByNote[c.getLong(0)]?.let { ranks += it }
        }
        ranks.distinct()
    }

    /**
     * How often each of [ranks] comes up in Anki today: new and learning cards once per learning
     * step, relearning cards once per relearning step, review cards once. Hubert asks for the
     * same number of right answers before a word of today's queue counts as done.
     */
    suspend fun todayNeeds(deckName: String, rankByFrench: Map<String, Int>, ranks: Collection<Int>): Map<Int, Int> = withContext(Dispatchers.IO) {
        val (newSteps, lapseSteps) = learningSteps(deckName)
        val rankByNote = rankByNote(deckName, rankByFrench)
        val types = mutableMapOf<Int, MutableSet<Int>>()
        context.contentResolver.query(
            CARDS_URI, arrayOf("note_id", "type"), "\"deck:$deckName\" (is:due OR is:learn OR rated:1)", null, null
        )?.use { c ->
            while (c.moveToNext()) rankByNote[c.getLong(0)]?.let { types.getOrPut(it) { mutableSetOf() } += c.getInt(1) }
        }
        val introducedToday = mutableSetOf<Int>()
        context.contentResolver.query(CARDS_URI, arrayOf("note_id"), "\"deck:$deckName\" introduced:1", null, null)?.use { c ->
            while (c.moveToNext()) rankByNote[c.getLong(0)]?.let { introducedToday += it }
        }
        ranks.associateWith { rank ->
            val cardTypes = types[rank]
            val need = when {
                rank in introducedToday -> newSteps
                cardTypes == null -> newSteps        // only in the queue as a new card
                0 in cardTypes || 1 in cardTypes -> newSteps
                3 in cardTypes -> lapseSteps
                else -> 1
            }
            need.coerceIn(1, MAX_NEED)
        }
    }

    /** Learning and relearning step counts from the deck's options (Anki defaults: 2 and 1). */
    private fun learningSteps(deckName: String): Pair<Int, Int> {
        val options = runCatching {
            context.contentResolver.query(DECKS_URI, arrayOf("deck_name", "options"), null, null, null)?.use { c ->
                var json: String? = null
                while (c.moveToNext()) if (c.getString(0) == deckName) json = c.getString(1)
                json
            }
        }.getOrNull() ?: return 2 to 1
        return runCatching {
            val o = JSONObject(options)
            o.getJSONObject("new").getJSONArray("delays").length() to o.getJSONObject("lapse").getJSONArray("delays").length()
        }.getOrDefault(2 to 1)
    }

    /** Note id → Hubert rank for all notes of [deckName]. */
    private fun rankByNote(deckName: String, rankByFrench: Map<String, Int>): Map<Long, Int> {
        val rankByNote = mutableMapOf<Long, Int>()
        context.contentResolver.query(NOTES_URI, arrayOf("_id", "flds"), "\"deck:$deckName\"", null, null)?.use { c ->
            while (c.moveToNext()) {
                val fields = c.getString(1).split(FIELD_SEPARATOR).map { stripHtml(it) }
                val rank = fields.firstOrNull { f -> f.isNotEmpty() && f.all { it.isDigit() } && f.length <= 4 }?.toIntOrNull()
                    ?: fields.firstNotNullOfOrNull { rankByFrench[it] }
                if (rank != null) rankByNote[c.getLong(0)] = rank
            }
        }
        return rankByNote
    }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()
}
