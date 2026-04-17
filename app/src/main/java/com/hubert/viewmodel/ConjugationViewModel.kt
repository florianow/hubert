package com.hubert.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.ConjugationVerb
import com.hubert.data.repository.HighScoreRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Structured tense explanation for the info dialog.
 */
data class TenseInfo(
    val description: String,
    val sections: List<TenseSection>
)

data class TenseSection(
    val title: String,
    val description: String? = null,
    val examples: List<Pair<String, String>> = emptyList(),
    /** Optional conjugation table: first row = header, remaining rows = data. */
    val table: List<List<String>>? = null
)

enum class QuestionMode { PICK, TYPE }

/**
 * Conjuguez! — verb conjugation game.
 *
 * Player selects which tenses to practice before the game starts.
 * During gameplay, tenses are picked randomly from the selection,
 * with error-weighted repetition: tenses the player gets wrong
 * appear more frequently.
 *
 * When a matching example sentence exists, it's shown with the verb blanked out.
 * Otherwise, a plain drill view (infinitive + pronoun + tense) is used.
 *
 * ALL data comes directly from the Anki deck — nothing is generated.
 */
data class ConjugationState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isNewHighScore: Boolean = false,

    // Tense selection (shown before game starts)
    val isTenseSelection: Boolean = false,
    val availableTenses: Map<String, String> = emptyMap(),  // key -> display name
    val selectedTenses: Set<String> = setOf("present"),

    // Current question
    val infinitive: String = "",
    val german: String = "",
    val ipa: String? = null,               // IPA pronunciation of the infinitive
    val tenseName: String = "",        // Display name: "Présent", "Imparfait", etc.
    val personLabel: String = "",      // "je", "tu", "il/elle", etc.

    // Passé composé question type: null if not PC, "auxiliary" or "verb_form"
    val pcQuestionType: String? = null,
    // For auxiliary questions: the participle shown in the prompt (e.g., "mangé")
    val participleShown: String? = null,
    // Auxiliary hint for passé composé auxiliary questions (e.g., "j'ai" or "je suis")
    val auxiliaryHint: String? = null,

    // Sentence context (null = use plain drill view)
    val sentenceFr: String? = null,    // French sentence with blank
    val sentenceDe: String? = null,    // German translation

    // Multiple choice
    val choices: List<String> = emptyList(),
    val correctIndex: Int = -1,

    // Feedback
    val selectedIndex: Int? = null,
    val feedback: Boolean? = null,     // true=correct, false=wrong

    // Timer-based scoring
    val timeRemainingMs: Long = 0L,
    val timerFraction: Float = 1f,
    val score: Int = 0,
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val highScore: Int = 0,

    // Info view limits: tenseKey -> remaining uses
    val infoUsesRemaining: Map<String, Int> = emptyMap(),

    // Post-game
    val durationMs: Long = 0L,
    val answerHistory: List<AnswerRecord> = emptyList(),

    // Manual advance — true when feedback is shown and player needs to tap "Next"
    val awaitingNext: Boolean = false,

    // Countdown
    val countdown: Int? = null,

    val questionMode: QuestionMode = QuestionMode.PICK,
    val typedText: String = "",
    val isMoodQuestion: Boolean = false,
)

