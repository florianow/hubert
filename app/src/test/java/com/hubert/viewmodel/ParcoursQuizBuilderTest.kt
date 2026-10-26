package com.hubert.viewmodel

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hubert.data.repository.ParcoursUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class ParcoursQuizBuilderTest {

    private val units: List<ParcoursUnit> by lazy {
        val json = File("src/main/assets/parcours.json").readText()
        Gson().fromJson(json, object : TypeToken<List<ParcoursUnit>>() {}.type)
    }

    @Test
    fun every_etape_fills_every_quiz() {
        units.forEach { unit ->
            QuizKind.entries.forEach { kind ->
                repeat(5) { seed ->
                    val items = ParcoursQuizBuilder.build(unit, kind, Random(seed))
                    assertEquals("${unit.title} ${kind.title}", kind.size, items.size)
                }
            }
        }
    }

    @Test
    fun every_question_is_well_formed() {
        units.forEach { unit ->
            repeat(20) { seed ->
                ParcoursQuizBuilder.build(unit, QuizKind.CONTROL, Random(seed)).forEach { item ->
                    if (item.isTyped) {
                        assertTrue("typed answers are digits: $item", item.answer.all { it.isDigit() })
                    } else {
                        assertTrue("answer among the options: $item", item.answer in item.options)
                        assertTrue("3–4 distinct options: $item", item.options.size in 3..4 && item.options.toSet().size == item.options.size)
                    }
                }
            }
        }
    }

    @Test
    fun exercises_have_one_gap_and_a_right_answer_not_among_the_wrong_ones() {
        units.flatMap { u -> u.exercises.orEmpty().map { u to it } }.forEach { (unit, e) ->
            assertEquals("${unit.title}: ${e.fr}", 1, Regex("___").findAll(e.fr).count())
            assertFalse("${unit.title}: ${e.fr}", e.answer in e.wrong)
            assertTrue("${unit.title}: ${e.fr}", e.wrong.size >= 2)
        }
    }

    @Test
    fun the_mini_check_starts_with_the_lesson_sentences() {
        val unit = units.first { it.exercises.orEmpty().count { e -> e.source == "lesson" } >= QuizKind.CHECK.size }
        val lesson = unit.exercises.orEmpty().filter { it.source == "lesson" }.map { it.fr }.toSet()
        val items = ParcoursQuizBuilder.build(unit, QuizKind.CHECK, Random(3))
        assertTrue(items.all { it.prompt in lesson })
    }

    @Test
    fun typed_numbers_are_checked_like_in_nombrez() {
        val item = QuizItem("", "", "", "1990", emptyList(), "", null)
        assertTrue(ParcoursQuizBuilder.isRight(item, "1 990"))
        assertTrue(ParcoursQuizBuilder.isRight(item, "01990"))
        assertFalse(ParcoursQuizBuilder.isRight(item, "1999"))
        assertFalse(ParcoursQuizBuilder.isRight(item, ""))
    }
}
