package com.hubert.viewmodel

import kotlin.random.Random

/** Rule groups on the Accordez! selection screen. */
enum class AccordGroup(val label: String, val description: String) {
    ETRE("Mit être", "Marie est partie · Julie et Marc sont arrivés"),
    AVOIR("Mit avoir", "j'ai écrit la lettre · la lettre que j'ai écrite"),
    REFLEXIVE("Reflexive Verben", "elle s'est levée · elle s'est lavé les mains")
}

/** The rule a question tests; [explanation] is shown after answering. */
enum class AccordRule(val group: AccordGroup, val explanation: String) {
    ETRE_SUBJECT(AccordGroup.ETRE, "Mit être richtet sich das Partizip nach dem Subjekt."),
    AVOIR_OBJECT_AFTER(AccordGroup.AVOIR, "Mit avoir: keine Angleichung, wenn das direkte Objekt danach steht."),
    AVOIR_OBJECT_BEFORE(AccordGroup.AVOIR, "Mit avoir: Angleichung an das direkte Objekt, wenn es davor steht (que, l', les)."),
    REFLEXIVE_DIRECT(AccordGroup.REFLEXIVE, "Reflexiv: se ist direktes Objekt → Angleichung an das Subjekt."),
    REFLEXIVE_OBJECT_AFTER(AccordGroup.REFLEXIVE, "Reflexiv mit direktem Objekt danach (les mains, les dents): keine Angleichung."),
    REFLEXIVE_INDIRECT(AccordGroup.REFLEXIVE, "se ist indirektes Objekt (parler à quelqu'un) → keine Angleichung.")
}

data class AccordQuestion(
    val sentence: String,          // with "___" for the participle
    val hint: String,              // "(partir · abfahren)"
    val choices: List<String>,
    val answer: String,
    val rule: AccordRule
) {
    val solved: String get() = sentence.replace("___", answer)
}

/**
 * Builds past-participle agreement questions from curated sentence patterns (the corpus
 * sentences don't mark preceding objects reliably). Forms are derived with the regular
 * rules: +e (fem.), +s (plural, not after s/x), +es (fem. plural) — true for every
 * participle used here (né → née, mort → morte, écrit → écrite, pris → prise/pris).
 */
class AccordezQuestionGenerator(private val random: Random = Random.Default) {

    private enum class G { M, F }

    private data class Subject(
        val text: String,          // "Marie", "Lucie et moi, nous"
        val etre: String,          // "est", "sommes"
        val reflexive: String,     // "s'est", "nous sommes"
        val gender: G,
        val plural: Boolean
    )

    private data class EtreVerb(val infinitive: String, val pp: String, val german: String, val rest: String)

    private data class AvoirSubject(val pronoun: String, val aux: String)

    private data class Obj(
        val noun: String,          // "la lettre"
        val gender: G,
        val plural: Boolean,
        val infinitive: String,
        val pp: String,
        val german: String
    )

    private data class ReflexiveVerb(
        val infinitive: String,    // "se lever"
        val pp: String,
        val german: String,
        val rest: String,
        val rule: AccordRule,
        val pluralOnly: Boolean = false
    )

    private val subjects = listOf(
        Subject("Marie", "est", "s'est", G.F, false),
        Subject("Paul", "est", "s'est", G.M, false),
        Subject("Ma sœur", "est", "s'est", G.F, false),
        Subject("Mon frère", "est", "s'est", G.M, false),
        Subject("Les filles", "sont", "se sont", G.F, true),
        Subject("Les garçons", "sont", "se sont", G.M, true),
        Subject("Julie et Marc", "sont", "se sont", G.M, true),      // mixed group → masculine
        Subject("Mes amies", "sont", "se sont", G.F, true),
        Subject("Lucie et moi, nous", "sommes", "nous sommes", G.F, true),
        Subject("Claire, tu", "es", "t'es", G.F, false),
        Subject("Madame, vous", "êtes", "vous êtes", G.F, false),     // polite vous → singular
        Subject("Messieurs, vous", "êtes", "vous êtes", G.M, true)
    )

