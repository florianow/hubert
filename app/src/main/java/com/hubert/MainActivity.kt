package com.hubert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
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
import com.hubert.viewmodel.SpellingBeeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HubertTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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
    HIGH_SCORES
}

@Composable
fun HubertApp() {
    val matchingVm: GameViewModel = hiltViewModel()
    val genderSnapVm: GenderSnapViewModel = hiltViewModel()
    val gapFillVm: GapFillViewModel = hiltViewModel()
    val spellingBeeVm: SpellingBeeViewModel = hiltViewModel()
    val conjugationVm: ConjugationViewModel = hiltViewModel()

    val matchingState by matchingVm.uiState.collectAsState()
    val genderSnapState by genderSnapVm.uiState.collectAsState()
    val gapFillState by gapFillVm.uiState.collectAsState()
    val spellingBeeState by spellingBeeVm.uiState.collectAsState()
    val conjugationState by conjugationVm.uiState.collectAsState()
    val topScores by matchingVm.topScores.collectAsState()

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

    LaunchedEffect(conjugationState.isPlaying, conjugationState.isGameOver, conjugationState.countdown) {
        if (conjugationState.countdown != null || conjugationState.isPlaying || conjugationState.isGameOver) {
            currentScreen = Screen.CONJUGATION
        }
    }

    when (currentScreen) {
        Screen.HIGH_SCORES -> {
            HighScoresScreen(
                scores = topScores,
                onBack = { currentScreen = Screen.MENU }
            )
        }

        Screen.MATCHING_GAME -> {
            when {
                matchingState.countdown != null -> {
                    CountdownScreen(count = matchingState.countdown!!)
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
                        score = matchingState.score,
                        isNewHighScore = matchingState.isNewHighScore,
                        stats = listOf(
                            Triple("Streak", "${matchingState.bestStreak}", "streak"),
                            Triple("Matches", "${matchingState.totalMatches}", "matches"),
                            Triple("Best", "${matchingState.highScore}", "best")
                        ),
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
                    CountdownScreen(count = genderSnapState.countdown!!)
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
                        score = genderSnapState.score,
                        isNewHighScore = genderSnapState.isNewHighScore,
                        stats = listOf(
                            Triple("Streak", "${genderSnapState.bestStreak}", "streak"),
                            Triple("Correct", "${genderSnapState.totalCorrect}", "correct"),
                            Triple("Wrong", "${genderSnapState.totalWrong}", "wrong")
                        ),
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
                    CountdownScreen(count = gapFillState.countdown!!)
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
                        score = gapFillState.score,
                        isNewHighScore = gapFillState.isNewHighScore,
                        stats = listOf(
                            Triple("Streak", "${gapFillState.bestStreak}", "streak"),
                            Triple("Correct", "${gapFillState.totalCorrect}", "correct"),
                            Triple("Wrong", "${gapFillState.totalWrong}", "wrong")
                        ),
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
                    CountdownScreen(count = spellingBeeState.countdown!!)
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
                        score = spellingBeeState.score,
                        isNewHighScore = spellingBeeState.isNewHighScore,
                        stats = listOf(
                            Triple("Streak", "${spellingBeeState.bestStreak}", "streak"),
                            Triple("Correct", "${spellingBeeState.totalCorrect}", "correct"),
                            Triple("Wrong", "${spellingBeeState.totalWrong}", "wrong")
                        ),
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
                conjugationState.countdown != null -> {
                    CountdownScreen(count = conjugationState.countdown!!)
                }
                conjugationState.isPlaying -> {
                    ConjugationScreen(
                        state = conjugationState,
                        onAnswer = { conjugationVm.answer(it) },
                        onQuit = {
                            conjugationVm.resetToMenu()
                            currentScreen = Screen.MENU
                        }
                    )
                }
                conjugationState.isGameOver -> {
                    GameOverScreen(
                        score = conjugationState.score,
                        isNewHighScore = conjugationState.isNewHighScore,
                        stats = listOf(
                            Triple("Streak", "${conjugationState.bestStreak}", "streak"),
                            Triple("Correct", "${conjugationState.totalCorrect}", "correct"),
                            Triple("Wrong", "${conjugationState.totalWrong}", "wrong")
                        ),
                        onPlayAgain = { conjugationVm.startGame() },
                        onBackToMenu = {
                            conjugationVm.resetToMenu()
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
                onStartMatching = { matchingVm.startGame() },
                onStartGenderSnap = { genderSnapVm.startGame() },
                onStartGapFill = { gapFillVm.startGame() },
                onStartSpellingBee = { spellingBeeVm.startGame() },
                onStartConjugation = { conjugationVm.startGame() },
                onShowHighScores = { currentScreen = Screen.HIGH_SCORES }
            )
        }
    }
}
