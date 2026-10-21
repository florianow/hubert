package com.hubert.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AccordezQuestionGeneratorTest {

    private fun questions(group: AccordGroup, n: Int = 3000): List<AccordQuestion> {
        val generator = AccordezQuestionGenerator(Random(1))
        return (1..n).map { generator.next(setOf(group)) }
    }

    @Test
    fun every_question_is_well_formed() {
        AccordGroup.entries.forEach { group ->
            questions(group).forEach { q ->
                assertEquals(q.sentence, 1, Regex("___").findAll(q.sentence).count())
                assertTrue("answer in choices: $q", q.answer in q.choices)
                assertTrue("3–4 distinct choices: $q", q.choices.size in 3..4 && q.choices.toSet().size == q.choices.size)
                assertEquals(group, q.rule.group)
            }
        }
    }

    private fun find(group: AccordGroup, sentence: String): AccordQuestion =
        questions(group, 20_000).first { it.sentence == sentence }

    @Test
    fun etre_agrees_with_subject() {
        assertEquals("partie", find(AccordGroup.ETRE, "Marie est ___ très tôt.").answer)
        assertEquals("arrivés", find(AccordGroup.ETRE, "Julie et Marc sont ___ en retard.").answer)
        assertEquals("venue", find(AccordGroup.ETRE, "Madame, vous êtes ___ à la fête.").answer)
        assertEquals("nées", find(AccordGroup.ETRE, "Les filles sont ___ en 1995.").answer)
    }

    @Test
    fun avoir_agrees_only_with_preceding_object() {
        assertEquals("écrit", find(AccordGroup.AVOIR, "Nous avons ___ la lettre.").answer)
        assertEquals("écrite", find(AccordGroup.AVOIR, "Voici la lettre que j'ai ___.").answer)
        assertEquals("prises", find(AccordGroup.AVOIR, "Les photos ? Elle les a ___ hier.").answer)
        assertEquals("prises", find(AccordGroup.AVOIR, "Voici les photos qu'il a ___.").answer)
        assertEquals("faits", find(AccordGroup.AVOIR, "Les gâteaux ? Tu les as ___ hier.").answer)
    }

    @Test
    fun reflexive_rules() {
        assertEquals("levée", find(AccordGroup.REFLEXIVE, "Marie s'est ___ tôt.").answer)
        assertEquals("lavé", find(AccordGroup.REFLEXIVE, "Marie s'est ___ les mains.").answer)
        assertEquals("parlé", find(AccordGroup.REFLEXIVE, "Les filles se sont ___ au téléphone.").answer)
        assertEquals("amusées", find(AccordGroup.REFLEXIVE, "Lucie et moi, nous nous sommes ___ à la fête.").answer)
    }
}