    private val etreVerbs = listOf(
        EtreVerb("aller", "allé", "gehen", "à Paris hier"),
        EtreVerb("venir", "venu", "kommen", "à la fête"),
        EtreVerb("partir", "parti", "abfahren", "très tôt"),
        EtreVerb("arriver", "arrivé", "ankommen", "en retard"),
        EtreVerb("entrer", "entré", "hereinkommen", "dans la cuisine"),
        EtreVerb("sortir", "sorti", "ausgehen", "ce soir"),
        EtreVerb("tomber", "tombé", "fallen", "dans la rue"),
        EtreVerb("rester", "resté", "bleiben", "à la maison"),
        EtreVerb("naître", "né", "geboren werden", "en 1995"),
        EtreVerb("devenir", "devenu", "werden", "célèbre"),
        EtreVerb("revenir", "revenu", "zurückkommen", "de vacances"),
        EtreVerb("rentrer", "rentré", "heimkommen", "tard"),
        EtreVerb("descendre", "descendu", "aussteigen", "du train"),
        EtreVerb("monter", "monté", "einsteigen", "dans le bus"),
        EtreVerb("retourner", "retourné", "zurückkehren", "en France")
    )

    private val avoirSubjects = listOf(
        AvoirSubject("je", "ai"), AvoirSubject("tu", "as"), AvoirSubject("il", "a"),
        AvoirSubject("elle", "a"), AvoirSubject("nous", "avons"), AvoirSubject("vous", "avez"),
        AvoirSubject("ils", "ont"), AvoirSubject("elles", "ont")
    )

    private val objects = listOf(
        Obj("la lettre", G.F, false, "écrire", "écrit", "schreiben"),
        Obj("les lettres", G.F, true, "écrire", "écrit", "schreiben"),
        Obj("la porte", G.F, false, "ouvrir", "ouvert", "öffnen"),
        Obj("les fenêtres", G.F, true, "ouvrir", "ouvert", "öffnen"),
        Obj("la photo", G.F, false, "prendre", "pris", "machen"),
        Obj("les photos", G.F, true, "prendre", "pris", "machen"),
        Obj("le film", G.M, false, "voir", "vu", "sehen"),
        Obj("la série", G.F, false, "voir", "vu", "sehen"),
        Obj("les livres", G.M, true, "lire", "lu", "lesen"),
        Obj("la tarte", G.F, false, "faire", "fait", "backen"),
        Obj("les gâteaux", G.M, true, "faire", "fait", "backen"),
        Obj("la robe", G.F, false, "mettre", "mis", "anziehen"),
        Obj("les clés", G.F, true, "perdre", "perdu", "verlieren"),
        Obj("la valise", G.F, false, "trouver", "trouvé", "finden"),
        Obj("les pommes", G.F, true, "manger", "mangé", "essen"),
        Obj("la voiture", G.F, false, "vendre", "vendu", "verkaufen"),
        Obj("les chansons", G.F, true, "chanter", "chanté", "singen"),
        Obj("les cadeaux", G.M, true, "recevoir", "reçu", "bekommen"),
        Obj("la table", G.F, false, "réserver", "réservé", "reservieren"),
        Obj("les fleurs", G.F, true, "acheter", "acheté", "kaufen"),
        Obj("la question", G.F, false, "comprendre", "compris", "verstehen"),
        Obj("les exercices", G.M, true, "finir", "fini", "beenden"),
        Obj("la musique", G.F, false, "entendre", "entendu", "hören"),
        Obj("les dates", G.F, true, "oublier", "oublié", "vergessen")
    )

    private val reflexiveVerbs = listOf(
        ReflexiveVerb("se lever", "levé", "aufstehen", "tôt", AccordRule.REFLEXIVE_DIRECT),
        ReflexiveVerb("se réveiller", "réveillé", "aufwachen", "à six heures", AccordRule.REFLEXIVE_DIRECT),
        ReflexiveVerb("se coucher", "couché", "ins Bett gehen", "tard", AccordRule.REFLEXIVE_DIRECT),
        ReflexiveVerb("se promener", "promené", "spazieren gehen", "au parc", AccordRule.REFLEXIVE_DIRECT),
        ReflexiveVerb("s'habiller", "habillé", "sich anziehen", "vite", AccordRule.REFLEXIVE_DIRECT),
        ReflexiveVerb("se reposer", "reposé", "sich ausruhen", "après le travail", AccordRule.REFLEXIVE_DIRECT),
        ReflexiveVerb("se dépêcher", "dépêché", "sich beeilen", "pour le train", AccordRule.REFLEXIVE_DIRECT),
        ReflexiveVerb("s'amuser", "amusé", "sich amüsieren", "à la fête", AccordRule.REFLEXIVE_DIRECT),
        ReflexiveVerb("se laver", "lavé", "sich waschen", "les mains", AccordRule.REFLEXIVE_OBJECT_AFTER),
        ReflexiveVerb("se brosser", "brossé", "sich putzen", "les dents", AccordRule.REFLEXIVE_OBJECT_AFTER),
        ReflexiveVerb("se parler", "parlé", "miteinander sprechen", "au téléphone", AccordRule.REFLEXIVE_INDIRECT, pluralOnly = true),
        ReflexiveVerb("se téléphoner", "téléphoné", "sich anrufen", "hier soir", AccordRule.REFLEXIVE_INDIRECT, pluralOnly = true)
    )

