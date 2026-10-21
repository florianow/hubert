package com.hubert.viewmodel

import com.hubert.utils.FrenchNumbers
import kotlin.random.Random

/** Topics on the Nombrez! selection screen. */
enum class NombrezTopic(val label: String, val description: String) {
    NUMBERS_100("Zahlen bis 100", "hören → tippen · Fokus 70–99"),
    NUMBERS_1000("Zahlen bis 1000", "hören → tippen"),
    NUMBERS_BIG("Große Zahlen & Jahre", "1990, 250 000 … hören → tippen"),
    TIME("Uhrzeit", "« moins le quart » → 2:45"),
    DATES("Datum", "« le quinze août » → 15.08."),
    DAYS_MONTHS("Tage & Monate", "Übersetzung · was kommt danach?")
}

/**
 * One Nombrez! question. Typed questions ([choices] empty) expect the digits in [answer];
 * choice questions expect the choice equal to [answer]. Audio-only questions hide [prompt]
 * until answered so the player has to listen.
 */
data class NombrezQuestion(
    val topic: NombrezTopic,
    val instruction: String,
    val prompt: String,
    val speech: String?,
    val audioOnly: Boolean,
    val choices: List<String>,
    val answer: String,
    val reveal: String                 // shown after answering, e.g. the written French form
) {
    val isTyped: Boolean get() = choices.isEmpty()
}

/** Generates questions for the selected topics; everything is computed, no data file needed. */
class NombrezQuestionGenerator(private val random: Random = Random.Default) {

    fun next(topics: Set<NombrezTopic>): NombrezQuestion = when (topics.random(random)) {
        NombrezTopic.NUMBERS_100 -> typedNumber(
            NombrezTopic.NUMBERS_100,
            // Half of the questions from the notorious 70–99 range
            if (random.nextBoolean()) random.nextInt(70, 100) else random.nextInt(0, 100)
        )
        NombrezTopic.NUMBERS_1000 -> typedNumber(NombrezTopic.NUMBERS_1000, random.nextInt(100, 1000))
        NombrezTopic.NUMBERS_BIG -> typedNumber(
            NombrezTopic.NUMBERS_BIG,
            when (random.nextInt(3)) {
                0 -> random.nextInt(1900, 2031)                 // years
                1 -> random.nextInt(1000, 100_000)
                else -> random.nextInt(100, 1000) * 1000 + random.nextInt(0, 1000)
            }
        )
        NombrezTopic.TIME -> if (random.nextBoolean()) colloquialTime() else officialTime()
        NombrezTopic.DATES -> date()
        NombrezTopic.DAYS_MONTHS -> when (random.nextInt(3)) {
            0 -> translateDayOrMonth()
            1 -> nextDay()
            else -> nextMonth()
        }
    }

    private fun typedNumber(topic: NombrezTopic, n: Int): NombrezQuestion {
        val words = FrenchNumbers.toWords(n)
        return NombrezQuestion(
            topic = topic,
            instruction = "Hör zu und tippe die Zahl",
            prompt = words,
            speech = words,
            audioOnly = true,
            choices = emptyList(),
            answer = n.toString(),
            reveal = "$n · $words"
        )
    }

    /** "trois heures moins le quart" → pick 2:45 (classic traps: hour ±1, et ↔ moins). */
    private fun colloquialTime(): NombrezQuestion {
        val hour = random.nextInt(1, 12)
        val minute = random.nextInt(0, 12) * 5
        fun fmt(h: Int, m: Int) = "${((h - 1).mod(12)) + 1}:${m.toString().padStart(2, '0')}"
        val correct = fmt(hour, minute)
        val traps = listOf(
            fmt(hour + 1, minute),
            fmt(hour - 1, minute),
            fmt(hour, (60 - minute) % 60),
            fmt(hour + 1, (60 - minute) % 60),
            fmt(hour, (minute + 30) % 60)
        )
        val words = FrenchNumbers.timeColloquialWords(hour, minute)
        return choiceQuestion(
            NombrezTopic.TIME, "Wie spät ist es?", words, words, audioOnly = false,
            correct = correct, traps = traps, reveal = "$correct · $words"
        )
    }

