package com.hubert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.hubert.ui.screens.*
import com.hubert.ui.theme.HubertTheme
import com.hubert.viewmodel.ConjugationViewModel
import com.hubert.viewmodel.GameViewModel
import com.hubert.viewmodel.GapFillViewModel
import com.hubert.viewmodel.GenderSnapViewModel
import com.hubert.viewmodel.PronunciationViewModel
import com.hubert.viewmodel.SpellingBeeViewModel
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
    MATCHING_GAME,
    GENDER_SNAP,
    GAP_FILL,
    SPELLING_BEE,
    CONJUGATION,
    PRONUNCIATION,
    STATISTICS
}

@Composable
fun HubertApp() {
    val matchingVm: GameViewModel = hiltViewModel()
    val genderSnapVm: GenderSnapViewModel = hiltViewModel()
    val gapFillVm: GapFillViewModel = hiltViewModel()
    val spellingBeeVm: SpellingBeeViewModel = hiltViewModel()
    val conjugationVm: ConjugationViewModel = hiltViewModel()
    val pronunciationVm: PronunciationViewModel = hiltViewModel()
    val statisticsVm: StatisticsViewModel = hiltViewModel()

    val matchingState by matchingVm.uiState.collectAsState()
    val genderSnapState by genderSnapVm.uiState.collectAsState()
    val gapFillState by gapFillVm.uiState.collectAsState()
    val spellingBeeState by spellingBeeVm.uiState.collectAsState()
    val conjugationState by conjugationVm.uiState.collectAsState()
    val pronunciationState by pronunciationVm.uiState.collectAsState()
    val statisticsState by statisticsVm.uiState.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.MENU) }

    // Navigate based on game states
    LaunchedEffect(matchingState.isPlaying, matchingState.isGameOver, matchingState.countdown) {
        if (matchingState.countdown != null || matchingState.isPlaying || matchingState.isGameOver) {
            currentScreen = Screen.MATCHING_GAME
        }
    }

    LaunchedEffect(genderSnapState.isPlaying, genderSnapState.isGameOver, genderSnapState.countdown) {
        if (genderSnapState.countdown != null || genderSnapState.isPlaying || genderSnapState.isGameOver) {
            currentScreen = Screen.GENDER_SNAP
        }
    }

    LaunchedEffect(gapFillState.isPlaying, gapFillState.isGameOver, gapFillState.countdown) {
        if (gapFillState.countdown != null || gapFillState.isPlaying || gapFillState.isGameOver) {
            currentScreen = Screen.GAP_FILL
        }
    }

    LaunchedEffect(spellingBeeState.isPlaying, spellingBeeState.isGameOver, spellingBeeState.countdown) {
        if (spellingBeeState.countdown != null || spellingBeeState.isPlaying || spellingBeeState.isGameOver) {
            currentScreen = Screen.SPELLING_BEE
        }
    }

    LaunchedEffect(conjugationState.isTenseSelection, conjugationState.isPlaying, conjugationState.isGameOver, conjugationState.countdown) {
        if (conjugationState.isTenseSelection || conjugationState.countdown != null || conjugationState.isPlaying || conjugationState.isGameOver) {
            currentScreen = Screen.CONJUGATION
        }
    }

    LaunchedEffect(pronunciationState.showSettings, pronunciationState.isPlaying, pronunciationState.isGameOver, pronunciationState.countdown) {
        if (pronunciationState.showSettings || pronunciationState.countdown != null || pronunciationState.isPlaying || pronunciationState.isGameOver) {
            currentScreen = Screen.PRONUNCIATION
        }
    }

    when (currentScreen) {
        Screen.STATISTICS -> {
            StatisticsScreen(
                state = statisticsState,
                onSelectGameType = { statisticsVm.selectGameType(it) },
                onBack = {
                    currentScreen = Screen.MENU
                }
            )
        }

        Screen.MATCHING_GAME -> {
            when {
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
                    GameScreen(
                        state = matchingState,
                        onSelectFrench = { matchingVm.selectFrench(it) },
                        onSelectGerman = { matchingVm.selectGerman(it) },
                        onQuit = {
                            matchingVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                matchingState.isGameOver -> {
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
                        onPlayAgain = { matchingVm.startGame() },
                        onBackToMenu = {
                            matchingVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
            }
        }

        Screen.GENDER_SNAP -> {
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
                    GenderSnapScreen(
                        state = genderSnapState,
                        onAnswer = { genderSnapVm.answer(it) },
                        onQuit = {
                            genderSnapVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                genderSnapState.isGameOver -> {
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

        Screen.GAP_FILL -> {
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
                    GapFillScreen(
                        state = gapFillState,
                        onAnswer = { gapFillVm.answer(it) },
                        onQuit = {
                            gapFillVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                gapFillState.isGameOver -> {
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

        Screen.SPELLING_BEE -> {
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
                    SpellingBeeScreen(
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
                spellingBeeState.isGameOver -> {
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

        Screen.CONJUGATION -> {
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
                    ConjugationScreen(
                        state = conjugationState,
                        onAnswer = { conjugationVm.answer(it) },
                        onNext = { conjugationVm.nextQuestion() },
                        onSpeak = { conjugationVm.speak(it) },
                        onQuit = {
                            conjugationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                conjugationState.isGameOver -> {
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
                        onPlayAgain = { conjugationVm.startGame() },
                        onBackToMenu = {
                            conjugationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
            }
        }

        Screen.PRONUNCIATION -> {
            // Settings dialog (shown as overlay when needed)
            if (pronunciationState.showSettings) {
                AzureSettingsDialog(
                    currentKey = pronunciationState.azureKey,
                    currentRegion = pronunciationState.azureRegion,
                    onSave = { key, region ->
                        pronunciationVm.saveSettings(key, region)
                        // If no game is active, go back to menu (settings opened from gear icon)
                        if (!pronunciationState.needsApiKey) {
                            currentScreen = Screen.MENU
                        }
                    },
                    onDismiss = {
                        pronunciationVm.dismissSettings()
                        if (!pronunciationState.isPlaying && !pronunciationState.isGameOver && pronunciationState.countdown == null) {
                            currentScreen = Screen.MENU
                        }
                    }
                )
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
                    PronunciationScreen(
                        state = pronunciationState,
                        onToggleRecording = { pronunciationVm.toggleRecording() },
                        onNext = { pronunciationVm.nextSentence() },
                        onRetry = { pronunciationVm.retryRecording() },
                        onSkipRetry = { pronunciationVm.skipRetry() },
                        onSpeak = { pronunciationVm.speak(it) },
                        onQuit = {
                            pronunciationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                pronunciationState.isGameOver -> {
                    PronunciationGameOverScreen(
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

        Screen.MENU -> {
            MenuScreen(
                matchingHighScore = matchingState.highScore,
                genderSnapHighScore = genderSnapState.highScore,
                gapFillHighScore = gapFillState.highScore,
                spellingBeeHighScore = spellingBeeState.highScore,
                conjugationHighScore = conjugationState.highScore,
                pronunciationHighScore = pronunciationState.highScore,
                onStartMatching = { matchingVm.startGame() },
                onStartGenderSnap = { genderSnapVm.startGame() },
                onStartGapFill = { gapFillVm.startGame() },
                onStartSpellingBee = { spellingBeeVm.startGame() },
                onStartConjugation = { conjugationVm.showTenseSelection() },
                onStartPronunciation = { pronunciationVm.onGameSelected() },
                onPronunciationSettings = { pronunciationVm.showSettings() },
                onShowStatistics = {
                    statisticsVm.loadStatistics()
                    currentScreen = Screen.STATISTICS
                }
            )
        }
    }
}
