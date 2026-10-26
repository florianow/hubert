package com.hubert.viewmodel

import com.hubert.data.repository.ParcoursExercise
import com.hubert.data.repository.ParcoursUnit
import kotlin.random.Random

/** The Parcours quizzes: after the lesson, the étape's exam, and the spaced Révision. */
enum class QuizKind(val title: String, val size: Int) {
    CHECK("Mini-Check", 5),
    CONTROL("Contrôle", 15),
    REVIEW("Révision", 8)
}

/**
 * One quiz question. Choice questions have [options]; typed ones (numbers: hear → digits)
 * have none. [speech] is the solved sentence, read out after answering; [audioOnly]
 * questions are only heard until answered.
 */
data class QuizItem(
    val instruction: String,
    val prompt: String,
    val de: String,
    val answer: String,
    val options: List<String>,
    val rule: String,
    val speech: String?,
    val audioOnly: Boolean = false
) {
    val isTyped: Boolean get() = options.isEmpty()
    /** The prompt with the gap filled in (shown after answering). */
    val solved: String get() = if ("___" in prompt) prompt.replace("___", answer) else prompt
}

/**
 * Builds the questions of a quiz for an étape: its gap exercises (lesson sentences first for
 * the Mini-Check, a mix for Contrôle and Révision); numbers and participle agreement are
 * filled up with the Nombrez!/Accordez! generators.
 */
object ParcoursQuizBuilder {

    fun build(unit: ParcoursUnit, kind: QuizKind, random: Random = Random.Default): List<QuizItem> {
        val exercises = unit.exercises.orEmpty().shuffled(random)
        val lesson = exercises.filter { it.source == "lesson" }
        val pool = exercises.filter { it.source != "lesson" }
        val chosen = when (kind) {
            QuizKind.CHECK -> (lesson + pool).take(kind.size)
            // About half from the lesson, the rest from the pool
            else -> (lesson.take(kind.size / 2 + 1) + pool).take(kind.size).let { picked ->
                picked + (exercises - picked.toSet()).take(kind.size - picked.size)
            }
        }
        val items = chosen.map { it.toItem(unit.kind, random) }.toMutableList()
        val generated = when (unit.kind) {
            "numbers" -> numberItems(unit, kind.size - items.size, random)
            // Agreement: half generated, so the étape's rule groups are all covered
            "agreement" -> agreementItems(unit, maxOf(kind.size - items.size, kind.size / 2), random)
            else -> emptyList()
        }
        return (items.take(kind.size - generated.size) + generated).shuffled(random).take(kind.size)
    }

    private fun ParcoursExercise.toItem(kind: String, random: Random) = QuizItem(
        instruction = INSTRUCTION[kind] ?: "Was passt in die Lücke?",
        prompt = fr,
        de = de,
        answer = answer,
        options = (wrong + answer).distinct().shuffled(random),
        rule = rule,
        speech = fr.replace("___", answer)
    )

    private val INSTRUCTION = mapOf(
        "article" to "Welcher Artikel passt?",
        "adjective" to "Welche Form des Adjektivs passt?",
        "conjugation" to "Welche Verbform passt?",
        "preposition" to "Welche Präposition passt?",
        "relative" to "Welches Relativpronomen passt?",
        "agreement" to "Welche Form passt?"
    )

    private fun numberItems(unit: ParcoursUnit, count: Int, random: Random): List<QuizItem> {
        val topics = unit.focus.mapNotNull { key -> NombrezTopic.entries.find { it.name == key } }.toSet()
            .ifEmpty { return emptyList() }
        val generator = NombrezQuestionGenerator(random)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<QuizItem>()
        repeat(count * 6) {
            if (out.size >= count) return out
            val q = generator.next(topics)
            if (!seen.add(q.prompt + q.answer)) return@repeat
            out += QuizItem(
                instruction = q.instruction,
                prompt = q.prompt,
                de = "",
                answer = q.answer,
                options = q.choices,
                rule = q.reveal,
                speech = q.speech,
                audioOnly = q.audioOnly
            )
        }
        return out
    }

    private fun agreementItems(unit: ParcoursUnit, count: Int, random: Random): List<QuizItem> {
        val groups = unit.focus.mapNotNull { key -> AccordGroup.entries.find { it.name == key } }.toSet()
            .ifEmpty { return emptyList() }
        val generator = AccordezQuestionGenerator(random)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<QuizItem>()
        repeat(count * 6) {
            if (out.size >= count) return out
            val q = generator.next(groups)
            if (!seen.add(q.sentence)) return@repeat
            out += QuizItem(
                instruction = "Welche Form des Partizips? ${q.hint}",
                prompt = q.sentence,
                de = "",
                answer = q.answer,
                options = q.choices,
                rule = q.rule.explanation,
                speech = q.solved
            )
        }
        return out
    }

    /** Typed answers (digits): the same normalisation as Nombrez!. */
    fun isRight(item: QuizItem, given: String): Boolean =
        if (item.isTyped) {
            val digits = given.filter { it.isDigit() }.trimStart('0').ifEmpty { if (given.any { it.isDigit() }) "0" else "" }
            digits.isNotEmpty() && digits == item.answer
        } else given == item.answer
}
