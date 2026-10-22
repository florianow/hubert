package com.hubert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.hubert.ui.screens.*
import com.hubert.ui.theme.HubertTheme
import com.hubert.viewmodel.ConjuguezViewModel
import com.hubert.viewmodel.CourseGame
import com.hubert.viewmodel.MenuDuJourViewModel
import com.hubert.viewmodel.NombrezViewModel
import com.hubert.viewmodel.AccordezViewModel
import com.hubert.viewmodel.WeeklyReportViewModel
import com.hubert.viewmodel.LernenViewModel
import com.hubert.viewmodel.DitesViewModel
import com.hubert.viewmodel.LernRundeViewModel
import com.hubert.viewmodel.LernRundeKind
import com.hubert.viewmodel.LernSpiel
import com.hubert.viewmodel.StageConfig
import com.hubert.viewmodel.TrouvezViewModel
import com.hubert.viewmodel.CompletezViewModel
import com.hubert.viewmodel.ClassezViewModel
import com.hubert.viewmodel.PreposezViewModel
import com.hubert.viewmodel.RelativisezViewModel
import com.hubert.viewmodel.PrononcezViewModel
import com.hubert.viewmodel.EcrivezViewModel
import com.hubert.viewmodel.ParlezViewModel
import com.hubert.viewmodel.SettingsViewModel
import com.hubert.viewmodel.StatisticsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HubertTheme {
                Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    HubertApp()
                }
            }
        }
    }
}

enum class Screen {
    MENU,
    TROUVEZ,
    CLASSEZ,
    COMPLETEZ,
    ECRIVEZ,
    CONJUGUEZ,
    PRONONCEZ,
    PREPOSEZ,
    RELATIVISEZ,
    NOMBREZ,
    ACCORDEZ,
    PARLEZ,
    MENU_DU_JOUR,
    LERNEN,
    DITES,
    STATISTICS,
    SETTINGS
}

