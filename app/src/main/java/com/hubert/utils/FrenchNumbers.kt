package com.hubert.utils

/**
 * French number words (traditional spelling: hyphens only below 100, "et" for 21/31/…/71).
 *
 * Rules covered: vingt et un · soixante et onze · soixante-dix-sept · quatre-vingts (plural s
 * only when nothing follows) · quatre-vingt-un · deux cents / deux cent un · mille (never
 * plural) · deux cent mille / quatre-vingt mille (no plural s before mille) · un million /
 * deux millions.
 */
object FrenchNumbers {

    private val UNITS = listOf(
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize"
    )
    private val TENS = mapOf(2 to "vingt", 3 to "trente", 4 to "quarante", 5 to "cinquante", 6 to "soixante")

    val DAYS = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
    val DAYS_DE = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    val MONTHS = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre"
    )
    val MONTHS_DE = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )

    fun toWords(n: Int): String {
        require(n in 0..999_999_999) { "unsupported number $n" }
        if (n < 1000) return below1000(n, plural = true)
        if (n < 1_000_000) {
            val thousands = n / 1000
            val rest = n % 1000
            val head = if (thousands == 1) "mille" else below1000(thousands, plural = false) + " mille"
            return if (rest == 0) head else "$head ${below1000(rest, plural = true)}"
        }
        val millions = n / 1_000_000
        val rest = n % 1_000_000
        val head = if (millions == 1) "un million" else below1000(millions, plural = true) + " millions"
        return if (rest == 0) head else "$head ${toWords(rest)}"
    }

    /** [plural] = false before "mille": deux cent mille, quatre-vingt mille. */
    private fun below1000(n: Int, plural: Boolean): String {
        val hundreds = n / 100
        val rest = n % 100
        return when {
            hundreds == 0 -> below100(rest, plural)
            hundreds == 1 -> if (rest == 0) "cent" else "cent ${below100(rest, plural)}"
            rest == 0 -> UNITS[hundreds] + if (plural) " cents" else " cent"
            else -> "${UNITS[hundreds]} cent ${below100(rest, plural)}"
        }
    }

    private fun below100(n: Int, plural: Boolean): String = when {
        n <= 16 -> UNITS[n]
        n < 20 -> "dix-${UNITS[n - 10]}"
        n < 70 -> {
            val tens = TENS.getValue(n / 10)
            when (n % 10) {
                0 -> tens
                1 -> "$tens et un"
                else -> "$tens-${UNITS[n % 10]}"
            }
        }
        n < 80 -> if (n == 71) "soixante et onze" else "soixante-${below100(n - 60, plural)}"
        n == 80 -> if (plural) "quatre-vingts" else "quatre-vingt"
        else -> "quatre-vingt-${below100(n - 80, plural)}"
    }

    /** "le premier mai", "le quinze août" — dates use cardinals except for the 1st. */
    fun dateWords(day: Int, month: Int): String =
        "le ${if (day == 1) "premier" else toWords(day)} ${MONTHS[month - 1]}"

    /** Official 24h time: "quatorze heures trente-sept", "zéro heure cinq", "une heure". */
    fun time24Words(hour: Int, minute: Int): String {
        val h = when (hour) {
            0 -> "zéro heure"
            1 -> "une heure"
            21 -> "vingt et une heures"
            else -> "${toWords(hour)} heures"
        }
        return if (minute == 0) h else "$h ${minuteWords(minute)}"
    }

    /**
     * Everyday 12h time for minutes in steps of 5: "trois heures moins le quart",
     * "deux heures et demie", "une heure vingt-cinq". [hour] in 1..12; 12 is "midi".
     */
    fun timeColloquialWords(hour: Int, minute: Int): String {
        require(hour in 1..12 && minute % 5 == 0 && minute in 0..55)
        fun hourWords(h: Int): String = when (h) {
            12, 0 -> "midi"
            13 -> "une heure"
            1 -> "une heure"
            else -> "${toWords(h)} heures"
        }
        return when {
            minute == 0 -> hourWords(hour)
            minute == 15 -> "${hourWords(hour)} et quart"
            minute == 30 -> if (hour == 12) "midi et demi" else "${hourWords(hour)} et demie"
            minute < 30 -> "${hourWords(hour)} ${minuteWords(minute)}"
            minute == 45 -> "${hourWords(hour + 1)} moins le quart"
            else -> "${hourWords(hour + 1)} moins ${minuteWords(60 - minute)}"
        }
    }

    /** Minutes use "une" (feminine "minute" implied): vingt et une, trente et une. */
    private fun minuteWords(minute: Int): String =
        if (minute % 10 == 1 && minute in 21..51) toWords(minute).replace(" et un", " et une") else toWords(minute)
}