@HiltViewModel
class ConjugationViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val statisticsRepository: StatisticsRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConjugationState())
    val uiState: StateFlow<ConjugationState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var timerJob: Job? = null
    private var timerDeadline = 0L
    private var timerPausedRemaining = 0L   // >0 means timer is paused
    private var gameStartTime = 0L

    private var verbPool: MutableList<ConjugationVerb> = mutableListOf()
    private var allVerbs: List<ConjugationVerb> = emptyList()  // for PC distractor generation
    private var activeTenses: Set<String> = setOf("present")

    // Error-weighted tense tracking: count of wrong answers per tense
    private var tenseErrorCounts: MutableMap<String, Int> = mutableMapOf()
    private val answerLog = mutableListOf<AnswerRecord>()

    companion object {
        const val GAME_TIME_MS = 90_000L
        const val MAX_TIME_MS = 300_000L
        const val CORRECT_BONUS_MS = 5_000L
        const val WRONG_PENALTY_MS = 5_000L
        const val POINTS_PER_CORRECT = 150
        const val STREAK_BONUS = 30
        const val NUM_CHOICES = 4
        const val INFO_USES_PER_TENSE = 3

        // Error weight: each error on a tense adds this much extra selection weight
        const val ERROR_WEIGHT_BONUS = 2

        // Tense keys → display names
        val TENSE_DISPLAY = mapOf(
            "present" to "Présent",
            "passe_compose" to "Passé composé",
            "imparfait" to "Imparfait",
            "futur" to "Futur simple",
            "conditionnel" to "Conditionnel",
            "subjonctif" to "Subjonctif",
            "passe_simple" to "Passé simple",
            "imperatif" to "Impératif"
        )

        // Tense explanations (from anki_french grammar notes by jacbz)
        // Structured for rich rendering: sections with title, description, and example pairs
        val TENSE_INFO = mapOf(
            "present" to TenseInfo(
                description = "Das présent (Präsens) beschreibt gegenwärtige Ereignisse und Handlungen, die zum Sprechzeitpunkt stattfinden, sowie Gewohnheiten und allgemeine Tatsachen.",
                sections = listOf(
                    TenseSection(
                        "Bildung",
                        description = "Stamm + Endungen. -er: -e, -es, -e, -ons, -ez, -ent. -ir (2. Gr.): -is, -is, -it, -issons, -issez, -issent. -re: -s, -s, \u2205, -ons, -ez, -ent.",
                        table = listOf(
                            listOf("", "-er", "-ir", "-re", "aller"),
                            listOf("je", "parle", "finis", "vends", "vais"),
                            listOf("tu", "parles", "finis", "vends", "vas"),
                            listOf("il/elle", "parle", "finit", "vend", "va"),
                            listOf("nous", "parlons", "finissons", "vendons", "allons"),
                            listOf("vous", "parlez", "finissez", "vendez", "allez"),
                            listOf("ils/elles", "parlent", "finissent", "vendent", "vont")
                        )
                    ),
                    TenseSection("Gegenwärtiges Ereignis", examples = listOf(
                        "Sophie et Lucas préparent le dîner." to "Sophie und Lucas bereiten das Abendessen zu."
                    )),
                    TenseSection("Andauernde Handlung mit \u201Edepuis\u201C", examples = listOf(
                        "Emma étudie le français depuis deux ans." to "Emma lernt seit zwei Jahren Französisch."
                    )),
                    TenseSection("Nahe Zukunft", examples = listOf(
                        "Nous commençons notre nouveau projet la semaine prochaine." to "Wir beginnen nächste Woche mit unserem neuen Projekt."
                    )),
                    TenseSection("Gewohnheiten und allgemeine Tatsachen", examples = listOf(
                        "Les Martin font du sport tous les weekends." to "Die Martins treiben jedes Wochenende Sport.",
                        "La patience est une vertu." to "Geduld ist eine Tugend."
                    )),
                    TenseSection("Realisierbare Bedingung nach \u201Esi\u201C", examples = listOf(
                        "Si nous prenons le train de 8h, nous arriverons à temps pour la réunion." to "Wenn wir den Zug um 8 Uhr nehmen, kommen wir rechtzeitig zum Meeting an."
                    )),
                    TenseSection("Historisches Präsens", description = "In Erzählungen kann das Präsens vergangene Ereignisse lebendiger darstellen.", examples = listOf(
                        "Je regardais la télévision. Soudain, on sonne à la porte." to "Ich schaute fern. Plötzlich klingelt es an der Tür."
                    ))
                )
            ),

            "passe_compose" to TenseInfo(
                description = "Das passé composé ist die wichtigste Vergangenheitsform im Französischen. Es beschreibt abgeschlossene Handlungen und wird mit einem Hilfsverb (avoir oder être) + Partizip gebildet.",
                sections = listOf(
                    TenseSection(
                        "Bildung",
                        description = "Hilfsverb (avoir/être) im Präsens + Partizip Perfekt. Partizip: -er \u2192 -é, -ir \u2192 -i, -re \u2192 -u. Bei être richtet sich das Partizip nach dem Subjekt.",
                        table = listOf(
                            listOf("", "-er", "-ir", "-re", "aller"),
                            listOf("Partizip", "parlé", "fini", "vendu", "allé"),
                            listOf("Hilfsverb", "avoir", "avoir", "avoir", "être"),
                            listOf("je", "j'ai parlé", "j'ai fini", "j'ai vendu", "je suis allé(e)"),
                            listOf("tu", "tu as parlé", "tu as fini", "tu as vendu", "tu es allé(e)"),
                            listOf("il/elle", "il a parlé", "il a fini", "il a vendu", "il est allé"),
                            listOf("nous", "avons parlé", "avons fini", "avons vendu", "sommes allé(e)s"),
                            listOf("vous", "avez parlé", "avez fini", "avez vendu", "êtes allé(e)(s)"),
                            listOf("ils/elles", "ont parlé", "ont fini", "ont vendu", "sont allés")
                        )
                    ),
                    TenseSection("Abgeschlossene Handlungen", examples = listOf(
                        "Nous avons visité le musée hier." to "Wir haben gestern das Museum besucht.",
                        "Il a plu toute la journée." to "Es hat den ganzen Tag geregnet."
                    )),
                    TenseSection("Aufeinanderfolgende Ereignisse", examples = listOf(
                        "Elle s'est levée, a pris son café et est partie." to "Sie ist aufgestanden, hat ihren Kaffee getrunken und ist gegangen."
                    )),
                    TenseSection("Être-Verben", description = "Bewegungs- und Zustandsverben: aller, venir, arriver, partir, entrer, sortir, monter, descendre, naître, mourir, rester, tomber, retourner, devenir, passer.", examples = listOf(
                        "Ils sont arrivés en retard." to "Sie sind zu spät angekommen.",
                        "Marie est née en 1990." to "Marie wurde 1990 geboren."
                    )),
                    TenseSection("Angleichung des Partizips", description = "Bei être richtet sich das Partizip nach dem Subjekt (Geschlecht und Zahl). Bei avoir nur, wenn ein direktes Objekt vorangestellt ist.", examples = listOf(
                        "Elles sont parties ensemble." to "Sie sind zusammen gegangen.",
                        "Les fleurs que j'ai achetées sont belles." to "Die Blumen, die ich gekauft habe, sind schön."
                    )),
                    TenseSection("Zusammenspiel mit dem Imparfait", description = "Das passé composé beschreibt die Haupthandlung, das imparfait den Hintergrund.", examples = listOf(
                        "Il faisait beau quand nous sommes arrivés." to "Es war schönes Wetter, als wir ankamen."
                    ))
                )
            ),

            "imparfait" to TenseInfo(
                description = "Das imparfait ist eine einfache Vergangenheitsform. Es beschreibt Hintergründe, Situationen und Handlungen, die zeitlich nicht genau begrenzt sind.",
                sections = listOf(
                    TenseSection(
                        "Bildung",
                        description = "Stamm der nous-Form im Präsens + Endungen: -ais, -ais, -ait, -ions, -iez, -aient. Einzige Ausnahme: être \u2192 ét-.",
                        table = listOf(
                            listOf("", "-er", "-ir", "-re", "être"),
                            listOf("je", "parlais", "finissais", "vendais", "étais"),
                            listOf("tu", "parlais", "finissais", "vendais", "étais"),
                            listOf("il/elle", "parlait", "finissait", "vendait", "était"),
                            listOf("nous", "parlions", "finissions", "vendions", "étions"),
                            listOf("vous", "parliez", "finissiez", "vendiez", "étiez"),
                            listOf("ils/elles", "parlaient", "finissaient", "vendaient", "étaient")
                        )
                    ),
                    TenseSection("Laufende Handlungen und andauernde Zustände", examples = listOf(
                        "Les enfants jouaient dans le parc." to "Die Kinder spielten im Park.",
                        "Sophie semblait fatiguée et avait l'air préoccupée." to "Sophie schien müde und wirkte besorgt."
                    )),
                    TenseSection("Gewohnheiten in der Vergangenheit", examples = listOf(
                        "Chaque été, nous allions à la montagne." to "Jeden Sommer fuhren wir in die Berge."
                    )),
                    TenseSection("Höfliche Ausdrucksweise", examples = listOf(
                        "Je voulais vous demander un service." to "Ich wollte Sie um einen Gefallen bitten."
                    )),
                    TenseSection("Nicht erfüllte Bedingung nach „si“", examples = listOf(
                        "Si j'avais plus de temps, j'apprendrais le japonais." to "Wenn ich mehr Zeit hätte, würde ich Japanisch lernen."
                    )),
                    TenseSection("Zusammenspiel mit dem Passé composé", description = "Das passé composé beschreibt abgeschlossene Handlungen, das imparfait den Hintergrund oder andauernde Zustände.", examples = listOf(
                        "Il faisait beau quand nous sommes arrivés à la plage." to "Es war schönes Wetter, als wir am Strand ankamen.",
                        "Lorsque Pierre est entré, Marie lisait un livre." to "Als Pierre hereinkam, las Marie gerade ein Buch."
                    ))
                )
            ),

            "futur" to TenseInfo(
                description = "Das futur simple (Futur I) wird für zukünftige Handlungen verwendet. Im Deutschen wird oft dafür das Präsens benutzt.",
                sections = listOf(
                    TenseSection(
                        "Bildung",
                        description = "Infinitiv + Endungen von avoir: -ai, -as, -a, -ons, -ez, -ont. Bei -re fällt das -e weg. Unregelmäßige Verben haben Sonderstämme.",
                        table = listOf(
                            listOf("", "-er", "-ir", "-re", "avoir"),
                            listOf("je", "parlerai", "finirai", "vendrai", "aurai"),
                            listOf("tu", "parleras", "finiras", "vendras", "auras"),
                            listOf("il/elle", "parlera", "finira", "vendra", "aura"),
                            listOf("nous", "parlerons", "finirons", "vendrons", "aurons"),
                            listOf("vous", "parlerez", "finirez", "vendrez", "aurez"),
                            listOf("ils/elles", "parleront", "finiront", "vendront", "auront")
                        )
                    ),
                    TenseSection("Zukünftige Handlungen", examples = listOf(
                        "Nous arriverons à la gare vers midi." to "Wir kommen gegen Mittag am Bahnhof an."
                    )),
                    TenseSection("Höflicher Imperativ oder höfliche Frage", examples = listOf(
                        "Tu fermeras la porte en partant, s'il te plaît." to "Schließ bitte die Tür, wenn du gehst.",
                        "Vous prendrez un café avec nous ?" to "Möchten Sie einen Kaffee mit uns trinken?"
                    )),
                    TenseSection("Erfüllbare Bedingung nach „si“", examples = listOf(
                        "Si le temps est beau, nous ferons un pique-nique au parc." to "Wenn das Wetter schön ist, machen wir ein Picknick im Park."
                    )),
                    TenseSection("Achtung: Anders als im Deutschen", description = "Nach espérer (hoffen), promettre (versprechen) und quand (wenn) steht im Französischen das futur simple.", examples = listOf(
                        "J'espère que vous passerez un bon week-end." to "Ich hoffe, dass Sie ein schönes Wochenende verbringen.",
                        "Quand nous visiterons Paris, nous monterons sur la Tour Eiffel." to "Wenn wir Paris besuchen, steigen wir auf den Eiffelturm."
                    )),
                    TenseSection("Vermutung", description = "Die Hilfsverben avoir und être im futur simple können eine Vermutung ausdrücken.", examples = listOf(
                        "Il est déjà tard, les magasins seront fermés." to "Es ist schon spät, die Geschäfte sind wahrscheinlich geschlossen."
                    ))
                )
            ),

            "conditionnel" to TenseInfo(
                description = "Das conditionnel ist sowohl Modus als auch Zeitform. Als Modus zeigt es an, dass eine Handlung möglicherweise stattfinden wird. Als Zeitform drückt es die Zukunft aus Sicht der Vergangenheit aus.",
                sections = listOf(
                    TenseSection(
                        "Bildung",
                        description = "Gleicher Stamm wie futur simple + Endungen des imparfait: -ais, -ais, -ait, -ions, -iez, -aient.",
                        table = listOf(
                            listOf("", "-er", "-ir", "-re", "faire"),
                            listOf("je", "parlerais", "finirais", "vendrais", "ferais"),
                            listOf("tu", "parlerais", "finirais", "vendrais", "ferais"),
                            listOf("il/elle", "parlerait", "finirait", "vendrait", "ferait"),
                            listOf("nous", "parlerions", "finirions", "vendrions", "ferions"),
                            listOf("vous", "parleriez", "finiriez", "vendriez", "feriez"),
                            listOf("ils/elles", "parleraient", "finiraient", "vendraient", "feraient")
                        )
                    ),
                    TenseSection("Nicht erfüllte Bedingung", description = "In Bedingungssätzen mit si + imparfait.", examples = listOf(
                        "Si Thomas avait plus de temps, il apprendrait le japonais." to "Wenn Thomas mehr Zeit hätte, würde er Japanisch lernen."
                    )),
                    TenseSection("Wünsche und Annahmen", examples = listOf(
                        "Nous aimerions visiter le musée du Louvre." to "Wir würden gerne das Louvre-Museum besuchen.",
                        "Sophie rêve d'être écrivaine ; elle écrirait des romans et voyagerait pour les promouvoir." to "Sophie träumt davon, Schriftstellerin zu sein; sie würde Romane schreiben und reisen, um sie zu bewerben."
                    )),
                    TenseSection("Wahrscheinlichkeit", examples = listOf(
                        "Je pense qu'elle le ferait." to "Ich denke, dass sie es tun würde."
                    )),
                    TenseSection("Höflichkeit", examples = listOf(
                        "Pourriez-vous m'indiquer le chemin vers la gare, s'il vous plaît ?" to "Könnten Sie mir bitte den Weg zum Bahnhof zeigen?"
                    )),
                    TenseSection("Höfliche Vorschläge", examples = listOf(
                        "Et si nous allions faire un pique-nique ce week-end ?" to "Wie wäre es, wenn wir dieses Wochenende picknicken gehen würden?"
                    )),
                    TenseSection("Unbestätigte Informationen (Medien)", examples = listOf(
                        "D'après le journal Le Monde, les otages seraient en vie." to "Laut der Zeitung „Le Monde“ wären die Geiseln am Leben."
                    )),
                    TenseSection("Zukunft aus Sicht der Vergangenheit", examples = listOf(
                        "Josephine a promis qu'ils partiraient en week-end dès qu'elle aurait terminé son projet." to "Josephine hat versprochen, dass sie übers Wochenende wegfahren, sobald sie ihr Projekt beendet hat."
                    ))
                )
            ),

            "subjonctif" to TenseInfo(
                description = "Der subjonctif wird f\u00FCr subjektive Aussagen und Willens\u00E4u\u00DFerungen verwendet, w\u00E4hrend der Indikativ Tatsachen beschreibt. Er steht meist in Nebens\u00E4tzen mit \u201Eque\u201C.",
                sections = listOf(
                    TenseSection(
                        "Bildung",
                        description = "Stamm der ils-Form im Präsens + Endungen: -e, -es, -e, -ions, -iez, -ent. Nous/vous verwenden oft den imparfait-Stamm.",
                        table = listOf(
                            listOf("", "-er", "-ir", "-re", "pouvoir"),
                            listOf("je", "parle", "finisse", "vende", "puisse"),
                            listOf("tu", "parles", "finisses", "vendes", "puisses"),
                            listOf("il/elle", "parle", "finisse", "vende", "puisse"),
                            listOf("nous", "parlions", "finissions", "vendions", "puissions"),
                            listOf("vous", "parliez", "finissiez", "vendiez", "puissiez"),
                            listOf("ils/elles", "parlent", "finissent", "vendent", "puissent")
                        )
                    ),
                    TenseSection("Wille und Wunsch", description = "Nach vouloir, souhaiter, aimer, désirer, exiger …", examples = listOf(
                        "Paul aimerait bien qu'elle le conduise à l'aéroport." to "Paul möchte gern, dass sie ihn zum Flughafen fährt."
                    )),
                    TenseSection("Stellungnahme", description = "Nach accepter, refuser, proposer, permettre, interdire …", examples = listOf(
                        "Le professeur a proposé que nous fassions une excursion." to "Der Lehrer hat vorgeschlagen, dass wir einen Ausflug machen."
                    )),
                    TenseSection("Pflicht", description = "Nach falloir, il est nécessaire, il est indispensable …", examples = listOf(
                        "Il faut que nous prenions une décision rapidement." to "Wir müssen schnell eine Entscheidung treffen."
                    )),
                    TenseSection("Möglichkeit und Unmöglichkeit", description = "Nach il se peut, il est possible, il est impossible …", examples = listOf(
                        "Il se peut que le train soit en retard à cause de la neige." to "Es kann sein, dass der Zug wegen des Schnees Verspätung hat.",
                        "Il est impossible que nous finissions le travail aujourd'hui." to "Es ist unmöglich, dass wir die Arbeit heute beenden."
                    )),
                    TenseSection("Zweifel", description = "Nach douter, il est peu probable …", examples = listOf(
                        "Nous doutons que cette solution résolve tous nos problèmes." to "Wir bezweifeln, dass diese Lösung alle unsere Probleme löst."
                    )),
                    TenseSection("Gefühle und Eindrücke", description = "Freude, Überraschung, Traurigkeit, Angst …", examples = listOf(
                        "Nous sommes ravis que vous ayez accepté notre invitation." to "Wir sind erfreut, dass Sie unsere Einladung angenommen haben.",
                        "Je suis surpris qu'il ne soit pas encore arrivé." to "Ich bin überrascht, dass er noch nicht angekommen ist.",
                        "J'ai peur qu'il ait oublié notre rendez-vous." to "Ich habe Angst, dass er unseren Termin vergessen hat."
                    )),
                    TenseSection("Konjunktionen", description = "Nach afin que, avant que, pour que …", examples = listOf(
                        "Le musée a été rénové afin que les visiteurs puissent mieux apprécier les œuvres." to "Das Museum wurde renoviert, damit die Besucher die Kunstwerke besser genießen können."
                    )),
                    TenseSection("Relativsätze", description = "Wenn eine gewünschte Eigenschaft ausgedrückt wird.", examples = listOf(
                        "Je cherche une solution qui convienne à tout le monde." to "Ich suche eine Lösung, die allen passt."
                    )),
                    TenseSection("Befehl an dritte Person", examples = listOf(
                        "Que personne ne sorte !" to "Niemand verlässt den Raum!"
                    )),
                    TenseSection("Feste Wendungen", examples = listOf(
                        "Vive la liberté !" to "Es lebe die Freiheit!"
                    )),
                    TenseSection("Bei Verneinung und Inversionsfrage", description = "Nach Verben des Meinens steht der subjonctif bei Verneinung oder Inversionsfrage — aber nicht bei est-ce que oder Intonationsfrage.", examples = listOf(
                        "Il ne croit pas que Marie soit douée pour les langues." to "Er glaubt nicht, dass Marie sprachbegabt ist.",
                        "Pensez-vous qu'il faille reporter la réunion ?" to "Denken Sie, dass es notwendig ist, die Besprechung zu verschieben?"
                    ))
                )
            ),

            "passe_simple" to TenseInfo(
                description = "Das passé simple ist eine einfache Vergangenheitsform, die hauptsächlich in der Schriftsprache verwendet wird (Romane, Geschichtsbücher). In der gesprochenen Sprache wird stattdessen das passé composé verwendet.",
                sections = listOf(
                    TenseSection(
                        "Bildung",
                        description = "Eigene Endungen je nach Gruppe. -er: -ai, -as, -a, -âmes, -âtes, -èrent. -ir/-re: -is, -is, -it, -îmes, -îtes, -irent. Viele unregelmäßige Verben nutzen -us-Endungen.",
                        table = listOf(
                            listOf("", "-er", "-ir", "-re", "avoir"),
                            listOf("je", "parlai", "finis", "vendis", "eus"),
                            listOf("tu", "parlas", "finis", "vendis", "eus"),
                            listOf("il/elle", "parla", "finit", "vendit", "eut"),
                            listOf("nous", "parlâmes", "finîmes", "vendîmes", "eûmes"),
                            listOf("vous", "parlâtes", "finîtes", "vendîtes", "eûtes"),
                            listOf("ils/elles", "parlèrent", "finirent", "vendirent", "eurent")
                        )
                    ),
                    TenseSection("Abgeschlossene Einzelereignisse", examples = listOf(
                        "Guy de Maupassant mourut en 1893." to "Guy de Maupassant starb 1893."
                    )),
                    TenseSection("Aufeinanderfolgende Handlungen", examples = listOf(
                        "Sophie ouvrit la porte, puis entra dans la pièce." to "Sophie öffnete die Tür und betrat dann den Raum."
                    )),
                    TenseSection("Begrenzte Wiederholungen", examples = listOf(
                        "Pendant un mois, elle lui écrivit chaque jour." to "Einen Monat lang schrieb sie ihm jeden Tag."
                    )),
                    TenseSection("Zusammenspiel mit dem Imparfait", description = "Das passé simple beschreibt die Haupthandlung, das imparfait den Hintergrund.", examples = listOf(
                        "Le train arriva à la gare qui était bondée de voyageurs." to "Der Zug kam am Bahnhof an, der voller Reisender war."
                    ))
                )
            ),

            "mood_question" to TenseInfo(
                description = "Subjonctif oder Indikativ? Der Modus hängt davon ab, ob eine subjektive Einstellung (Wunsch, Zweifel, Gefühl, Pflicht) oder eine Tatsache ausgedrückt wird. Entscheidend ist meist das einleitende Verb oder die Konjunktion.",
                sections = listOf(
                    TenseSection(
                        "Subjonctif — nach diesen Auslösern",
                        description = "Wille & Wunsch: vouloir que, souhaiter que, aimer que\nPflicht: il faut que, il est nécessaire que\nMöglichkeit: il se peut que, il est possible que\nZweifel & Verneinung: douter que, ne pas croire que\nGefühle: être content que, avoir peur que, regretter que\nKonjunktionen: bien que, avant que, pour que, afin que, à moins que",
                        examples = listOf(
                            "Il faut que tu finisses avant midi." to "Du musst vor Mittag fertig sein.",
                            "Je suis ravi qu'elle vienne ce soir." to "Ich bin froh, dass sie heute Abend kommt.",
                            "Bien qu'il fasse froid, nous sortons." to "Obwohl es kalt ist, gehen wir raus."
                        )
                    ),
                    TenseSection(
                        "Indikativ — nach diesen Auslösern",
                        description = "Tatsachen & Gewissheit: savoir que, être sûr que, il est certain que\nWahrnehmung: voir que, entendre que, remarquer que\nMeinung (bejahend): penser que, croire que, trouver que\nZeit & Kausalität: parce que, puisque, quand (+ futur)",
                        examples = listOf(
                            "Je sais qu'elle parle bien français." to "Ich weiß, dass sie gut Französisch spricht.",
                            "Il est certain qu'il viendra demain." to "Es ist sicher, dass er morgen kommt.",
                            "Je pense qu'il a raison." to "Ich glaube, dass er Recht hat."
                        )
                    ),
                    TenseSection(
                        "Umschlag bei Verneinung / Inversionsfrage",
                        description = "Verben des Meinens (penser, croire, trouver) verlangen den Subjonctif, wenn sie verneint sind oder eine Inversionsfrage bilden.",
                        examples = listOf(
                            "Je ne crois pas qu'il soit là." to "Ich glaube nicht, dass er da ist.",
                            "Pensez-vous qu'il faille reporter la réunion?" to "Denken Sie, dass die Sitzung verschoben werden muss?"
                        )
                    )
                )
            ),

            "imperatif" to TenseInfo(
                description = "Der impératif (Imperativ) drückt direkte Aufforderungen, Wünsche, Befehle, Bitten, Empfehlungen, Verbote und Ratschläge aus. Im Französischen gibt es ihn nur für tu, nous und vous.",
                sections = listOf(
                    TenseSection(
                        "Bildung",
                        description = "Wie Präsens, aber ohne Subjektpronomen. Bei -er-Verben fällt das -s der tu-Form weg. Nur 3 Formen: tu, nous, vous.",
                        table = listOf(
                            listOf("", "-er", "-ir", "-re", "être"),
                            listOf("tu", "parle", "finis", "vends", "sois"),
                            listOf("nous", "parlons", "finissons", "vendons", "soyons"),
                            listOf("vous", "parlez", "finissez", "vendez", "soyez")
                        )
                    ),
                    TenseSection("Wunsch und Ratschlag", examples = listOf(
                        "Profite bien de tes vacances." to "Genieße deinen Urlaub!"
                    )),
                    TenseSection("Anweisung", examples = listOf(
                        "Mélangez les ingrédients pendant 5 minutes." to "Vermischen Sie die Zutaten fünf Minuten lang."
                    )),
                    TenseSection("Befehl", examples = listOf(
                        "Écoute attentivement !" to "Hör genau zu!",
                        "Partons maintenant !" to "Lass uns jetzt gehen!"
                    )),
                    TenseSection("Betonung mit „donc“", examples = listOf(
                        "Essayez donc cette recette." to "Probieren Sie doch mal dieses Rezept aus!"
                    )),
                    TenseSection("Besonderheiten", description = "Die 2. Person Singular der -er-Verben endet auf -e (ohne -s). Wenn en oder y folgen, erhält der Imperativ die Endung -es: penses-y (denk daran). Für Befehle an dritte Personen wird der Subjonctif verwendet.", examples = listOf(
                        "Qu'elle vienne tout de suite !" to "Sie soll sofort kommen!"
                    )),
                    TenseSection("Reflexive Verben im Imperativ", description = "Das Reflexivpronomen wird im bejahten Imperativ mit Bindestrich nachgestellt.", examples = listOf(
                        "Lave-toi ! / Lavons-nous ! / Lavez-vous !" to "Wasch dich! / Waschen wir uns! / Wascht euch!",
                        "Ne te lave pas !" to "Wasch dich nicht!"
                    ))
                )
            )
        )

        val PERSON_LABELS = listOf("je", "tu", "il/elle", "nous", "vous", "ils/elles")
        // Impératif only has tu (1), nous (3), vous (4) forms
        val IMPERATIF_PERSONS = listOf(1, 3, 4)
    }

    init {
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "conjugation")
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    /**
     * Show the tense selection screen. Called when user taps the Conjuguez! card.
     */
    fun showTenseSelection() {
        _uiState.update {
            it.copy(
                isTenseSelection = true,
                availableTenses = TENSE_DISPLAY,
                selectedTenses = it.selectedTenses  // preserve previous selection
            )
        }
    }

    /**
     * Toggle a tense on/off in the selection screen.
     * Ensures at least one tense remains selected.
     */
    fun toggleTense(tenseKey: String) {
        _uiState.update { state ->
            val current = state.selectedTenses
            val updated = if (tenseKey in current) {
                // Don't allow deselecting the last tense
                if (current.size > 1) current - tenseKey else current
            } else {
                current + tenseKey
            }
            state.copy(selectedTenses = updated)
        }
    }

    /**
     * Start the game with the currently selected tenses.
     */
    fun startGame() {
        countdownJob?.cancel()
        timerJob?.cancel()

        activeTenses = _uiState.value.selectedTenses
        tenseErrorCounts = mutableMapOf()
        answerLog.clear()
        allVerbs = vocabRepository.getConjugations()
        verbPool = allVerbs.shuffled().toMutableList()

        // Initialize info uses: 3 per selected tense
        val infoUses = activeTenses.associateWith { INFO_USES_PER_TENSE }.toMutableMap()
        if ("subjonctif" in activeTenses && "present" in activeTenses) {
            infoUses["mood_question"] = INFO_USES_PER_TENSE
        }

        _uiState.update {
            ConjugationState(
                isPlaying = false,
                isTenseSelection = false,
                countdown = 3,
                highScore = it.highScore,
                selectedTenses = it.selectedTenses,
                infoUsesRemaining = infoUses
            )
        }

        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                _uiState.update { it.copy(countdown = i) }
                delay(800)
            }
            _uiState.update { it.copy(countdown = null, isPlaying = true) }
            gameStartTime = System.currentTimeMillis()
            timerDeadline = System.currentTimeMillis() + GAME_TIME_MS
            _uiState.update {
                it.copy(timeRemainingMs = GAME_TIME_MS, timerFraction = 1f)
            }
            showNextQuestion()
            startTimer()
        }
    }

    private fun showNextQuestion() {
        if (verbPool.isEmpty()) {
            verbPool = allVerbs.shuffled().toMutableList()
        }

        // Mood question: ~25% chance when subjonctif + present both active
        if ("subjonctif" in activeTenses && "present" in activeTenses && Math.random() < 0.25) {
            if (tryShowMoodQuestion()) return
        }

        val verb = verbPool.removeFirst()
        val verbIpa = vocabRepository.getIpaForFrench(verb.infinitive)

        // Pick tense using error-weighted random selection
        val tense = pickWeightedTense(verb)
        if (tense == null) {
            // This verb doesn't have any of the selected tenses, skip
            showNextQuestion()
            return
        }

        val forms = verb.tenses[tense] ?: return

        // Pick a random person (that has a non-empty form)
        val eligiblePersons = if (tense == "imperatif") {
            IMPERATIF_PERSONS.filter { idx -> idx < forms.size && forms[idx].isNotEmpty() }
        } else {
            forms.indices.filter { forms[it].isNotEmpty() }
        }

        if (eligiblePersons.isEmpty()) {
            showNextQuestion()
            return
        }

        val personIdx = eligiblePersons.random()
        val correctForm = forms[personIdx]

        // Build distractors
        val distractors: List<String>
        val auxiliaryHint: String?
        val pcQuestionType: String?
        val participleShown: String?

        if (tense == "passe_compose") {
            // Passé composé: randomly alternate between two question types
            val questionType = if (Math.random() < 0.5) "auxiliary" else "verb_form"

            if (questionType == "auxiliary") {
                // AUXILIARY QUESTION: player picks the correct auxiliary form (ai/suis/as/est/etc.)
                // The participle is shown in the prompt
                val aux = verb.auxiliary ?: "avoir"
                val correctAuxForm = getAuxiliaryForm(personIdx, aux)

                // Distractors: mix forms from the WRONG auxiliary + wrong person forms
                val auxDistractorPool = mutableSetOf<String>()
                // Add all forms of the other auxiliary
                val otherAux = if (aux == "etre") "avoir" else "etre"
                for (pi in 0..5) {
                    auxDistractorPool.add(getAuxiliaryForm(pi, otherAux))
                }
                // Add wrong-person forms of the correct auxiliary
                for (pi in 0..5) {
                    if (pi != personIdx) {
                        auxDistractorPool.add(getAuxiliaryForm(pi, aux))
                    }
                }
                auxDistractorPool.remove(correctAuxForm)

                distractors = auxDistractorPool.shuffled().take(NUM_CHOICES - 1)
                auxiliaryHint = null  // not used for auxiliary questions (UI uses pcQuestionType)
                pcQuestionType = "auxiliary"
                participleShown = correctForm // the participle (e.g., "mangé")

                if (distractors.isEmpty()) {
                    showNextQuestion()
                    return
                }

                val allChoices = (listOf(correctAuxForm) + distractors).shuffled()
                val correctIdx = allChoices.indexOf(correctAuxForm)

                Log.d("Conjuguez", "AUX Q: ${verb.infinitive} person=$personIdx " +
                    "label=${PERSON_LABELS[personIdx]} aux=${verb.auxiliary} " +
                    "correctAux=$correctAuxForm participle=$correctForm")

                val auxMode = if (Math.random() < 0.2) QuestionMode.TYPE else QuestionMode.PICK
                if (auxMode == QuestionMode.TYPE) frenchTts.speak(verb.infinitive)

                _uiState.update {
                    it.copy(
                        infinitive = verb.infinitive,
                        german = verb.german,
                        ipa = verbIpa,
                        tenseName = TENSE_DISPLAY[tense] ?: tense,
                        personLabel = PERSON_LABELS[personIdx],
                        pcQuestionType = pcQuestionType,
                        participleShown = participleShown,
                        auxiliaryHint = null,
                        sentenceFr = null,
                        sentenceDe = null,
                        choices = allChoices,
                        correctIndex = correctIdx,
                        selectedIndex = null,
                        feedback = null,
                        questionMode = auxMode,
                        typedText = "",
                        isMoodQuestion = false,
                        awaitingNext = false,
                    )
                }
                return

            } else {
                // VERB FORM QUESTION: player picks the correct PC form vs forms from other tenses
                // Distractors come from the same verb's OTHER tenses (same person)
                val distractorPool = mutableSetOf<String>()
                for ((otherTense, otherForms) in verb.tenses) {
                    if (otherTense == "passe_compose") continue
                    if (personIdx < otherForms.size && otherForms[personIdx].isNotEmpty()) {
                        val form = otherForms[personIdx]
                        if (form.lowercase() != correctForm.lowercase()) {
                            distractorPool.add(form)
                        }
                    }
                }
                // If same-person distractors are insufficient, add other persons' forms from other tenses
                if (distractorPool.size < NUM_CHOICES - 1) {
                    for ((otherTense, otherForms) in verb.tenses) {
                        if (otherTense == "passe_compose") continue
                        for (f in otherForms) {
                            if (f.isNotEmpty() && f.lowercase() != correctForm.lowercase()) {
                                distractorPool.add(f)
                            }
                        }
                    }
                }
                distractors = distractorPool.shuffled().take(NUM_CHOICES - 1)
                pcQuestionType = "verb_form"
                participleShown = null

                // For verb_form, show auxiliary in the prompt so player knows context
                val aux = verb.auxiliary ?: "avoir"
                auxiliaryHint = buildAuxiliaryHint(personIdx, aux)
            }
        } else {
            // Other tenses: prefer same-person, different-tense distractors (excludes passé composé)
            // This forces the player to distinguish tense endings rather than just recognizing person patterns
            val distractorPool = mutableSetOf<String>()

            // Phase 1: same person, different tenses (not passé composé)
            for ((otherTense, otherForms) in verb.tenses) {
                if (otherTense == tense || otherTense == "passe_compose") continue
                if (personIdx < otherForms.size && otherForms[personIdx].isNotEmpty()) {
                    val form = otherForms[personIdx]
                    if (form.lowercase() != correctForm.lowercase()) {
                        distractorPool.add(form)
                    }
                }
            }

            // Phase 2 fallback: if not enough same-person distractors, add different persons from other tenses
            if (distractorPool.size < NUM_CHOICES - 1) {
                for ((otherTense, otherForms) in verb.tenses) {
                    if (otherTense == "passe_compose") continue
                    for (f in otherForms) {
                        if (f.isNotEmpty() && f.lowercase() != correctForm.lowercase()) {
                            distractorPool.add(f)
                        }
                    }
                }
            }

            distractors = distractorPool.shuffled().take(NUM_CHOICES - 1)
            auxiliaryHint = null
            pcQuestionType = null
            participleShown = null
        }

        // If not enough distractors, skip this question
        if (distractors.isEmpty()) {
            showNextQuestion()
            return
        }

        val allChoices = (listOf(correctForm) + distractors).shuffled()
        val correctIdx = allChoices.indexOf(correctForm)

        // Check for matching sentence
        val sentenceMatch = verb.sentences
            ?.get(tense)
            ?.get(personIdx.toString())

        val sentenceFr = if (sentenceMatch != null) {
            sentenceMatch.fr.replace(sentenceMatch.blank, "___")
        } else null

        Log.d("Conjuguez", "Q: ${verb.infinitive} tense=$tense person=$personIdx " +
            "label=${PERSON_LABELS[personIdx]} correct=$correctForm " +
            "choices=${allChoices.joinToString()} correctIdx=$correctIdx")

        // Pick question mode: 20% chance of TYPE
        val mode = if (Math.random() < 0.2) QuestionMode.TYPE else QuestionMode.PICK
        if (mode == QuestionMode.TYPE) frenchTts.speak(verb.infinitive)

        _uiState.update {
            it.copy(
                infinitive = verb.infinitive,
                german = verb.german,
                ipa = verbIpa,
                tenseName = TENSE_DISPLAY[tense] ?: tense,
                personLabel = PERSON_LABELS[personIdx],
                pcQuestionType = pcQuestionType,
                participleShown = participleShown,
                auxiliaryHint = auxiliaryHint,
                sentenceFr = sentenceFr,
                sentenceDe = sentenceMatch?.de,
                choices = allChoices,
                correctIndex = correctIdx,
                selectedIndex = null,
                feedback = null,
                questionMode = mode,
                typedText = "",
                isMoodQuestion = false,
                awaitingNext = false,
            )
        }
    }

    /**
     * Build the auxiliary hint for passé composé drill view.
     * Shows the conjugated auxiliary (avoir/être) for the given person.
     */
    private fun buildAuxiliaryHint(personIdx: Int, auxiliary: String): String {
        val avoirForms = listOf("j'ai", "tu as", "il/elle a", "nous avons", "vous avez", "ils/elles ont")
        val etreForms = listOf("je suis", "tu es", "il/elle est", "nous sommes", "vous êtes", "ils/elles sont")

        val forms = if (auxiliary == "etre") etreForms else avoirForms
        return if (personIdx in forms.indices) forms[personIdx] else forms[0]
    }

    /**
     * Get just the auxiliary verb form (without pronoun) for a given person.
     * Used as answer choices in auxiliary questions.
     */
    private fun getAuxiliaryForm(personIdx: Int, auxiliary: String): String {
        val avoirForms = listOf("ai", "as", "a", "avons", "avez", "ont")
        val etreForms = listOf("suis", "es", "est", "sommes", "êtes", "sont")

        val forms = if (auxiliary == "etre") etreForms else avoirForms
        return if (personIdx in forms.indices) forms[personIdx] else forms[0]
    }

    /**
     * Pick a tense from the active set using error-weighted random selection.
     * Each tense gets a base weight of 1. Each error on a tense adds
     * [ERROR_WEIGHT_BONUS] extra weight, so tenses the player struggles with
     * appear more often.
     *
     * Returns null if the verb has none of the active tenses.
     */
    private fun pickWeightedTense(verb: ConjugationVerb): String? {
        val eligibleTenses = verb.tenses.keys.filter { it in activeTenses }
        if (eligibleTenses.isEmpty()) return null

        // Build weighted list
        val weighted = eligibleTenses.flatMap { tense ->
            val weight = 1 + (tenseErrorCounts[tense] ?: 0) * ERROR_WEIGHT_BONUS
            List(weight) { tense }
        }

        return weighted.random()
    }

    fun answer(choiceIndex: Int) {
        val state = _uiState.value
        if (!state.isPlaying || state.feedback != null) return

        val isCorrect = choiceIndex == state.correctIndex

        // Build appropriate question text for the answer log
        val questionText = if (state.pcQuestionType == "auxiliary") {
            "${state.infinitive} (${state.tenseName}, ${state.personLabel}) — avoir ou être?"
        } else {
            "${state.infinitive} (${state.tenseName}, ${state.personLabel})"
        }

        answerLog.add(
            AnswerRecord(
                question = questionText,
                yourAnswer = state.choices[choiceIndex],
                correctAnswer = state.choices[state.correctIndex],
                isCorrect = isCorrect
            )
        )

        // Always speak the correct form (learn from mistakes too)
        // For auxiliary questions, speak the full PC form (auxiliary + participle)
        if (state.pcQuestionType == "auxiliary" && state.participleShown != null) {
            frenchTts.speak("${state.choices[state.correctIndex]} ${state.participleShown}")
        } else {
            frenchTts.speak(state.choices[state.correctIndex])
        }

        // Find the current tense key for error tracking
        val currentTenseKey = TENSE_DISPLAY.entries
            .firstOrNull { it.value == state.tenseName }?.key

        if (isCorrect) {
            val newStreak = state.streak + 1
            val streakBonus = if (newStreak >= 2) (newStreak - 1) * STREAK_BONUS else 0
            val pointsGained = POINTS_PER_CORRECT + streakBonus

            // Add time bonus, capped at MAX_TIME_MS
            val now = System.currentTimeMillis()
            val maxDeadline = now + MAX_TIME_MS
            timerDeadline = (timerDeadline + CORRECT_BONUS_MS).coerceAtMost(maxDeadline)

            _uiState.update {
                it.copy(
                    selectedIndex = choiceIndex,
                    feedback = true,
                    score = it.score + pointsGained,
                    totalCorrect = it.totalCorrect + 1,
                    streak = newStreak,
                    bestStreak = maxOf(it.bestStreak, newStreak)
                )
            }
        } else {
            // Track error for weighted tense selection
            if (currentTenseKey != null) {
                tenseErrorCounts[currentTenseKey] =
                    (tenseErrorCounts[currentTenseKey] ?: 0) + 1
            }

            timerDeadline -= WRONG_PENALTY_MS

            _uiState.update {
                it.copy(
                    selectedIndex = choiceIndex,
                    feedback = false,
                    totalWrong = it.totalWrong + 1,
                    streak = 0
                )
            }

            if (timerDeadline <= System.currentTimeMillis()) {
                timerJob?.cancel()
                _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                viewModelScope.launch {
                    delay(1200)
                    endGame()
                }
                return
            }
        }

        // Wait for player to tap "Next" (manual advance)
        _uiState.update { it.copy(awaitingNext = true) }
    }

    /**
     * Called when the player taps "Next" after seeing feedback.
     */
    fun nextQuestion() {
        if (!_uiState.value.awaitingNext) return
        _uiState.update { it.copy(awaitingNext = false) }
        showNextQuestion()
    }

    /**
     * Attempt to show a mood-recognition question (subjonctif vs indicatif présent).
     * Returns true if a suitable question was generated, false if no candidate found.
     */
    private fun tryShowMoodQuestion(): Boolean {
        val candidate = allVerbs.shuffled().firstOrNull { verb ->
            val pForms = verb.tenses["present"] ?: return@firstOrNull false
            val sForms = verb.tenses["subjonctif"] ?: return@firstOrNull false
            pForms.indices.any { idx ->
                idx < sForms.size &&
                pForms[idx].isNotEmpty() && sForms[idx].isNotEmpty() &&
                pForms[idx].lowercase() != sForms[idx].lowercase()
            }
        } ?: return false

        val pForms = candidate.tenses["present"]!!
        val sForms = candidate.tenses["subjonctif"]!!

        val eligiblePersons = pForms.indices.filter { idx ->
            idx < sForms.size &&
            pForms[idx].isNotEmpty() && sForms[idx].isNotEmpty() &&
            pForms[idx].lowercase() != sForms[idx].lowercase()
        }
        if (eligiblePersons.isEmpty()) return false
        val personIdx = eligiblePersons.random()

        val correctIsSub = Math.random() < 0.5
        val correctForm = if (correctIsSub) sForms[personIdx] else pForms[personIdx]
        val otherMoodForm = if (correctIsSub) pForms[personIdx] else sForms[personIdx]
        val correctTense = if (correctIsSub) "subjonctif" else "present"

        val distractorPool = mutableSetOf(otherMoodForm)
        for ((otherTense, otherForms) in candidate.tenses) {
            if (otherTense == "subjonctif" || otherTense == "present" || otherTense == "passe_compose") continue
            if (personIdx < otherForms.size && otherForms[personIdx].isNotEmpty()) {
                val f = otherForms[personIdx]
                if (f.lowercase() != correctForm.lowercase() && f.lowercase() != otherMoodForm.lowercase()) {
                    distractorPool.add(f)
                }
            }
        }
        val distractors = distractorPool.take(NUM_CHOICES - 1).toList()
        if (distractors.isEmpty()) return false

        val allChoices = (listOf(correctForm) + distractors).shuffled()
        val correctIdx = allChoices.indexOf(correctForm)

        val sentence = candidate.sentences?.get(correctTense)?.get(personIdx.toString())
        val personLabel = PERSON_LABELS[personIdx]
        val (sentenceFr, sentenceDe) = if (sentence != null) {
            sentence.fr.replace(sentence.blank, "___") to sentence.de
        } else {
            // No real sentence — generate a trigger phrase so the player has mood context
            val que = if (personLabel.startsWith("il") || personLabel.startsWith("ils")) "qu'" else "que "
            if (correctTense == "subjonctif") {
                "Il faut $que$personLabel ___." to "Es ist nötig, dass …"
            } else {
                "Je sais $que$personLabel ___." to "Ich weiß, dass …"
            }
        }
        val verbIpa = vocabRepository.getIpaForFrench(candidate.infinitive)

        val mode = if (Math.random() < 0.2) QuestionMode.TYPE else QuestionMode.PICK
        if (mode == QuestionMode.TYPE) frenchTts.speak(candidate.infinitive)

        _uiState.update {
            it.copy(
                infinitive = candidate.infinitive,
                german = candidate.german,
                ipa = verbIpa,
                tenseName = "Subjonctif ou Indicatif?",
                personLabel = personLabel,
                pcQuestionType = null,
                participleShown = null,
                auxiliaryHint = null,
                sentenceFr = sentenceFr,
                sentenceDe = sentenceDe,
                choices = allChoices,
                correctIndex = correctIdx,
                selectedIndex = null,
                feedback = null,
                questionMode = mode,
                typedText = "",
                isMoodQuestion = true,
                awaitingNext = false
            )
        }
        return true
    }

    fun onTypedTextChanged(text: String) {
        if (_uiState.value.feedback != null) return
        _uiState.update { it.copy(typedText = text) }
    }

    fun submitTyped() {
        val state = _uiState.value
        if (!state.isPlaying || state.feedback != null) return
        if (state.questionMode != QuestionMode.TYPE) return
        if (state.typedText.isBlank()) return

        val correctForm = state.choices[state.correctIndex]
        val isCorrect = normalize(state.typedText) == normalize(correctForm)

        val questionText = "${state.infinitive} (${state.tenseName}, ${state.personLabel})"
        answerLog.add(AnswerRecord(
            question = questionText,
            yourAnswer = state.typedText.trim(),
            correctAnswer = correctForm,
            isCorrect = isCorrect
        ))

        frenchTts.speak(correctForm)

        val currentTenseKey = TENSE_DISPLAY.entries.firstOrNull { it.value == state.tenseName }?.key

        if (isCorrect) {
            val newStreak = state.streak + 1
            val streakBonus = if (newStreak >= 2) (newStreak - 1) * STREAK_BONUS else 0
            val now = System.currentTimeMillis()
            val maxDeadline = now + MAX_TIME_MS
            timerDeadline = (timerDeadline + CORRECT_BONUS_MS).coerceAtMost(maxDeadline)
            _uiState.update {
                it.copy(
                    feedback = true,
                    score = it.score + POINTS_PER_CORRECT + streakBonus,
                    totalCorrect = it.totalCorrect + 1,
                    streak = newStreak,
                    bestStreak = maxOf(it.bestStreak, newStreak)
                )
            }
        } else {
            if (currentTenseKey != null) {
                tenseErrorCounts[currentTenseKey] = (tenseErrorCounts[currentTenseKey] ?: 0) + 1
            }
            timerDeadline -= WRONG_PENALTY_MS
            _uiState.update {
                it.copy(
                    feedback = false,
                    totalWrong = it.totalWrong + 1,
                    streak = 0
                )
            }
            if (timerDeadline <= System.currentTimeMillis()) {
                timerJob?.cancel()
                _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                viewModelScope.launch {
                    delay(1200)
                    endGame()
                }
                return
            }
        }
        _uiState.update { it.copy(awaitingNext = true) }
    }

    private fun normalize(text: String): String {
        val trimmed = text.trim().lowercase()
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        return decomposed.replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = timerDeadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    _uiState.update { it.copy(timeRemainingMs = 0, timerFraction = 0f) }
                    endGame()
                    break
                }
                _uiState.update {
                    it.copy(
                        timeRemainingMs = remaining,
                        timerFraction = (remaining.toFloat() / GAME_TIME_MS).coerceIn(0f, 1f)
                    )
                }
                delay(50)
            }
        }
    }

    /**
     * Pause the timer (e.g., when info dialog opens).
     */
    fun pauseTimer() {
        val remaining = timerDeadline - System.currentTimeMillis()
        if (remaining > 0) {
            timerPausedRemaining = remaining
            timerJob?.cancel()
        }
    }

    /**
     * Resume the timer (e.g., when info dialog closes).
     */
    fun resumeTimer() {
        if (timerPausedRemaining > 0) {
            timerDeadline = System.currentTimeMillis() + timerPausedRemaining
            timerPausedRemaining = 0L
            startTimer()
        }
    }

    /**
     * Try to use an info view for the given tense.
     * Returns true if allowed (uses remaining > 0), false if exhausted.
     */
    fun useInfoView(tenseKey: String): Boolean {
        val remaining = _uiState.value.infoUsesRemaining[tenseKey] ?: 0
        if (remaining <= 0) return false
        _uiState.update {
            it.copy(infoUsesRemaining = it.infoUsesRemaining + (tenseKey to (remaining - 1)))
        }
        return true
    }

    private fun endGame() {
        timerJob?.cancel()
        val state = _uiState.value
        val durationMs = System.currentTimeMillis() - gameStartTime

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore(gameType = "conjugation")
            val isNewHigh = state.score > previousHigh

            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalCorrect,
                roundsCompleted = state.bestStreak,
                gameType = "conjugation"
            )

            statisticsRepository.saveSession(
                gameType = "conjugation",
                score = state.score,
                totalCorrect = state.totalCorrect,
                totalWrong = state.totalWrong,
                bestStreak = state.bestStreak,
                durationMs = durationMs
            )

            statisticsRepository.saveWordAttempts("conjugation", answerLog)

            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isGameOver = true,
                    isNewHighScore = isNewHigh,
                    highScore = maxOf(it.highScore, state.score),
                    durationMs = durationMs,
                    answerHistory = answerLog.toList()
                )
            }
        }
    }

    fun resetToMenu() {
        countdownJob?.cancel()
        timerJob?.cancel()
        answerLog.clear()
        _uiState.value = ConjugationState()
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "conjugation")
            _uiState.update { it.copy(highScore = hs) }
        }
    }

    fun speak(text: String) {
        frenchTts.speak(text)
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        timerJob?.cancel()
    }
}