@Composable
fun HubertApp() {
    val matchingVm: TrouvezViewModel = hiltViewModel()
    val genderSnapVm: ClassezViewModel = hiltViewModel()
    val gapFillVm: CompletezViewModel = hiltViewModel()
    val spellingBeeVm: EcrivezViewModel = hiltViewModel()
    val conjugationVm: ConjuguezViewModel = hiltViewModel()
    val pronunciationVm: PrononcezViewModel = hiltViewModel()
    val prepositionVm: PreposezViewModel = hiltViewModel()
    val relativisezVm: RelativisezViewModel = hiltViewModel()
    val nombrezVm: NombrezViewModel = hiltViewModel()
    val accordezVm: AccordezViewModel = hiltViewModel()
    val parlezVm: ParlezViewModel = hiltViewModel()
    val statisticsVm: StatisticsViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val menuVm: MenuDuJourViewModel = hiltViewModel()
    val weeklyVm: WeeklyReportViewModel = hiltViewModel()
    val lernenVm: LernenViewModel = hiltViewModel()
    val lernRundeVm: LernRundeViewModel = hiltViewModel()
    val ditesVm: DitesViewModel = hiltViewModel()

    val matchingState by matchingVm.uiState.collectAsState()
    val genderSnapState by genderSnapVm.uiState.collectAsState()
    val gapFillState by gapFillVm.uiState.collectAsState()
    val spellingBeeState by spellingBeeVm.uiState.collectAsState()
    val conjugationState by conjugationVm.uiState.collectAsState()
    val pronunciationState by pronunciationVm.uiState.collectAsState()
    val prepositionState by prepositionVm.uiState.collectAsState()
    val relativisezState by relativisezVm.uiState.collectAsState()
    val nombrezState by nombrezVm.uiState.collectAsState()
    val accordezState by accordezVm.uiState.collectAsState()
    val parlezState by parlezVm.uiState.collectAsState()
    val statisticsState by statisticsVm.uiState.collectAsState()
    val settingsState by settingsVm.settings.collectAsState()
    val ankiUiState by settingsVm.anki.collectAsState()
    val ankiLastSync by settingsVm.ankiLastSync.collectAsState()
    val menuState by menuVm.uiState.collectAsState()
    val weeklyState by weeklyVm.uiState.collectAsState()
    val lernenState by lernenVm.uiState.collectAsState()
    val lernRundeState by lernRundeVm.uiState.collectAsState()
    val ditesState by ditesVm.uiState.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.MENU) }

    // "Menu du jour" — start the next course with the menu's stage config
    val startNextCourse: () -> Unit = {
        val course = menuVm.uiState.value.nextCourse
        val config = menuVm.configForNextCourse()
        if (course != null && config != null) {
            when (course.game) {
                CourseGame.TROUVEZ -> matchingVm.startGame(config)
                CourseGame.COMPLETEZ -> gapFillVm.startGame(config)
                CourseGame.CLASSEZ -> genderSnapVm.startGame(config)
                CourseGame.ECRIVEZ -> spellingBeeVm.startGame(config)
                CourseGame.CONJUGUEZ -> conjugationVm.startGame(config)
                CourseGame.PREPOSEZ -> prepositionVm.startGame(config)
                CourseGame.RELATIVISEZ -> relativisezVm.startGame(config)
                CourseGame.NOMBREZ -> nombrezVm.startGame(config)
                CourseGame.ACCORDEZ -> accordezVm.startGame(config)
            }
        }
    }

    // "Vokabeln lernen" — start the planned round in its game
    val startLearningRound: () -> Unit = {
        val round = lernenVm.uiState.value.nextRound
        if (round != null) {
            when (round.spiel) {
                LernSpiel.TROUVEZ -> matchingVm.startGame(
                    StageConfig(TrouvezViewModel.GAME_TIME_MS, category = null, focusRanks = round.ranks, learningRound = true)
                )
                LernSpiel.COMPLETEZ -> gapFillVm.startGame(
                    StageConfig(CompletezViewModel.GAME_TIME_MS, category = null, focusRanks = round.ranks, learningRound = true)
                )
                LernSpiel.ECOUTEZ -> lernRundeVm.start(LernRundeKind.LISTEN, round.ranks)
                LernSpiel.RAPPEL -> lernRundeVm.start(LernRundeKind.RECALL, round.ranks)
            }
        }
    }
    val startLearning: () -> Unit = {
        lernenVm.start()
        currentScreen = Screen.LERNEN
    }

    // Navigate based on game states
    LaunchedEffect(matchingState.isModeSelection, matchingState.isCategorySelection, matchingState.isPinSelection, matchingState.isPlaying, matchingState.isGameOver, matchingState.countdown) {
        if (matchingState.isModeSelection || matchingState.isCategorySelection || matchingState.isPinSelection || matchingState.countdown != null || matchingState.isPlaying || matchingState.isGameOver) {
            currentScreen = Screen.TROUVEZ
        }
    }

    LaunchedEffect(genderSnapState.isPlaying, genderSnapState.isGameOver, genderSnapState.countdown) {
        if (genderSnapState.countdown != null || genderSnapState.isPlaying || genderSnapState.isGameOver) {
            currentScreen = Screen.CLASSEZ
        }
    }

    LaunchedEffect(gapFillState.isPlaying, gapFillState.isGameOver, gapFillState.countdown) {
        if (gapFillState.countdown != null || gapFillState.isPlaying || gapFillState.isGameOver) {
            currentScreen = Screen.COMPLETEZ
        }
    }

    LaunchedEffect(spellingBeeState.isPlaying, spellingBeeState.isGameOver, spellingBeeState.countdown) {
        if (spellingBeeState.countdown != null || spellingBeeState.isPlaying || spellingBeeState.isGameOver) {
            currentScreen = Screen.ECRIVEZ
        }
    }

    LaunchedEffect(conjugationState.isTenseSelection, conjugationState.isPlaying, conjugationState.isGameOver, conjugationState.countdown) {
        if (conjugationState.isTenseSelection || conjugationState.countdown != null || conjugationState.isPlaying || conjugationState.isGameOver) {
            currentScreen = Screen.CONJUGUEZ
        }
    }

    LaunchedEffect(pronunciationState.isPlaying, pronunciationState.isGameOver, pronunciationState.countdown) {
        if (pronunciationState.countdown != null || pronunciationState.isPlaying || pronunciationState.isGameOver) {
            currentScreen = Screen.PRONONCEZ
        }
    }

    LaunchedEffect(prepositionState.isSelection, prepositionState.isPlaying, prepositionState.isGameOver, prepositionState.countdown) {
        if (prepositionState.isSelection || prepositionState.countdown != null || prepositionState.isPlaying || prepositionState.isGameOver) {
            currentScreen = Screen.PREPOSEZ
        }
    }

    LaunchedEffect(relativisezState.isSelection, relativisezState.isPlaying, relativisezState.isGameOver, relativisezState.countdown) {
        if (relativisezState.isSelection || relativisezState.countdown != null || relativisezState.isPlaying || relativisezState.isGameOver) {
            currentScreen = Screen.RELATIVISEZ
        }
    }

    LaunchedEffect(nombrezState.isSelection, nombrezState.isPlaying, nombrezState.isGameOver, nombrezState.countdown) {
        if (nombrezState.isSelection || nombrezState.countdown != null || nombrezState.isPlaying || nombrezState.isGameOver) {
            currentScreen = Screen.NOMBREZ
        }
    }

    LaunchedEffect(accordezState.isSelection, accordezState.isPlaying, accordezState.isGameOver, accordezState.countdown) {
        if (accordezState.isSelection || accordezState.countdown != null || accordezState.isPlaying || accordezState.isGameOver) {
            currentScreen = Screen.ACCORDEZ
        }
    }

    LaunchedEffect(
        parlezState.isTopicSelection,
        parlezState.isPlaying,
        parlezState.isEvaluating,
        parlezState.isGameOver
    ) {
        if (parlezState.isTopicSelection || parlezState.isPlaying ||
            parlezState.isEvaluating || parlezState.isGameOver) {
            currentScreen = Screen.PARLEZ
        }
    }

    // "Menu du jour" — a finished game hands its answers to the menu instead of showing GameOver
    LaunchedEffect(matchingState.isGameOver) {
        if (matchingState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(matchingState.answerHistory)
            matchingVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    LaunchedEffect(gapFillState.isGameOver) {
        if (gapFillState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(gapFillState.answerHistory)
            gapFillVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    LaunchedEffect(genderSnapState.isGameOver) {
        if (genderSnapState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(genderSnapState.answerHistory)
            genderSnapVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    LaunchedEffect(spellingBeeState.isGameOver) {
        if (spellingBeeState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(spellingBeeState.answerHistory)
            spellingBeeVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    LaunchedEffect(conjugationState.isGameOver) {
        if (conjugationState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(conjugationState.answerHistory)
            conjugationVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    LaunchedEffect(prepositionState.isGameOver) {
        if (prepositionState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(prepositionState.answerHistory)
            prepositionVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    LaunchedEffect(relativisezState.isGameOver) {
        if (relativisezState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(relativisezState.answerHistory)
            relativisezVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    LaunchedEffect(nombrezState.isGameOver) {
        if (nombrezState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(nombrezState.answerHistory)
            nombrezVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    LaunchedEffect(accordezState.isGameOver) {
        if (accordezState.isGameOver && menuVm.uiState.value.isRunning) {
            menuVm.onCourseFinished(accordezState.answerHistory)
            accordezVm.resetToMenu()
            currentScreen = Screen.MENU_DU_JOUR
        }
    }
    // "Vokabeln lernen" — finished rounds report back to the learning overview
    LaunchedEffect(matchingState.isGameOver) {
        if (matchingState.isGameOver && lernenVm.uiState.value.isRunning) {
            lernenVm.onRoundFinished(matchingState.answerHistory)
            matchingVm.resetToMenu()
            currentScreen = Screen.LERNEN
        }
    }
    LaunchedEffect(gapFillState.isGameOver) {
        if (gapFillState.isGameOver && lernenVm.uiState.value.isRunning) {
            lernenVm.onRoundFinished(gapFillState.answerHistory)
            gapFillVm.resetToMenu()
            currentScreen = Screen.LERNEN
        }
    }
    LaunchedEffect(lernRundeState.isFinished) {
        if (lernRundeState.isFinished && lernenVm.uiState.value.isRunning) {
            lernenVm.onRoundFinished(lernRundeState.answerHistory)
            lernRundeVm.reset()
        }
    }

    // Quitting any course (or the menu card) back to the main menu abandons the menu
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.MENU) menuVm.refreshOverview()
        if (currentScreen == Screen.MENU && lernenVm.uiState.value.isRunning) {
            lernenVm.stop()
            lernRundeVm.reset()
        }
        if (currentScreen == Screen.MENU && menuVm.uiState.value.isRunning) {
            menuVm.reset()
        }
    }

    // "Rapport de la semaine" — once per new week on app start, or opened from the menu card
    LaunchedEffect(Unit) { weeklyVm.showIfNewWeek() }
    if (weeklyState.isVisible) {
        WeeklyReportScreen(
            state = weeklyState,
            onPreviousWeek = { weeklyVm.previousWeek() },
            onNextWeek = { weeklyVm.nextWeek() },
            onClose = { weeklyVm.close() }
        )
        return
    }

    when (currentScreen) {
        Screen.DITES -> {
            DitesScreen(
                state = ditesState,
                onReveal = { ditesVm.reveal() },
                onListen = { ditesVm.listen() },
                onToggleRecording = { ditesVm.toggleRecording() },
                onRate = { ditesVm.rate(it) },
                onQuit = {
                    ditesVm.reset()
                    currentScreen = Screen.MENU
                }
            )
        }

        Screen.LERNEN -> {
            if (lernRundeState.isActive && !lernRundeState.isFinished) {
                LernRundeScreen(
                    state = lernRundeState,
                    onChoice = { lernRundeVm.answerChoice(it) },
                    onTyped = { lernRundeVm.onTypedTextChanged(it) },
                    onSubmit = { lernRundeVm.submitTyped() },
                    onNext = { lernRundeVm.next() },
                    onSpeak = { lernRundeVm.speak() },
                    onQuit = { currentScreen = Screen.MENU }
                )
            } else {
                LernenScreen(
                    state = lernenState,
                    onContinue = startLearningRound,
                    onContinuePastLimit = { lernenVm.continuePastLimit() },
                    onStop = { currentScreen = Screen.MENU }
                )
            }
        }

        Screen.MENU_DU_JOUR -> {
            when {
                menuState.isChoosingType -> {
                    MenuDuJourTypeScreen(
                        dailyCategory = menuState.dailyCategory,
                        completPreview = menuState.completPreview,
                        onChoose = { menuVm.chooseMenuType(it) },
                        onShowWeeklyReport = { weeklyVm.open(0) },
                        onBack = { currentScreen = Screen.MENU }
                    )
                }
                menuState.isChoosingCategory -> {
                    MenuDuJourCategoryScreen(
                        categories = menuState.availableCategories,
                        onChoose = { menuVm.chooseCategory(it) },
                        onBack = { menuVm.backToMenuTypes() }
                    )
                }
                menuState.isDiscovering -> {
                    MenuDuJourDiscoveryScreen(
                        state = menuState,
                        onAnswer = { known -> if (menuVm.discoveryAnswer(known)) startNextCourse() },
                        onSpeak = { menuVm.speakCurrentCard() },
                        onBack = { currentScreen = Screen.MENU }
                    )
                }
                menuState.isFinished -> {
                    MenuDuJourAdditionScreen(
                        state = menuState,
                        onTogglePin = { menuVm.togglePin(it) },
                        onNewMenu = { menuVm.showMenuSelection() },
                        onBackToMenu = {
                            menuVm.reset()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                menuState.isIntro || menuState.isBetweenCourses -> {
                    MenuDuJourCourseScreen(
                        state = menuState,
                        onContinue = {
                            if (menuVm.uiState.value.needsDiscovery) menuVm.startDiscovery() else startNextCourse()
                        },
                        onBack = { currentScreen = Screen.MENU }
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MenuWine)
                    }
                }
            }
        }

        Screen.SETTINGS -> {
            SettingsScreen(
                settings = settingsState,
                onSave = { gemini, azure, region ->
                    settingsVm.save(gemini, azure, region)
                    pronunciationVm.onSettingsSaved()
                    parlezVm.onSettingsSaved()
                    currentScreen = Screen.MENU
                },
                onReminderChange = { enabled, hour -> settingsVm.setReminder(enabled, hour) },
                onDailyLimitChange = { settingsVm.setDailyReviewLimit(it) },
                anki = ankiUiState,
                ankiLastSync = ankiLastSync,
                onAnkiEnable = { settingsVm.setAnkiEnabled(it) },
                onAnkiDeck = { settingsVm.chooseAnkiDeck(it) },
                onAnkiSync = { settingsVm.syncAnkiNow() },
                onAnkiRefresh = { settingsVm.refreshAnki() },
                onBack = { currentScreen = Screen.MENU }
            )
        }

        Screen.STATISTICS -> {
            StatisticsScreen(
                state = statisticsState,
                onSelectGameType = { statisticsVm.selectGameType(it) },
                onBack = {
                    currentScreen = Screen.MENU
                }
            )
        }

        Screen.TROUVEZ -> {
            when {
                matchingState.isModeSelection -> {
                    TrouvezModeScreen(
                        state = matchingState,
                        onThemenTraining = { matchingVm.showCategorySelection() },
                        onFreierModus = { matchingVm.showPinSelection() },
                        onBack = {
                            matchingVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                matchingState.isCategorySelection -> {
                    TrouvezCategoryScreen(
                        state = matchingState,
                        onSelectCategory = { matchingVm.selectCategory(it) },
                        onRandomCategory = { matchingVm.selectRandomCategory() },
                        onBack = { matchingVm.showModeSelection() }
                    )
                }
                matchingState.isPinSelection -> {
                    TrouvezPinScreen(
                        state = matchingState,
                        onSearch = { matchingVm.searchWords(it) },
                        onTogglePin = { matchingVm.togglePin(it) },
                        onStart = { matchingVm.startGame() },
                        onBack = {
                            matchingVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                matchingState.countdown != null -> {
                    CountdownScreen(
                        count = matchingState.countdown!!,
                        onBack = {
                            matchingVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                matchingState.isPlaying -> {
                    TrouvezScreen(
                        state = matchingState,
                        onSelectFrench = { matchingVm.selectFrench(it) },
                        onSelectGerman = { matchingVm.selectGerman(it) },
                        onQuit = {
                            matchingVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                matchingState.isGameOver && !menuState.isRunning && !lernenState.isRunning -> {
                    GameOverScreen(
                        title = "TIME'S UP!",
                        score = matchingState.score,
                        isNewHighScore = matchingState.isNewHighScore,
                        totalCorrect = matchingState.totalMatches,
                        totalWrong = 0,
                        bestStreak = matchingState.bestStreak,
                        highScore = matchingState.highScore,
                        durationMs = matchingState.durationMs,
                        answerHistory = matchingState.answerHistory,
                        pinnedRanks = matchingState.pinnedRanks,
                        onTogglePin = { matchingVm.togglePin(it) },
                        wordStreaks = matchingState.wordStreaks,
                        onPlayAgain = { matchingVm.startGame() },
                        onBackToMenu = {
                            matchingVm.resetToMenu()
                            currentScreen = Screen.MENU
                        },
                        onChooseCategory = if (matchingState.selectedCategory != null) {
                            { matchingVm.showCategorySelection() }
                        } else null,
                        onRandomCategory = if (matchingState.selectedCategory != null) {
                            { matchingVm.selectRandomCategory() }
                        } else null
                    )
                }
            }
        }

        Screen.CLASSEZ -> {
            when {
                genderSnapState.countdown != null -> {
                    CountdownScreen(
                        count = genderSnapState.countdown!!,
                        onBack = {
                            genderSnapVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                genderSnapState.isPlaying -> {
                    ClassezScreen(
                        state = genderSnapState,
                        onAnswer = { genderSnapVm.answer(it) },
                        onQuit = {
                            genderSnapVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                genderSnapState.isGameOver && !menuState.isRunning -> {
                    GameOverScreen(
                        title = "TIME'S UP!",
                        score = genderSnapState.score,
                        isNewHighScore = genderSnapState.isNewHighScore,
                        totalCorrect = genderSnapState.totalCorrect,
                        totalWrong = genderSnapState.totalWrong,
                        bestStreak = genderSnapState.bestStreak,
                        highScore = genderSnapState.highScore,
                        durationMs = genderSnapState.durationMs,
                        answerHistory = genderSnapState.answerHistory,
                        onPlayAgain = { genderSnapVm.startGame() },
                        onBackToMenu = {
                            genderSnapVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
            }
        }

        Screen.COMPLETEZ -> {
            when {
                gapFillState.countdown != null -> {
                    CountdownScreen(
                        count = gapFillState.countdown!!,
                        onBack = {
                            gapFillVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                gapFillState.isPlaying -> {
                    CompletezScreen(
                        state = gapFillState,
                        onAnswer = { gapFillVm.answer(it) },
                        onQuit = {
                            gapFillVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                gapFillState.isGameOver && !menuState.isRunning && !lernenState.isRunning -> {
                    GameOverScreen(
                        title = "TIME'S UP!",
                        score = gapFillState.score,
                        isNewHighScore = gapFillState.isNewHighScore,
                        totalCorrect = gapFillState.totalCorrect,
                        totalWrong = gapFillState.totalWrong,
                        bestStreak = gapFillState.bestStreak,
                        highScore = gapFillState.highScore,
                        durationMs = gapFillState.durationMs,
                        answerHistory = gapFillState.answerHistory,
                        onPlayAgain = { gapFillVm.startGame() },
                        onBackToMenu = {
                            gapFillVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
            }
        }

        Screen.ECRIVEZ -> {
            when {
                spellingBeeState.countdown != null -> {
                    CountdownScreen(
                        count = spellingBeeState.countdown!!,
                        onBack = {
                            spellingBeeVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                spellingBeeState.isPlaying -> {
                    EcrivezScreen(
                        state = spellingBeeState,
                        onTypedTextChanged = { spellingBeeVm.onTypedTextChanged(it) },
                        onSubmit = { spellingBeeVm.submit() },
                        onReplay = { spellingBeeVm.replayAudio() },
                        onQuit = {
                            spellingBeeVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                spellingBeeState.isGameOver && !menuState.isRunning -> {
                    GameOverScreen(
                        title = "TIME'S UP!",
                        score = spellingBeeState.score,
                        isNewHighScore = spellingBeeState.isNewHighScore,
                        totalCorrect = spellingBeeState.totalCorrect,
                        totalWrong = spellingBeeState.totalWrong,
                        bestStreak = spellingBeeState.bestStreak,
                        highScore = spellingBeeState.highScore,
                        durationMs = spellingBeeState.durationMs,
                        answerHistory = spellingBeeState.answerHistory,
                        onPlayAgain = { spellingBeeVm.startGame() },
                        onBackToMenu = {
                            spellingBeeVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
            }
        }

        Screen.CONJUGUEZ -> {
            when {
                conjugationState.isTenseSelection -> {
                    TenseSelectionScreen(
                        state = conjugationState,
                        onToggleTense = { conjugationVm.toggleTense(it) },
                        onStart = { conjugationVm.startGame() },
                        onBack = {
                            conjugationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                conjugationState.countdown != null -> {
                    CountdownScreen(
                        count = conjugationState.countdown!!,
                        onBack = {
                            conjugationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                conjugationState.isPlaying -> {
                    ConjuguezScreen(
                        state = conjugationState,
                        onAnswer = { conjugationVm.answer(it) },
                        onNext = { conjugationVm.nextQuestion() },
                        onSpeak = { conjugationVm.speak(it) },
                        onQuit = {
                            conjugationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        },
                        onPauseTimer = { conjugationVm.pauseTimer() },
                        onResumeTimer = { conjugationVm.resumeTimer() },
                        onUseInfoView = { conjugationVm.useInfoView(it) },
                        onTypedTextChanged = { conjugationVm.onTypedTextChanged(it) },
                        onSubmitTyped = { conjugationVm.submitTyped() },
                    )
                }
                conjugationState.isGameOver && !menuState.isRunning -> {
                    GameOverScreen(
                        title = "GAME OVER",
                        score = conjugationState.score,
                        isNewHighScore = conjugationState.isNewHighScore,
                        totalCorrect = conjugationState.totalCorrect,
                        totalWrong = conjugationState.totalWrong,
                        bestStreak = conjugationState.bestStreak,
                        highScore = conjugationState.highScore,
                        durationMs = conjugationState.durationMs,
                        answerHistory = conjugationState.answerHistory,
                        aiAnalysis = conjugationState.aiAnalysis,
                        isLoadingAnalysis = conjugationState.isLoadingAnalysis,
                        onPlayAgain = { conjugationVm.startGame() },
                        onBackToMenu = {
                            conjugationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        },
                        onChooseCategory = { conjugationVm.showTenseSelection() },
                        onChooseCategoryLabel = "🕐 Zeiten"
                    )
                }
            }
        }

        Screen.PRONONCEZ -> {
            // Redirect to global settings when API key is missing
            if (pronunciationState.showSettings) {
                currentScreen = Screen.SETTINGS
                pronunciationVm.dismissSettings()
            }

            when {
                pronunciationState.countdown != null -> {
                    CountdownScreen(
                        count = pronunciationState.countdown!!,
                        onBack = {
                            pronunciationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                pronunciationState.isPlaying -> {
                    PrononcezScreen(
                        state = pronunciationState,
                        onToggleRecording = { pronunciationVm.toggleRecording() },
                        onNext = { pronunciationVm.nextSentence() },
                        onRetry = { pronunciationVm.retryRecording() },
                        onSkipRetry = { pronunciationVm.skipRetry() },
                        onSpeak = { pronunciationVm.speak(it) },
                        onPlayRecording = { pronunciationVm.playRecording() },
                        onQuit = {
                            pronunciationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                pronunciationState.isGameOver -> {
                    PrononcezGameOverScreen(
                        score = pronunciationState.score,
                        isNewHighScore = pronunciationState.isNewHighScore,
                        runStats = pronunciationState.runStats,
                        onPlayAgain = { pronunciationVm.startGame() },
                        onBackToMenu = {
                            pronunciationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
            }
        }

        Screen.PREPOSEZ -> {
            when {
                prepositionState.isSelection -> {
                    PreposezSelectionScreen(
                        state = prepositionState,
                        onToggle = { prepositionVm.togglePreposition(it) },
                        onToggleGroup = { prepositionVm.toggleGroup(it) },
                        onStart = { prepositionVm.startGame() },
                        onBack = {
                            prepositionVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                prepositionState.countdown != null -> {
                    CountdownScreen(
                        count = prepositionState.countdown!!,
                        onBack = {
                            prepositionVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                prepositionState.isPlaying -> {
                    PreposezScreen(
                        state = prepositionState,
                        onAnswer = { prepositionVm.answer(it) },
                        onQuit = {
                            prepositionVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                prepositionState.isGameOver && !menuState.isRunning -> {
                    GameOverScreen(
                        title = "TIME'S UP!",
                        score = prepositionState.score,
                        isNewHighScore = prepositionState.isNewHighScore,
                        totalCorrect = prepositionState.totalCorrect,
                        totalWrong = prepositionState.totalWrong,
                        bestStreak = prepositionState.bestStreak,
                        highScore = prepositionState.highScore,
                        durationMs = prepositionState.durationMs,
                        answerHistory = prepositionState.answerHistory,
                        aiAnalysis = prepositionState.aiAnalysis,
                        isLoadingAnalysis = prepositionState.isLoadingAnalysis,
                        onPlayAgain = { prepositionVm.startGame() },
                        onBackToMenu = {
                            prepositionVm.resetToMenu()
                            currentScreen = Screen.MENU
                        },
                        onChooseCategory = { prepositionVm.showSelection() },
                        onChooseCategoryLabel = "🔄 Auswahl"
                    )
                }
            }
        }

        Screen.RELATIVISEZ -> {
            when {
                relativisezState.isSelection -> {
                    RelativisezSelectionScreen(
                        state = relativisezState,
                        onToggle = { relativisezVm.togglePronoun(it) },
                        onToggleGroup = { relativisezVm.toggleGroup(it) },
                        onStart = { relativisezVm.startGame() },
                        onBack = {
                            relativisezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                relativisezState.countdown != null -> {
                    CountdownScreen(
                        count = relativisezState.countdown!!,
                        onBack = {
                            relativisezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                relativisezState.isPlaying -> {
                    RelativisezScreen(
                        state = relativisezState,
                        onAnswer = { relativisezVm.answer(it) },
                        onQuit = {
                            relativisezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                relativisezState.isGameOver && !menuState.isRunning -> {
                    GameOverScreen(
                        title = "TIME'S UP!",
                        score = relativisezState.score,
                        isNewHighScore = relativisezState.isNewHighScore,
                        totalCorrect = relativisezState.totalCorrect,
                        totalWrong = relativisezState.totalWrong,
                        bestStreak = relativisezState.bestStreak,
                        highScore = relativisezState.highScore,
                        durationMs = relativisezState.durationMs,
                        answerHistory = relativisezState.answerHistory,
                        aiAnalysis = relativisezState.aiAnalysis,
                        isLoadingAnalysis = relativisezState.isLoadingAnalysis,
                        onPlayAgain = { relativisezVm.startGame() },
                        onBackToMenu = {
                            relativisezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        },
                        onChooseCategory = { relativisezVm.showSelection() },
                        onChooseCategoryLabel = "🔄 Auswahl"
                    )
                }
            }
        }

        Screen.NOMBREZ -> {
            when {
                nombrezState.isSelection -> {
                    NombrezSelectionScreen(
                        state = nombrezState,
                        onToggle = { nombrezVm.toggleTopic(it) },
                        onStart = { nombrezVm.startGame() },
                        onBack = {
                            nombrezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                nombrezState.countdown != null -> {
                    CountdownScreen(
                        count = nombrezState.countdown!!,
                        onBack = {
                            nombrezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                nombrezState.isPlaying -> {
                    NombrezScreen(
                        state = nombrezState,
                        onTypedTextChanged = { nombrezVm.onTypedTextChanged(it) },
                        onSubmitTyped = { nombrezVm.submitTyped() },
                        onChoice = { nombrezVm.answerChoice(it) },
                        onReplay = { nombrezVm.replayAudio() },
                        onQuit = {
                            nombrezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                nombrezState.isGameOver && !menuState.isRunning -> {
                    GameOverScreen(
                        title = "TIME'S UP!",
                        score = nombrezState.score,
                        isNewHighScore = nombrezState.isNewHighScore,
                        totalCorrect = nombrezState.totalCorrect,
                        totalWrong = nombrezState.totalWrong,
                        bestStreak = nombrezState.bestStreak,
                        highScore = nombrezState.highScore,
                        durationMs = nombrezState.durationMs,
                        answerHistory = nombrezState.answerHistory,
                        onPlayAgain = { nombrezVm.startGame() },
                        onBackToMenu = {
                            nombrezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        },
                        onChooseCategory = { nombrezVm.showSelection() },
                        onChooseCategoryLabel = "🔢 Themen"
                    )
                }
            }
        }

        Screen.ACCORDEZ -> {
            when {
                accordezState.isSelection -> {
                    AccordezSelectionScreen(
                        state = accordezState,
                        onToggle = { accordezVm.toggleGroup(it) },
                        onStart = { accordezVm.startGame() },
                        onBack = {
                            accordezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                accordezState.countdown != null -> {
                    CountdownScreen(
                        count = accordezState.countdown!!,
                        onBack = {
                            accordezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                accordezState.isPlaying -> {
                    AccordezScreen(
                        state = accordezState,
                        onChoice = { accordezVm.answerChoice(it) },
                        onQuit = {
                            accordezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                accordezState.isGameOver && !menuState.isRunning -> {
                    GameOverScreen(
                        title = "TIME'S UP!",
                        score = accordezState.score,
                        isNewHighScore = accordezState.isNewHighScore,
                        totalCorrect = accordezState.totalCorrect,
                        totalWrong = accordezState.totalWrong,
                        bestStreak = accordezState.bestStreak,
                        highScore = accordezState.highScore,
                        durationMs = accordezState.durationMs,
                        answerHistory = accordezState.answerHistory,
                        onPlayAgain = { accordezVm.startGame() },
                        onBackToMenu = {
                            accordezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        },
                        onChooseCategory = { accordezVm.showSelection() },
                        onChooseCategoryLabel = "🔄 Regeln"
                    )
                }
            }
        }

        Screen.PARLEZ -> {
            // Redirect to global settings when API key is missing
            if (parlezState.showSettings) {
                currentScreen = Screen.SETTINGS
                parlezVm.dismissSettings()
            }

            when {
                parlezState.isTopicSelection -> {
                    ParlezTopicSelectionScreen(
                        state = parlezState,
                        onSelectNiveau = { parlezVm.selectNiveau(it) },
                        onSelectTopic = { parlezVm.selectTopic(it) },
                        onStart = { parlezVm.startConversation() },
                        onSettings = { parlezVm.showSettings() },
                        onBack = {
                            parlezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                parlezState.isPlaying -> {
                    ParlezConversationScreen(
                        state = parlezState,
                        onToggleRecording = { parlezVm.toggleRecording() },
                        onToggleHints = { parlezVm.toggleHints() },
                        onRequestContextHints = { parlezVm.requestContextHints() },
                        onSpeakHint = { parlezVm.speakHint(it) },
                        onTranslate = { parlezVm.translateMessage(it) },
                        onFinish = { parlezVm.finishConversation() },
                        onQuit = {
                            parlezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                parlezState.isEvaluating -> {
                    ParlezEvaluatingScreen()
                }
                parlezState.isGameOver -> {
                    ParlezResultScreen(
                        state = parlezState,
                        onPlayAgain = { parlezVm.replaySameTopic() },
                        onSelectOtherSituation = { parlezVm.goToTopicSelection() },
                        onBackToMenu = {
                            parlezVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
            }
        }

        Screen.MENU -> {
            // Show info dialog when a game requires API keys that aren't set yet
            when {
                pronunciationState.needsApiKey -> NeedsApiKeyDialog(
                    gameName = "Prononcez!",
                    neededKeys = listOf("Azure Speech Services"),
                    onGoToSettings = {
                        pronunciationVm.dismissNeedsApiKey()
                        currentScreen = Screen.SETTINGS
                    },
                    onDismiss = { pronunciationVm.dismissNeedsApiKey() }
                )
                parlezState.needsApiKey -> NeedsApiKeyDialog(
                    gameName = "Parlez!",
                    neededKeys = listOf("Google Gemini", "Azure Speech Services"),
                    onGoToSettings = {
                        parlezVm.dismissNeedsApiKey()
                        currentScreen = Screen.SETTINGS
                    },
                    onDismiss = { parlezVm.dismissNeedsApiKey() }
                )
            }

            MenuScreen(
                matchingHighScore = matchingState.highScore,
                genderSnapHighScore = genderSnapState.highScore,
                gapFillHighScore = gapFillState.highScore,
                spellingBeeHighScore = spellingBeeState.highScore,
                conjugationHighScore = conjugationState.highScore,
                pronunciationHighScore = pronunciationState.highScore,
                prepositionHighScore = prepositionState.highScore,
                relativisezHighScore = relativisezState.highScore,
                nombrezHighScore = nombrezState.highScore,
                accordezHighScore = accordezState.highScore,
                parlezHighScore = parlezState.highScore,
                learningOverview = menuState.overview,
                onShowProgress = { weeklyVm.open(0) },
                onStartLearning = startLearning,
                onMenuDuJour = {
                    menuVm.showMenuSelection()
                    currentScreen = Screen.MENU_DU_JOUR
                },
                onStartMatching = { matchingVm.showModeSelection() },
                onStartGenderSnap = { genderSnapVm.startGame() },
                onStartGapFill = { gapFillVm.startGame() },
                onStartSpellingBee = { spellingBeeVm.startGame() },
                onStartConjugation = { conjugationVm.showTenseSelection() },
                onStartPronunciation = { pronunciationVm.onGameSelected() },
                onStartPreposition = { prepositionVm.showSelection() },
                onStartRelativisez = { relativisezVm.showSelection() },
                onStartNombrez = { nombrezVm.showSelection() },
                onStartAccordez = { accordezVm.showSelection() },
                onStartDites = {
                    ditesVm.start()
                    currentScreen = Screen.DITES
                },
                onStartParlez = { parlezVm.onGameSelected() },
                onShowSettings = { currentScreen = Screen.SETTINGS },
                onShowStatistics = {
                    statisticsVm.loadStatistics()
                    currentScreen = Screen.STATISTICS
                }
            )
        }
    }
}