    fun next(groups: Set<AccordGroup>): AccordQuestion = when (groups.random(random)) {
        AccordGroup.ETRE -> etreQuestion()
        AccordGroup.AVOIR -> when (random.nextInt(3)) {
            0 -> avoirObjectAfter()
            1 -> avoirRelative()
            else -> avoirPronoun()
        }
        AccordGroup.REFLEXIVE -> reflexiveQuestion()
    }

    // ── Patterns ────────────────────────────────────────────────────────

    private fun etreQuestion(): AccordQuestion {
        val s = subjects.random(random)
        val v = etreVerbs.random(random)
        return question(
            sentence = "${s.text} ${s.etre} ___ ${v.rest}.",
            infinitive = v.infinitive, german = v.german, pp = v.pp,
            answer = agree(v.pp, s.gender, s.plural), rule = AccordRule.ETRE_SUBJECT
        )
    }

    /** "Nous avons ___ (écrire) la lettre." → écrit */
    private fun avoirObjectAfter(): AccordQuestion {
        val s = avoirSubjects.random(random)
        val o = objects.random(random)
        return question(
            sentence = capitalize("${subjectWithAux(s)} ___ ${o.noun}."),
            infinitive = o.infinitive, german = o.german, pp = o.pp,
            answer = o.pp, rule = AccordRule.AVOIR_OBJECT_AFTER
        )
    }

    /** "Voici la lettre que nous avons ___ (écrire)." → écrite */
    private fun avoirRelative(): AccordQuestion {
        val s = avoirSubjects.random(random)
        val o = objects.random(random)
        val que = if (s.pronoun.first() in "aeiou") "qu'" else "que "
        return question(
            sentence = "Voici ${o.noun} $que${subjectWithAux(s)} ___.",
            infinitive = o.infinitive, german = o.german, pp = o.pp,
            answer = agree(o.pp, o.gender, o.plural), rule = AccordRule.AVOIR_OBJECT_BEFORE
        )
    }

    /** "La lettre ? Nous l'avons ___ (écrire) hier." → écrite */
    private fun avoirPronoun(): AccordQuestion {
        val s = avoirSubjects.random(random)
        val o = objects.random(random)
        val pronoun = if (o.plural) "les ${s.aux}" else "l'${s.aux}"
        return question(
            sentence = "${capitalize(o.noun)} ? ${capitalize(s.pronoun)} $pronoun ___ hier.",
            infinitive = o.infinitive, german = o.german, pp = o.pp,
            answer = agree(o.pp, o.gender, o.plural), rule = AccordRule.AVOIR_OBJECT_BEFORE
        )
    }

    private fun reflexiveQuestion(): AccordQuestion {
        val v = reflexiveVerbs.random(random)
        val s = (if (v.pluralOnly) subjects.filter { it.plural && !it.text.contains("vous") } else subjects).random(random)
        val answer = if (v.rule == AccordRule.REFLEXIVE_DIRECT) agree(v.pp, s.gender, s.plural) else v.pp
        return question(
            sentence = "${s.text} ${s.reflexive} ___ ${v.rest}.",
            infinitive = v.infinitive, german = v.german, pp = v.pp,
            answer = answer, rule = v.rule
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun subjectWithAux(s: AvoirSubject): String =
        if (s.pronoun == "je") "j'${s.aux}" else "${s.pronoun} ${s.aux}"

    private fun capitalize(text: String): String = text.replaceFirstChar { it.uppercaseChar() }

    private fun agree(pp: String, gender: G, plural: Boolean): String = when {
        gender == G.F && plural -> pp + "es"
        gender == G.F -> pp + "e"
        plural -> if (pp.endsWith("s") || pp.endsWith("x")) pp else pp + "s"
        else -> pp
    }

    private fun question(
        sentence: String,
        infinitive: String,
        german: String,
        pp: String,
        answer: String,
        rule: AccordRule
    ): AccordQuestion {
        val forms = listOf(
            agree(pp, G.M, false), agree(pp, G.F, false),
            agree(pp, G.M, true), agree(pp, G.F, true)
        ).distinct()
        return AccordQuestion(
            sentence = sentence,
            hint = "($infinitive · $german)",
            choices = forms.shuffled(random),
            answer = answer,
            rule = rule
        )
    }
}
