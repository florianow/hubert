package com.hubert.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClozeGameTest {

    private val sentence = "Le président s'est prononcé en faveur de la réforme."

    @Test
    fun splits_words_and_marks_target_as_star() {
        val game = ClozeGame.create(sentence, "prononcé")
        assertEquals(listOf("Le", "président", "s'", "est", "prononcé", "en", "faveur", "de", "la", "réforme"),
            game.slots.map { it.word })
        assertEquals(1, game.slots.count { it.isStar })
        assertEquals(1, game.tiles.count { it.isStar })
        assertEquals(10, game.tiles.size)
    }

    @Test
    fun tiles_are_sorted_alphabetically_ignoring_case() {
        val words = ClozeGame.create(sentence, null).tiles.map { it.word }
        assertEquals(listOf("de", "en", "est", "faveur", "la", "Le", "président", "prononcé", "réforme", "s'"), words)
    }

    @Test
    fun only_the_next_word_is_accepted() {
        var game = ClozeGame.create(sentence, "prononcé")
        val wrong = game.tiles.indexOfFirst { it.word == "la" }
        game = game.tapTile(wrong)
        assertEquals(wrong, game.errorTile)
        assertFalse(game.slots.first().revealed)

        game = game.tapTile(game.tiles.indexOfFirst { it.word == "Le" })
        assertTrue(game.slots.first().revealed)
        assertEquals(null, game.errorTile)
    }

    @Test
    fun full_order_finishes_and_star_needs_the_star_tile() {
        var game = ClozeGame.create(sentence, "prononcé")
        for (word in listOf("Le", "président", "s'", "est")) {
            game = game.tapTile(game.tiles.indexOfFirst { !it.used && !it.isStar && it.word == word })
        }
        // A normal tile can never stand in for the ★
        game = game.tapTile(game.tiles.indexOfFirst { it.word == "en" })
        assertNotNull(game.errorTile)
        game = game.tapTile(game.tiles.indexOfFirst { it.isStar })
        for (word in listOf("en", "faveur", "de", "la", "réforme")) {
            game = game.tapTile(game.tiles.indexOfFirst { !it.used && !it.isStar && it.word == word })
        }
        assertTrue(game.isFinished)
    }

    @Test
    fun lightning_leaves_only_the_star() {
        val game = ClozeGame.create(sentence, "prononcé").revealAllButStar()
        assertEquals(listOf(true), game.slots.filter { !it.revealed }.map { it.isStar })
        assertTrue(game.tapTile(game.tiles.indexOfFirst { it.isStar }).isFinished)
    }

    @Test
    fun counts_errors_and_hints_for_self_grading() {
        var game = ClozeGame.create(sentence, "prononcé")
        game = game.tapTile(game.tiles.indexOfFirst { it.word == "la" })          // wrong
        assertEquals(1, game.errors)
        game = game.tapSlot(game.segments.indexOfFirst { it is ClozeGame.Segment.Slot && it.word == "Le" })
        assertEquals(1, game.hints)
        assertFalse(game.starHinted)
        game = game.revealAllButStar()                                             // ⚡ is no hint
        assertEquals(1, game.hints)
        game = game.tapSlot(game.segments.indexOfFirst { it is ClozeGame.Segment.Slot && it.isStar })
        assertTrue(game.starHinted)
        assertTrue(game.isFinished)
    }

    @Test
    fun give_up_reveals_everything_and_marks_the_star() {
        val game = ClozeGame.create(sentence, "prononcé").revealAll()
        assertTrue(game.isFinished)
        assertTrue(game.starHinted)
    }

    @Test
    fun multi_word_target_and_duplicates() {
        val game = ClozeGame.create("Il a mis la table et la chaise.", "mis la table")
        assertEquals("mis la table", game.slots.single { it.isStar }.word)
        // The first "la" is inside the ★ phrase, the second one is a normal tile
        assertEquals(listOf("Il", "a", "mis la table", "et", "la", "chaise"), game.slots.map { it.word })
        assertEquals(1, game.tiles.count { it.word == "la" })
    }
}