    /** "quatorze heures trente-sept" → pick 14:37 (traps: quatre/quatorze/quarante confusions). */
    private fun officialTime(): NombrezQuestion {
        val hour = random.nextInt(0, 24)
        val minute = random.nextInt(0, 60)
        fun fmt(h: Int, m: Int) = "${h.mod(24)}:${m.mod(60).toString().padStart(2, '0')}"
        val correct = fmt(hour, minute)
        val traps = listOf(
            fmt(hour + 2, minute),
            fmt(hour - 2, minute),
            fmt(hour, minute + 10),
            fmt(hour, minute - 10),
            fmt(hour + 12, minute)
        )
        val words = FrenchNumbers.time24Words(hour, minute)
        return choiceQuestion(
            NombrezTopic.TIME, "Wie spät ist es? (Hör zu)", words, words, audioOnly = true,
            correct = correct, traps = traps, reveal = "$correct · $words"
        )
    }

    private fun date(): NombrezQuestion {
        val month = random.nextInt(1, 13)
        val day = random.nextInt(1, daysIn(month) + 1)
        fun fmt(d: Int, m: Int) = "${d.toString().padStart(2, '0')}.${m.toString().padStart(2, '0')}."
        val correct = fmt(day, month)
        val similarDay = when {
            day in 13..16 -> listOf(day + 1, day - 1)       // treize/quatorze/quinze/seize
            day % 10 == 1 && day > 20 -> listOf(day - 1)     // vingt et un / vingt
            else -> listOf(if (day + 10 <= 28) day + 10 else day - 10, if (day + 2 <= 28) day + 2 else day - 2)
        }
        val traps = similarDay.map { fmt(it, month) } +
            listOf(fmt(day, month % 12 + 1), fmt(day, (month + 10) % 12 + 1))
        val words = FrenchNumbers.dateWords(day, month)
        return choiceQuestion(
            NombrezTopic.DATES, "Welches Datum hörst du?", words, words, audioOnly = true,
            correct = correct, traps = traps, reveal = "$correct · $words"
        )
    }

    private fun translateDayOrMonth(): NombrezQuestion {
        val (fr, de) = if (random.nextBoolean()) FrenchNumbers.DAYS to FrenchNumbers.DAYS_DE
                       else FrenchNumbers.MONTHS to FrenchNumbers.MONTHS_DE
        val i = random.nextInt(fr.size)
        return choiceQuestion(
            NombrezTopic.DAYS_MONTHS, "Auf Deutsch?", fr[i], fr[i], audioOnly = false,
            correct = de[i], traps = de - de[i], reveal = "${fr[i]} = ${de[i]}"
        )
    }

    private fun nextDay(): NombrezQuestion {
        val days = FrenchNumbers.DAYS
        val i = random.nextInt(days.size)
        val correct = days[(i + 1) % days.size]
        return choiceQuestion(
            NombrezTopic.DAYS_MONTHS, "Welcher Tag kommt danach?", days[i], days[i], audioOnly = false,
            correct = correct, traps = days - correct - days[i],
            reveal = "${days[i]} → $correct"
        )
    }

    private fun nextMonth(): NombrezQuestion {
        val months = FrenchNumbers.MONTHS
        val i = random.nextInt(months.size)
        val correct = months[(i + 1) % months.size]
        // Neighbours make the best traps (the month before, two ahead)
        val traps = listOf(months[(i + 11) % 12], months[(i + 2) % 12], months[(i + 6) % 12])
        return choiceQuestion(
            NombrezTopic.DAYS_MONTHS, "Welcher Monat kommt danach?", months[i], months[i], audioOnly = false,
            correct = correct, traps = traps, reveal = "${months[i]} → $correct"
        )
    }

    private fun choiceQuestion(
        topic: NombrezTopic,
        instruction: String,
        prompt: String,
        speech: String,
        audioOnly: Boolean,
        correct: String,
        traps: List<String>,
        reveal: String
    ): NombrezQuestion {
        val distractors = traps.filter { it != correct }.distinct().shuffled(random).take(3)
        return NombrezQuestion(
            topic = topic,
            instruction = instruction,
            prompt = prompt,
            speech = speech,
            audioOnly = audioOnly,
            choices = (distractors + correct).shuffled(random),
            answer = correct,
            reveal = reveal
        )
    }

    private fun daysIn(month: Int): Int = when (month) {
        2 -> 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
