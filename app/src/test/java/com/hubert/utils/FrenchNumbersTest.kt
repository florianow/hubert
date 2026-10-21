package com.hubert.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FrenchNumbersTest {

    private fun check(n: Int, expected: String) = assertEquals("$n", expected, FrenchNumbers.toWords(n))

    @Test
    fun units_and_teens() {
        check(0, "zéro")
        check(1, "un")
        check(16, "seize")
        check(17, "dix-sept")
        check(19, "dix-neuf")
    }

    @Test
    fun tens_with_et_un() {
        check(20, "vingt")
        check(21, "vingt et un")
        check(22, "vingt-deux")
        check(61, "soixante et un")
        check(69, "soixante-neuf")
    }

    @Test
    fun seventies_eighties_nineties() {
        check(70, "soixante-dix")
        check(71, "soixante et onze")
        check(72, "soixante-douze")
        check(77, "soixante-dix-sept")
        check(80, "quatre-vingts")
        check(81, "quatre-vingt-un")
        check(90, "quatre-vingt-dix")
        check(91, "quatre-vingt-onze")
        check(97, "quatre-vingt-dix-sept")
        check(99, "quatre-vingt-dix-neuf")
    }

    @Test
    fun hundreds() {
        check(100, "cent")
        check(101, "cent un")
        check(180, "cent quatre-vingts")
        check(200, "deux cents")
        check(201, "deux cent un")
        check(999, "neuf cent quatre-vingt-dix-neuf")
    }

    @Test
    fun thousands_and_millions() {
        check(1000, "mille")
        check(1001, "mille un")
        check(1990, "mille neuf cent quatre-vingt-dix")
        check(2000, "deux mille")
        check(2024, "deux mille vingt-quatre")
        check(80_000, "quatre-vingt mille")
        check(200_000, "deux cent mille")
        check(250_300, "deux cent cinquante mille trois cents")
        check(1_000_000, "un million")
        check(2_500_000, "deux millions cinq cent mille")
    }

    @Test
    fun dates() {
        assertEquals("le premier mai", FrenchNumbers.dateWords(1, 5))
        assertEquals("le quinze août", FrenchNumbers.dateWords(15, 8))
        assertEquals("le trente et un décembre", FrenchNumbers.dateWords(31, 12))
    }

    @Test
    fun times() {
        assertEquals("quatorze heures trente-sept", FrenchNumbers.time24Words(14, 37))
        assertEquals("zéro heure cinq", FrenchNumbers.time24Words(0, 5))
        assertEquals("une heure", FrenchNumbers.time24Words(1, 0))
        assertEquals("vingt et une heures vingt et une", FrenchNumbers.time24Words(21, 21))
        assertEquals("trois heures moins le quart", FrenchNumbers.timeColloquialWords(2, 45))
        assertEquals("deux heures et demie", FrenchNumbers.timeColloquialWords(2, 30))
        assertEquals("deux heures et quart", FrenchNumbers.timeColloquialWords(2, 15))
        assertEquals("une heure vingt-cinq", FrenchNumbers.timeColloquialWords(1, 25))
        assertEquals("midi moins dix", FrenchNumbers.timeColloquialWords(11, 50))
        assertEquals("midi et demi", FrenchNumbers.timeColloquialWords(12, 30))
        assertEquals("une heure moins vingt", FrenchNumbers.timeColloquialWords(12, 40))
    }
}
