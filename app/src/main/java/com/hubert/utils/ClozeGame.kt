package com.hubert.utils

import java.text.Collator
import java.util.Locale

/**
 * The anki_french "Lückenspiel": rebuild a hidden sentence by tapping word tiles in order.
 * - every word is a hidden slot; the target word (if any) is one ★ slot/tile
 * - tiles are sorted alphabetically (German collation, case-insensitive), ★ appears once
 * - a tile is accepted only if it is the next hidden word; otherwise it is marked as error
 * - tapping a hidden slot reveals it as a hint; "lightning" reveals all but the ★
 * Immutable: every action returns the next state.
 */
data class ClozeGame(
    val segments: List<Segment>,
    val tiles: List<Tile>,
    val errorTile: Int? = null
) {
    sealed interface Segment {
        data class Text(val text: String) : Segment
        data class Slot(val word: String, val isStar: Boolean, val revealed: Boolean = false) : Segment
    }

    data class Tile(val word: String, val isStar: Boolean, val used: Boolean = false)

    val slots: List<Segment.Slot> get() = segments.filterIsInstance<Segment.Slot>()
    val isFinished: Boolean get() = slots.all { it.revealed }
    val hasStar: Boolean get() = slots.any { it.isStar }

    private fun firstHidden(): Int = segments.indexOfFirst { it is Segment.Slot && !it.revealed }

    private fun matches(tile: Tile, slot: Segment.Slot) =
        if (slot.isStar) tile.isStar else !tile.isStar && tile.word == slot.word

    /** Tap on a word tile. */
    fun tapTile(index: Int): ClozeGame {
        val tile = tiles.getOrNull(index) ?: return this
        if (tile.used) return this
        val next = firstHidden().takeIf { it >= 0 } ?: return this
        val slot = segments[next] as Segment.Slot
        if (!matches(tile, slot)) return copy(errorTile = index)
        return reveal(next).useTile(index)
    }

    /** Tap on a hidden slot: reveal it as a hint (and use up its tile). */
    fun tapSlot(segmentIndex: Int): ClozeGame {
        val slot = segments.getOrNull(segmentIndex) as? Segment.Slot ?: return this
        if (slot.revealed) return this
        val tileIndex = tiles.indexOfFirst { !it.used && matches(it, slot) }
        val revealed = reveal(segmentIndex)
        return if (tileIndex >= 0) revealed.useTile(tileIndex) else revealed
    }

    /** ⚡: reveal everything except the target word. */
    fun revealAllButStar(): ClozeGame {
        var game = this
        segments.forEachIndexed { i, s -> if (s is Segment.Slot && !s.isStar && !s.revealed) game = game.tapSlot(i) }
        return game
    }

    private fun reveal(index: Int): ClozeGame {
        val slot = segments[index] as Segment.Slot
        // All ★ slots open together (the target can appear twice)
        val updated = segments.mapIndexed { i, s ->
            if (s is Segment.Slot && (i == index || (slot.isStar && s.isStar))) s.copy(revealed = true) else s
        }
        return copy(segments = updated, errorTile = null)
    }

    private fun useTile(index: Int) = copy(tiles = tiles.mapIndexed { i, t -> if (i == index) t.copy(used = true) else t })

    companion object {
        private val WORD = Regex("[\\p{L}0-9\\-()/]+['’]?")

        /** Build a game for [sentence]; [target] (word or phrase) becomes the ★. */
        fun create(sentence: String, target: String?): ClozeGame {
            val matches = WORD.findAll(sentence).toList()
            val targetTokens = target?.let { t -> WORD.findAll(t).map { it.value.lowercase() }.toList() }.orEmpty()
            val starStart = if (targetTokens.isEmpty()) -1 else
                (0..matches.size - targetTokens.size).firstOrNull { i ->
                    targetTokens.indices.all { k -> matches[i + k].value.lowercase() == targetTokens[k] }
                } ?: -1

            val segments = mutableListOf<Segment>()
            var pos = 0
            var i = 0
            while (i < matches.size) {
                val m = matches[i]
                if (m.range.first > pos) segments += Segment.Text(sentence.substring(pos, m.range.first))
                if (i == starStart) {
                    val last = matches[i + targetTokens.size - 1]
                    segments += Segment.Slot(sentence.substring(m.range.first, last.range.last + 1), isStar = true)
                    pos = last.range.last + 1
                    i += targetTokens.size
                } else {
                    segments += Segment.Slot(m.value, isStar = false)
                    pos = m.range.last + 1
                    i++
                }
            }
            if (pos < sentence.length) segments += Segment.Text(sentence.substring(pos))

            val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
            val slots = segments.filterIsInstance<Segment.Slot>()
            val tiles = slots.filter { !it.isStar }.map { Tile(it.word, isStar = false) }
                .sortedWith { a, b -> collator.compare(a.word, b.word) }
                .toMutableList()
            if (slots.any { it.isStar }) {
                // ★ sorts like its hidden word, as in the Anki template
                val star = slots.first { it.isStar }
                val at = tiles.indexOfFirst { collator.compare(it.word, star.word) > 0 }.let { if (it < 0) tiles.size else it }
                tiles.add(at, Tile(star.word, isStar = true))
            }
            return ClozeGame(segments, tiles)
        }
    }
}
