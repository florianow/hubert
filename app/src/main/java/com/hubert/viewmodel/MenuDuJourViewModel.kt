package com.hubert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubert.data.local.MenuSessionDao
import com.hubert.data.local.WordAttemptDao
import com.hubert.data.model.MenuSession
import com.hubert.data.model.VocabWord
import com.hubert.data.repository.LearningOverview
import com.hubert.data.repository.LearningRepository
import com.hubert.data.repository.PinnedWordsRepository
import com.hubert.data.repository.StatisticsRepository
import com.hubert.data.repository.VocabRepository
import com.hubert.utils.FrenchTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Settings a game uses when it is played as one course of a Menu du jour:
 * shorter round, words restricted to [category] (null = no theme), [focusRanks] asked first.
 * Grammar games ignore category/focus words; [grammarFocus] (tense keys, prepositions or
 * pronouns) replaces the player's own selection for that round when not empty.
 */
data class StageConfig(
    val timeMs: Long,
    val category: String?,
    val focusRanks: List<Int>,
    val grammarFocus: Set<String> = emptySet()
)

/** A game that can be served as a course. [gameType] matches the GameSession keys. */
enum class CourseGame(
    val gameType: String,
    val gameName: String,
    val timeMs: Long,
    val targetCorrect: Int,     // correct answers needed for the full volume bonus
    val isVocab: Boolean        // vocab games follow the menu's theme and focus words
) {
    TROUVEZ("matching", "Trouvez!", 40_000L, 12, true),
    COMPLETEZ("gap_fill", "Complétez!", 45_000L, 8, true),
    CLASSEZ("gender_snap", "Classez!", 30_000L, 15, true),
    ECRIVEZ("spelling_bee", "Écrivez!", 45_000L, 6, true),
    CONJUGUEZ("conjugation", "Conjuguez!", 60_000L, 6, false),
    PREPOSEZ("preposition", "Préposez!", 60_000L, 8, false),
    RELATIVISEZ("relative_pronoun", "Relativisez!", 60_000L, 8, false),
    NOMBREZ("numbers", "Nombrez!", 45_000L, 8, false),
    ACCORDEZ("agreement", "Accordez!", 60_000L, 8, false);

    companion object {
        val GRAMMAR = listOf(CONJUGUEZ, PREPOSEZ, RELATIVISEZ, ACCORDEZ)
        /** Complet serves 2 of these 4 each day (theme part), a different pair daily. */
        val COMPLET_ROTATION = listOf(COMPLETEZ, CLASSEZ, ECRIVEZ, NOMBREZ)
    }
}

/** Express/Complet are split: first the player's own words, then the theme of the day. */
enum class MenuPart { LEARNING, THEME }

data class Course(
    val label: String,
    val goal: String,
    val game: CourseGame,
    val grammarFocus: Set<String> = emptySet(),
    val part: MenuPart = MenuPart.THEME
) {
    /** Key for stored course scores; the two Trouvez! courses (own words / theme) stay apart. */
    val scoreKey: String get() = if (part == MenuPart.LEARNING) "learning_${game.gameType}" else game.gameType
}

private val APERITIF = Course("Apéritif", "Neue & fällige Wörter wiedererkennen", CourseGame.TROUVEZ, part = MenuPart.LEARNING)
private val ENTREE = Course("Entrée", "Themenwörter kennenlernen", CourseGame.TROUVEZ)
private val PLAT = Course("Plat principal", "Wörter im Satz anwenden", CourseGame.COMPLETEZ)
private val FROMAGE = Course("Fromage", "Genus festigen: le ou la?", CourseGame.CLASSEZ)
private val DESSERT = Course("Dessert", "Selbst schreiben — deine Fehler kommen wieder", CourseGame.ECRIVEZ)
private val CAFE = Course("Café", "Zum Abschluss: Zahlen, Uhrzeit & Datum", CourseGame.NOMBREZ)

enum class MenuType(val title: String, val description: String) {
    EXPRESS("Express", "3 Gänge · ca. 2 Min · Thema des Tages"),
    COMPLET("Complet", "5 Gänge mit Grammatik · Spiele wechseln täglich · Thema des Tages"),
    A_LA_CARTE("À la carte", "4 Gänge · du wählst das Thema")
}

