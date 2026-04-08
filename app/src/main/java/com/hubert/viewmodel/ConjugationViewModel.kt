package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.model.ConjugationVerb
import com.hubert.data.repository.HighScoreRepository
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
    val examples: List<Pair<String, String>> = emptyList()
)

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
    val tenseName: String = "",        // Display name: "Présent", "Imparfait", etc.
    val personLabel: String = "",      // "je", "tu", "il/elle", etc.

    // Sentence context (null = use plain drill view)
    val sentenceFr: String? = null,    // French sentence with blank
    val sentenceDe: String? = null,    // German translation

    // Multiple choice
    val choices: List<String> = emptyList(),
    val correctIndex: Int = -1,

    // Feedback
    val selectedIndex: Int? = null,
    val feedback: Boolean? = null,     // true=correct, false=wrong

    // Scoring — points-based: start at 10, +3 correct (+streak), -5 wrong, game over at 0
    val points: Int = STARTING_POINTS,
    val score: Int = 0,
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val highScore: Int = 0,

    // Countdown
    val countdown: Int? = null
) {
    companion object {
        const val STARTING_POINTS = 10
    }
}

@HiltViewModel
class ConjugationViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val highScoreRepository: HighScoreRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConjugationState())
    val uiState: StateFlow<ConjugationState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    private var verbPool: MutableList<ConjugationVerb> = mutableListOf()
    private var activeTenses: Set<String> = setOf("present")

    // Error-weighted tense tracking: count of wrong answers per tense
    private var tenseErrorCounts: MutableMap<String, Int> = mutableMapOf()

    companion object {
        const val POINTS_PER_CORRECT = 3
        const val STREAK_BONUS = 1
        const val WRONG_PENALTY = 5
        const val NUM_CHOICES = 4

        // Error weight: each error on a tense adds this much extra selection weight
        const val ERROR_WEIGHT_BONUS = 2

        // Tense keys → display names
        val TENSE_DISPLAY = mapOf(
            "present" to "Présent",
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
                    TenseSection("Gegenwärtiges Ereignis", examples = listOf(
                        "Sophie et Lucas préparent le dîner." to "Sophie und Lucas bereiten das Abendessen zu."
                    )),
                    TenseSection("Andauernde Handlung mit „depuis“", examples = listOf(
                        "Emma étudie le français depuis deux ans." to "Emma lernt seit zwei Jahren Französisch."
                    )),
                    TenseSection("Nahe Zukunft", examples = listOf(
                        "Nous commençons notre nouveau projet la semaine prochaine." to "Wir beginnen nächste Woche mit unserem neuen Projekt."
                    )),
                    TenseSection("Gewohnheiten und allgemeine Tatsachen", examples = listOf(
                        "Les Martin font du sport tous les weekends." to "Die Martins treiben jedes Wochenende Sport.",
                        "La patience est une vertu." to "Geduld ist eine Tugend."
                    )),
                    TenseSection("Realisierbare Bedingung nach „si“", examples = listOf(
                        "Si nous prenons le train de 8h, nous arriverons à temps pour la réunion." to "Wenn wir den Zug um 8 Uhr nehmen, kommen wir rechtzeitig zum Meeting an."
                    )),
                    TenseSection("Historisches Präsens", description = "In Erzählungen kann das Präsens vergangene Ereignisse lebendiger darstellen.", examples = listOf(
                        "Je regardais la télévision. Soudain, on sonne à la porte." to "Ich schaute fern. Plötzlich klingelt es an der Tür."
                    ))
                )
            ),

            "imparfait" to TenseInfo(
                description = "Das imparfait ist eine einfache Vergangenheitsform. Es beschreibt Hintergründe, Situationen und Handlungen, die zeitlich nicht genau begrenzt sind.",
                sections = listOf(
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
                description = "Der subjonctif wird für subjektive Aussagen und Willensäußerungen verwendet, während der Indikativ Tatsachen beschreibt. Er steht meist in Nebensätzen mit „que“.",
                sections = listOf(
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

            "imperatif" to TenseInfo(
                description = "Der impératif (Imperativ) drückt direkte Aufforderungen, Wünsche, Befehle, Bitten, Empfehlungen, Verbote und Ratschläge aus. Im Französischen gibt es ihn nur für tu, nous und vous.",
                sections = listOf(
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

        activeTenses = _uiState.value.selectedTenses
        tenseErrorCounts = mutableMapOf()
        verbPool = vocabRepository.getConjugations().shuffled().toMutableList()

        _uiState.update {
            ConjugationState(
                isPlaying = false,
                isTenseSelection = false,
                countdown = 3,
                points = ConjugationState.STARTING_POINTS,
                highScore = it.highScore,
                selectedTenses = it.selectedTenses  // preserve for next round
            )
        }

        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                _uiState.update { it.copy(countdown = i) }
                delay(800)
            }
            _uiState.update { it.copy(countdown = null, isPlaying = true) }
            showNextQuestion()
        }
    }

    private fun showNextQuestion() {
        if (verbPool.isEmpty()) {
            verbPool = vocabRepository.getConjugations().shuffled().toMutableList()
        }

        val verb = verbPool.removeFirst()

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

        // Build distractors: other forms of the SAME verb (different tenses/persons)
        val distractorPool = mutableSetOf<String>()
        for ((_, fs) in verb.tenses) {
            for (f in fs) {
                if (f.isNotEmpty() && f.lowercase() != correctForm.lowercase()) {
                    distractorPool.add(f)
                }
            }
        }

        val distractors = distractorPool.shuffled().take(NUM_CHOICES - 1)

        // If not enough distractors from same verb, this is rare but handle it
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

        _uiState.update {
            it.copy(
                infinitive = verb.infinitive,
                german = verb.german,
                tenseName = TENSE_DISPLAY[tense] ?: tense,
                personLabel = PERSON_LABELS[personIdx],
                sentenceFr = sentenceFr,
                sentenceDe = sentenceMatch?.de,
                choices = allChoices,
                correctIndex = correctIdx,
                selectedIndex = null,
                feedback = null
            )
        }
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

        // Always speak the correct conjugated form (learn from mistakes too)
        frenchTts.speak(state.choices[state.correctIndex])

        // Find the current tense key for error tracking
        val currentTenseKey = TENSE_DISPLAY.entries
            .firstOrNull { it.value == state.tenseName }?.key

        if (isCorrect) {
            val newStreak = state.streak + 1
            val streakBonus = if (newStreak >= 2) (newStreak - 1) * STREAK_BONUS else 0
            val pointsGained = POINTS_PER_CORRECT + streakBonus

            _uiState.update {
                it.copy(
                    selectedIndex = choiceIndex,
                    feedback = true,
                    points = it.points + pointsGained,
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

            val newPoints = (state.points - WRONG_PENALTY).coerceAtLeast(0)

            _uiState.update {
                it.copy(
                    selectedIndex = choiceIndex,
                    feedback = false,
                    points = newPoints,
                    totalWrong = it.totalWrong + 1,
                    streak = 0
                )
            }

            if (newPoints <= 0) {
                viewModelScope.launch {
                    delay(1200)
                    endGame()
                }
                return
            }
        }

        viewModelScope.launch {
            delay(if (isCorrect) 600 else 1200)
            if (_uiState.value.isPlaying) {
                showNextQuestion()
            }
        }
    }

    private fun endGame() {
        val state = _uiState.value

        viewModelScope.launch {
            val previousHigh = highScoreRepository.getHighestScore(gameType = "conjugation")
            val isNewHigh = state.score > previousHigh

            highScoreRepository.saveScore(
                score = state.score,
                matchesCompleted = state.totalCorrect,
                roundsCompleted = state.bestStreak,
                gameType = "conjugation"
            )

            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isGameOver = true,
                    isNewHighScore = isNewHigh,
                    highScore = maxOf(it.highScore, state.score)
                )
            }
        }
    }

    fun resetToMenu() {
        countdownJob?.cancel()
        viewModelScope.launch {
            val hs = highScoreRepository.getHighestScore(gameType = "conjugation")
            _uiState.value = ConjugationState(highScore = hs)
        }
    }

    fun speak(text: String) {
        frenchTts.speak(text)
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
