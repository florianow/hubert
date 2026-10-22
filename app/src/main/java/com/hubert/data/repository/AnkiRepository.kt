package com.hubert.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val rankByNote = mutableMapOf<Long, Int>()
        context.contentResolver.query(NOTES_URI, arrayOf("_id", "flds"), search, null, null)?.use { c ->
            while (c.moveToNext()) {
                val fields = c.getString(1).split(FIELD_SEPARATOR).map { stripHtml(it) }
                val rank = fields.firstOrNull { f -> f.isNotEmpty() && f.all { it.isDigit() } && f.length <= 4 }?.toIntOrNull()
                    ?: fields.firstNotNullOfOrNull { rankByFrench[it] }
                if (rank != null) rankByNote[c.getLong(0)] = rank
            }
        }
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

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()
}
