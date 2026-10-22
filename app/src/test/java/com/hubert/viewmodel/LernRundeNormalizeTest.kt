package com.hubert.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class LernRundeNormalizeTest {

    private fun n(text: String) = LernRundeViewModel.normalize(text)

    @Test
    fun strips_case_accents_and_spaces() {
        assertEquals("ete", n("  Été "))
        assertEquals("pomme de terre", n("pomme   de terre"))
    }

    @Test
    fun strips_a_separate_article_only() {
        assertEquals("maison", n("la maison"))
        assertEquals("homme", n("l'homme"))
        assertEquals("homme", n("L’homme"))
        assertEquals("enfants", n("les enfants"))
        assertEquals("lever", n("lever"))
        assertEquals("lequel", n("lequel"))
        assertEquals("laver", n("laver"))
        assertEquals("unique", n("unique"))
    }
}