data class CourseResult(
    val course: Course,
    val score: Int,                 // 0–100
    val totalCorrect: Int,
    val totalWrong: Int,
    val answers: List<AnswerRecord>
)

/** A word answered wrong somewhere in the menu, shown in "À revoir". */
data class ReviewItem(
    val french: String,
    val german: String,
    val rank: Int,
    val courses: List<String>
)

/** A new word presented in the Amuse-bouche before the first course. */
data class DiscoveryCard(
    val word: VocabWord,
    val exampleFr: String?,
    val exampleDe: String?
)

/** A wrong answer from a non-vocab course (grammar, numbers) — no vocab word to pin. */
data class GrammarMistake(
    val gameName: String,
    val question: String,
    val yourAnswer: String,
    val correctAnswer: String
)

data class MenuDuJourState(
    val isRunning: Boolean = false,      // a menu is in progress (from type selection to L'addition)
    val isChoosingType: Boolean = false,
    val isChoosingCategory: Boolean = false,
    val isIntro: Boolean = false,        // showing the menu card before the first course
    val isBetweenCourses: Boolean = false,
    val isFinished: Boolean = false,     // showing L'addition

    val menuType: MenuType = MenuType.A_LA_CARTE,
    val availableCategories: List<String> = emptyList(),
    val dailyCategory: String = "",      // theme of the day for Express and Complet
    val completPreview: String = "",     // today's Complet line-up, e.g. "Trouvez! · Préposez! · …"
    val category: String = "",
    val courses: List<Course> = emptyList(),
    val focusCount: Int = 0,             // struggled/pinned words worked into the menu
    val dueCount: Int = 0,               // reviews due today (Express/Complet)

    // Amuse-bouche: new words of the day, presented before the first course
    val discoveryCards: List<DiscoveryCard> = emptyList(),
    val discoveryIndex: Int = 0,
    val isDiscovering: Boolean = false,
    val discoveryDone: Boolean = false,
    val currentIndex: Int = 0,           // index into [courses] of the next/current course
    val results: List<CourseResult> = emptyList(),

    // L'addition
    val overallScore: Int = 0,
    val previousOverallScore: Int? = null,
    val previousCourseScores: Map<String, Int> = emptyMap(),
    val review: List<ReviewItem> = emptyList(),
    val grammarMistakes: List<GrammarMistake> = emptyList(),
    val pinnedRanks: Set<Int> = emptySet(),
    val menusPlayed: Int = 0,

    // Learning path snapshot for the main menu strip
    val overview: LearningOverview? = null
) {
    val nextCourse: Course? get() = courses.getOrNull(currentIndex)
    val needsDiscovery: Boolean get() = discoveryCards.isNotEmpty() && !discoveryDone
    val currentCard: DiscoveryCard? get() = discoveryCards.getOrNull(discoveryIndex)
    val grade: Double get() = (overallScore / 5.0 * 2).roundToInt() / 2.0   // /20, in half points
}

/**
 * "Menu du jour": a guided session of several games in a row, built around one
 * thematic category plus the player's recently struggled words, ending with a
 * grade on the French /20 scale.
 *
 * Learning structure: recognise (Trouvez) → apply in context (Complétez) →
 * grammar (Complet only) → consolidate gender (Classez) → produce (Écrivez).
 * Words missed in earlier courses are fed into later ones.
 */
