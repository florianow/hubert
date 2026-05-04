package com.hubert

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.hubert.ui.screens.*
import com.hubert.ui.theme.HubertTheme
import com.hubert.viewmodel.ConjuguezViewModel
import com.hubert.viewmodel.GameType
import com.hubert.viewmodel.TrouvezViewModel
import com.hubert.viewmodel.CompletezViewModel
import com.hubert.viewmodel.ClassezViewModel
import com.hubert.viewmodel.PreposezViewModel
import com.hubert.viewmodel.PrononcezViewModel
import com.hubert.viewmodel.EcrivezViewModel
import com.hubert.viewmodel.ParlezViewModel
import com.hubert.viewmodel.SettingsViewModel
import com.hubert.viewmodel.StatisticsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
    PARLEZ,
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
    val parlezVm: ParlezViewModel = hiltViewModel()
    val statisticsVm: StatisticsViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()

    val matchingState by matchingVm.uiState.collectAsState()
    val genderSnapState by genderSnapVm.uiState.collectAsState()
    val gapFillState by gapFillVm.uiState.collectAsState()
    val spellingBeeState by spellingBeeVm.uiState.collectAsState()
    val conjugationState by conjugationVm.uiState.collectAsState()
    val pronunciationState by pronunciationVm.uiState.collectAsState()
    val prepositionState by prepositionVm.uiState.collectAsState()
    val parlezState by parlezVm.uiState.collectAsState()
    val statisticsState by statisticsVm.uiState.collectAsState()
    val settingsState by settingsVm.settings.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.MENU) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // "Hubert choisit!" — query least-played game, show toast, launch it
    val onHubertChoisit: () -> Unit = {
        coroutineScope.launch {
            val gameTypeKey = statisticsVm.getLeastPlayedGameType()
            val displayName = GameType.fromKey(gameTypeKey)?.displayName ?: gameTypeKey
            Toast.makeText(context, displayName, Toast.LENGTH_SHORT).show()
            when (gameTypeKey) {
                "matching" -> matchingVm.startGame()
                "gender_snap" -> genderSnapVm.startGame()
                "gap_fill" -> gapFillVm.startGame()
                "spelling_bee" -> spellingBeeVm.startGame()
                "conjugation" -> conjugationVm.showTenseSelection()
                "pronunciation" -> pronunciationVm.onGameSelected()
                "preposition" -> prepositionVm.showSelection()
                "parlez"      -> parlezVm.onGameSelected()
            }
        }
    }

    // Navigate based on game states
    LaunchedEffect(matchingState.isPlaying, matchingState.isGameOver, matchingState.countdown) {
        if (matchingState.countdown != null || matchingState.isPlaying || matchingState.isGameOver) {
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

    when (currentScreen) {
        Screen.SETTINGS -> {
            SettingsScreen(
                settings = settingsState,
                onSave = { gemini, azure, region ->
                    settingsVm.save(gemini, azure, region)
                    pronunciationVm.onSettingsSaved()
                    parlezVm.onSettingsSaved()
                    currentScreen = Screen.MENU
                },
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
                prepositionState.isGameOver -> {
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
                        onPlayAgain = { prepositionVm.startGame() },
                        onBackToMenu = {
                            prepositionVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
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
                parlezHighScore = parlezState.highScore,
                onHubertChoisit = onHubertChoisit,
                onStartMatching = { matchingVm.startGame() },
                onStartGenderSnap = { genderSnapVm.startGame() },
                onStartGapFill = { gapFillVm.startGame() },
                onStartSpellingBee = { spellingBeeVm.startGame() },
                onStartConjugation = { conjugationVm.showTenseSelection() },
                onStartPronunciation = { pronunciationVm.onGameSelected() },
                onStartPreposition = { prepositionVm.showSelection() },
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
