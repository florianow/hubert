package com.hubert.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NombrezQuestionGeneratorTest {

    @Test
    fun every_topic_produces_valid_questions() {
        val generator = NombrezQuestionGenerator(Random(42))
        NombrezTopic.entries.forEach { topic ->
            repeat(2000) {
                val q = generator.next(setOf(topic))
                assertEquals(topic, q.topic)
                if (q.isTyped) {
                    assertTrue("typed answer must be digits: ${q.answer}", q.answer.all { it.isDigit() })
                } else {
                    assertEquals("4 choices for ${q.prompt}: ${q.choices}", 4, q.choices.size)
                    assertEquals("choices must be distinct: ${q.choices}", 4, q.choices.toSet().size)
                    assertTrue("answer among choices: ${q.prompt}", q.answer in q.choices)
                }
            }
        }
    }

    @Test
    fun numbers_100_focus_on_seventy_to_ninety_nine() {
        val generator = NombrezQuestionGenerator(Random(7))
        val numbers = (1..1000).map { generator.next(setOf(NombrezTopic.NUMBERS_100)).answer.toInt() }
        assertTrue(numbers.all { it in 0..99 })
        assertTrue("70–99 should be over-represented", numbers.count { it >= 70 } > 500)
    }
}