@HiltViewModel
class MenuDuJourViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val statisticsRepository: StatisticsRepository,
    private val pinnedWordsRepository: PinnedWordsRepository,
    private val menuSessionDao: MenuSessionDao,
    private val wordAttemptDao: WordAttemptDao,
    private val learningRepository: LearningRepository,
    private val frenchTts: FrenchTts
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuDuJourState())
    val uiState: StateFlow<MenuDuJourState> = _uiState.asStateFlow()

    // Express/Complet part 1: new words, due reviews, recent mistakes, then learned words
    private var learningRanks: List<Int> = emptyList()
    // À la carte: recent mistakes worked into the theme
    private var focusRanks: List<Int> = emptyList()
    private val discoveryKnown = mutableSetOf<Int>()
    private var startTime = 0L

    companion object {
        const val MIN_CATEGORY_NOUNS = 12
        const val MIN_CATEGORY_SENTENCES = 12
        const val MAX_FOCUS_WORDS = 8
        const val GRAMMAR_WINDOW_DAYS = 28L
        const val MIN_FOCUS_ATTEMPTS = 4
        const val MAX_DUE_PER_MENU = 30
        const val MAX_MENU_FOCUS = 45
    }

    init {
        viewModelScope.launch {
            pinnedWordsRepository.pinnedRanks.collect { ranks ->
                _uiState.update { it.copy(pinnedRanks = ranks) }
            }
        }
    }

    /** Entry point: let the player pick Express / Complet / À la carte. */
    fun showMenuSelection() {
        focusRanks = emptyList()
        val categories = eligibleCategories().sorted()
        _uiState.update {
            MenuDuJourState(
                isRunning = true,
                isChoosingType = true,
                availableCategories = categories,
                pinnedRanks = it.pinnedRanks,
                overview = it.overview
            )
        }
        viewModelScope.launch {
            val chef = Course("Plat du chef", "", dailyGrammarGame())
            _uiState.update {
                it.copy(
                    dailyCategory = dailyCategory(categories),
                    completPreview = completCourses(chef).joinToString(" · ") { c -> c.game.gameName }
                )
            }
        }
    }

    fun chooseMenuType(type: MenuType) {
        if (type == MenuType.A_LA_CARTE) {
            _uiState.update { it.copy(menuType = type, isChoosingType = false, isChoosingCategory = true) }
        } else {
            viewModelScope.launch {
                buildMenu(type, dailyCategory(_uiState.value.availableCategories))
            }
        }
    }

    fun chooseCategory(category: String) {
        viewModelScope.launch { buildMenu(MenuType.A_LA_CARTE, category) }
    }

    fun backToMenuTypes() {
        _uiState.update { it.copy(isChoosingCategory = false, isChoosingType = true) }
    }

    private suspend fun buildMenu(type: MenuType, category: String) {
        val struggled = loadFocusRanks()
        discoveryKnown.clear()
        var cards = emptyList<DiscoveryCard>()
        var due = emptyList<Int>()
        if (type == MenuType.A_LA_CARTE) {
            // À la carte stays thematic: theme words plus recent mistakes
            focusRanks = struggled
        } else {
            // Learning path first: new words of the day, due reviews, then recent mistakes;
            // the theme of the day fills whatever room is left in each course
            val newRanks = learningRepository.wordsToIntroduce()
            cards = newRanks.mapNotNull { rank ->
                val word = vocabRepository.getWordByRank(rank) ?: return@mapNotNull null
                val example = vocabRepository.getRandomSentence(rank)
                DiscoveryCard(word, example?.fr, example?.de)
            }
            due = learningRepository.dueRanks(MAX_DUE_PER_MENU)
            // Part 1 must never run dry: learned words fill up after new/due/mistakes
            learningRanks = (learningRepository.introducedToday() + newRanks + due + struggled +
                learningRepository.practiceRanks(MAX_MENU_FOCUS))
                .distinct()
                .take(MAX_MENU_FOCUS)
            focusRanks = emptyList()
        }
        val courses = when (type) {
            MenuType.EXPRESS -> listOf(APERITIF, ENTREE, DESSERT)
            MenuType.COMPLET -> {
                val grammar = dailyGrammarGame()
                val focus = grammarFocus(grammar)
                val goal = if (focus.isEmpty()) "Grammatik: deine eigene Auswahl"
                           else "Fokus: " + focus.joinToString(", ") { ConjuguezViewModel.TENSE_DISPLAY[it] ?: it }
                val chef = Course("Plat du chef", goal, grammar, focus)
                completCourses(chef)
            }
            MenuType.A_LA_CARTE -> listOf(ENTREE, PLAT, FROMAGE, DESSERT)
        }
        _uiState.update {
            it.copy(
                isChoosingType = false,
                isChoosingCategory = false,
                isIntro = true,
                menuType = type,
                category = category,
                courses = courses,
                focusCount = focusRanks.size,
                dueCount = due.size,
                discoveryCards = cards,
                discoveryIndex = 0,
                isDiscovering = false,
                discoveryDone = false
            )
        }
    }

    // ── Amuse-bouche ────────────────────────────────────────────────────

    fun startDiscovery() {
        _uiState.update { it.copy(isIntro = false, isDiscovering = true, discoveryIndex = 0) }
        speakCurrentCard()
    }

    fun speakCurrentCard() {
        _uiState.value.currentCard?.let { frenchTts.speak(it.word.french) }
    }

    /**
     * Answer for the current card. Returns true when the last card is done — the caller then
     * starts the first course. Known words are checked again in a week instead of today.
     */
    fun discoveryAnswer(alreadyKnown: Boolean): Boolean {
        val state = _uiState.value
        val card = state.currentCard ?: return true
        if (alreadyKnown) discoveryKnown += card.word.rank
        if (state.discoveryIndex + 1 < state.discoveryCards.size) {
            _uiState.update { it.copy(discoveryIndex = it.discoveryIndex + 1) }
            speakCurrentCard()
            return false
        }
        val ranks = state.discoveryCards.map { it.word.rank }
        val known = discoveryKnown.toSet()
        learningRanks = learningRanks - known
        viewModelScope.launch { learningRepository.introduce(ranks, known) }
        _uiState.update { it.copy(isDiscovering = false, discoveryDone = true) }
        return true
    }

    /** Config for the course that is about to be played. */
    fun configForNextCourse(): StageConfig? {
        val state = _uiState.value
        val course = state.nextCourse ?: return null
        if (state.currentIndex == 0) startTime = System.currentTimeMillis()
        // Mistakes come back in later courses of the same part only, so the theme part stays thematic
        // (part-1 mistakes are rescheduled for tomorrow by the spaced-repetition state anyway)
        val missedInPart = state.results
            .filter { it.course.part == course.part }
            .flatMap { r -> r.answers.filter { !it.isCorrect && it.rank >= 0 }.map { it.rank } }
        _uiState.update { it.copy(isIntro = false, isBetweenCourses = false) }
        return when (course.part) {
            MenuPart.LEARNING -> StageConfig(
                timeMs = course.game.timeMs,
                category = null,
                focusRanks = (missedInPart + learningRanks).distinct()
            )
            MenuPart.THEME -> StageConfig(
                timeMs = course.game.timeMs,
                category = state.category,
                focusRanks = (missedInPart + focusRanks).distinct(),
                grammarFocus = course.grammarFocus
            )
        }
    }

    /** Called by the navigation layer when the running game reports game over. */
    fun onCourseFinished(answers: List<AnswerRecord>) {
        val state = _uiState.value
        val course = state.nextCourse ?: return
        val correct = answers.count { it.isCorrect }
        val wrong = answers.size - correct
        val result = CourseResult(
            course = course,
            score = courseScore(course.game, correct, wrong),
            totalCorrect = correct,
            totalWrong = wrong,
            answers = answers
        )
        val results = state.results + result
        val nextIndex = state.currentIndex + 1
        _uiState.update { it.copy(results = results, currentIndex = nextIndex) }

        if (nextIndex >= state.courses.size) finishMenu(results)
        else _uiState.update { it.copy(isBetweenCourses = true) }
    }

    fun togglePin(rank: Int) {
        viewModelScope.launch { pinnedWordsRepository.togglePin(rank) }
    }

    /** Abort or leave the menu (also after L'addition). */
    fun reset() {
        focusRanks = emptyList()
        learningRanks = emptyList()
        _uiState.update { MenuDuJourState(pinnedRanks = it.pinnedRanks, overview = it.overview) }
    }

    /** Reload the learning-path numbers shown in the main menu. */
    fun refreshOverview() {
        viewModelScope.launch {
            val overview = learningRepository.overview()
            _uiState.update { it.copy(overview = overview) }
        }
    }

    // ── Scoring ─────────────────────────────────────────────────────────

    /** 80% accuracy + 20% volume (enough correct answers for the course). */
    private fun courseScore(game: CourseGame, correct: Int, wrong: Int): Int {
        val total = correct + wrong
        if (total == 0) return 0
        val accuracy = correct.toDouble() / total
        val volume = (correct.toDouble() / game.targetCorrect).coerceAtMost(1.0)
        return ((0.8 * accuracy + 0.2 * volume) * 100).roundToInt()
    }

    private fun finishMenu(results: List<CourseResult>) {
        val state = _uiState.value
        val overall = results.map { it.score }.average().roundToInt()
        val durationMs = System.currentTimeMillis() - startTime

        viewModelScope.launch {
            val previous = menuSessionDao.getLatest(state.menuType.name)
            menuSessionDao.insert(
                MenuSession(
                    menuType = state.menuType.name,
                    category = state.category,
                    overallScore = overall,
                    stageScores = results.joinToString(",") { "${it.course.scoreKey}:${it.score}" },
                    durationMs = durationMs
                )
            )
            _uiState.update {
                it.copy(
                    isBetweenCourses = false,
                    isFinished = true,
                    overallScore = overall,
                    previousOverallScore = previous?.overallScore,
                    previousCourseScores = previous?.let { p -> parseStageScores(p.stageScores) } ?: emptyMap(),
                    review = buildReview(results),
                    grammarMistakes = results.filter { !it.course.game.isVocab }.flatMap { r ->
                        r.answers.filter { !it.isCorrect }.map { a ->
                            GrammarMistake(r.course.game.gameName, a.question, a.yourAnswer, a.correctAnswer)
                        }
                    },
                    menusPlayed = menuSessionDao.getCount()
                )
            }
        }
    }

    private fun parseStageScores(raw: String): Map<String, Int> =
        raw.split(",").mapNotNull { entry ->
            val (type, score) = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            score.toIntOrNull()?.let { type to it }
        }.toMap()

    private fun buildReview(results: List<CourseResult>): List<ReviewItem> =
        results.flatMap { r ->
            r.answers.filter { !it.isCorrect && it.rank >= 0 }.map { it.rank to r.course.game.gameName }
        }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (rank, courses) ->
                val word = vocabRepository.getWordByRank(rank) ?: return@mapNotNull null
                ReviewItem(word.french, word.german, rank, courses.distinct())
            }

    // ── Menu planning ───────────────────────────────────────────────────

    /** Categories with enough nouns (Classez) and sentences (Complétez) to fill a menu. */
    private fun eligibleCategories(): List<String> {
        val sentenceRanks = vocabRepository.getWordsWithSentences().map { it.rank }.toSet()
        return vocabRepository.getCategories().keys.filter { cat ->
            val words = vocabRepository.getWordsInCategory(cat)
            words.count { it.gender != null && it.pos in listOf("nm", "nf") } >= MIN_CATEGORY_NOUNS &&
                words.count { it.rank in sentenceRanks } >= MIN_CATEGORY_SENTENCES
        }
    }

    /**
     * Always 5 courses: Apéritif (own words), then the theme part with Entrée (getting to know
     * the theme words), the grammar course and 2 of Complétez / Classez / Écrivez / Nombrez —
     * a different pair each day, served in menu order.
     */
    private fun completCourses(chef: Course): List<Course> {
        val rotation = CourseGame.COMPLET_ROTATION
        val first = (LocalDate.now().toEpochDay() % rotation.size).toInt()
        val today = setOf(rotation[first], rotation[(first + 1) % rotation.size])
        return listOf(APERITIF, ENTREE, chef) + listOf(PLAT, FROMAGE, DESSERT, CAFE).filter { it.game in today }
    }

    /**
     * Express: part 1 is the Entrée. Complet: the Entrée plus the next vocab course. All other
     * courses form the theme part, in their usual order. À la carte has no part 1.
     */
    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Theme of the day: the same for every Express/Complet menu today, a new one tomorrow.
     * Themes served in the last N days (N = number of themes) are skipped, so every theme
     * comes round once before any repeats. Today's menus don't count, keeping the pick stable.
     */
    private suspend fun dailyCategory(categories: List<String>): String {
        val today = LocalDate.now()
        val recent = menuSessionDao
            .getBetween(startOfDay(today.minusDays(categories.size.toLong())), startOfDay(today))
            .map { it.category }
            .toSet()
        val fresh = (categories - recent).ifEmpty { categories }.sorted()
        return fresh[Random(today.toEpochDay()).nextInt(fresh.size)]
    }

    /**
     * Grammar course of the day: today's Complet keeps whatever was served earlier today;
     * otherwise never the same game as the previous Complet, and among the rest the one with
     * the lowest accuracy over the last weeks (no recent attempts counts as weakest).
     */
    private suspend fun dailyGrammarGame(): CourseGame {
        val today = startOfDay(LocalDate.now())
        val servedToday = menuSessionDao.getBetween(today, Long.MAX_VALUE)
            .firstOrNull { it.menuType == MenuType.COMPLET.name }
            ?.let { grammarGameOf(it) }
        if (servedToday != null) return servedToday

        val previous = menuSessionDao.getLatestBefore(MenuType.COMPLET.name, today)?.let { grammarGameOf(it) }
        val since = startOfDay(LocalDate.now().minusDays(GRAMMAR_WINDOW_DAYS))
        return (CourseGame.GRAMMAR - setOfNotNull(previous)).minBy { game ->
            val attempts = wordAttemptDao.getAttemptsSince(game.gameType, since)
            if (attempts.isEmpty()) -1.0 else attempts.count { it.isCorrect }.toDouble() / attempts.size
        }
    }

    private fun grammarGameOf(session: MenuSession): CourseGame? =
        parseStageScores(session.stageScores).keys
            .firstNotNullOfOrNull { key -> CourseGame.GRAMMAR.find { it.gameType == key } }

    /**
     * What to practise inside the grammar game: the tenses (Conjuguez, top 2) or
     * prepositions/pronouns (top 4) with the highest recent error rate. Empty = keep the
     * player's own selection (too little data).
     */
    private suspend fun grammarFocus(game: CourseGame): Set<String> {
        val since = startOfDay(LocalDate.now().minusDays(GRAMMAR_WINDOW_DAYS))
        val errorRates = wordAttemptDao.getAttemptsSince(game.gameType, since)
            .mapNotNull { attempt -> focusKey(game, attempt.question, attempt.correctAnswer)?.let { it to attempt.isCorrect } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size >= MIN_FOCUS_ATTEMPTS }
            .mapValues { (_, results) -> results.count { !it }.toDouble() / results.size }
            .filterValues { it > 0 }
        val top = errorRates.entries.sortedByDescending { it.value }
            .take(if (game == CourseGame.CONJUGUEZ) 2 else 4)
            .map { it.key }
            .toSet()
        // Préposez/Relativisez need at least two forms to make a useful round
        return if (game == CourseGame.CONJUGUEZ || top.size >= 2) top else emptySet()
    }

    /** Conjuguez logs "voir (Conditionnel, vous)"; the others log the form as correct answer. */
    private fun focusKey(game: CourseGame, question: String, correctAnswer: String): String? = when (game) {
        CourseGame.CONJUGUEZ -> {
            val display = Regex("""\(([^,]+),""").find(question)?.groupValues?.get(1)?.trim()
            ConjuguezViewModel.TENSE_DISPLAY.entries
                .firstOrNull { it.value == display && it.key != "mood_question" }
                ?.key
        }
        CourseGame.PREPOSEZ -> correctAnswer.takeIf { it in PreposezViewModel.ALL_PREPOSITIONS }
        CourseGame.RELATIVISEZ -> correctAnswer.takeIf { it in RelativisezViewModel.ALL_PRONOUNS }
        else -> null
    }

    /** Recently struggled words (any game) plus a few pinned words, resolved to vocab ranks. */
    private suspend fun loadFocusRanks(): List<Int> {
        val byFrench: Map<String, VocabWord> = vocabRepository.getAllWords().associateBy { it.french }
        val struggled = statisticsRepository.getRecentlyStruggledWords(daysBack = 14, limit = 30)
            .mapNotNull { s ->
                // Écrivez/Complétez log the French word as the answer, the others as the question
                val french = when (s.gameType) {
                    "spelling_bee", "gap_fill" -> s.correctAnswer
                    "gender_snap" -> s.question
                    "matching" -> s.question
                    else -> null
                }
                french?.let { byFrench[it]?.rank }
            }
        val pinned = pinnedWordsRepository.pinnedRanks.first().shuffled()
        return (struggled + pinned).distinct().take(MAX_FOCUS_WORDS)
    }
}
