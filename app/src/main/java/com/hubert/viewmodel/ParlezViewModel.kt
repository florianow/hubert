package com.hubert.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.hubert.data.model.ParlezTopic
import com.hubert.data.repository.StatisticsRepository
import com.hubert.utils.AudioRecorder
import com.hubert.utils.AzurePronunciationApi
import com.hubert.utils.FrenchTts
import com.hubert.utils.GeminiApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

// ── DataStore ──────────────────────────────────────────────────────────────────

private val Context.parlezDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "parlez_settings")

private val KEY_GEMINI_KEY    = stringPreferencesKey("gemini_api_key")
private val KEY_AZURE_KEY     = stringPreferencesKey("parlez_azure_key")
private val KEY_AZURE_REGION  = stringPreferencesKey("parlez_azure_region")
private val KEY_TOPIC_SCORES  = stringPreferencesKey("parlez_topic_scores")

// ── Data classes ───────────────────────────────────────────────────────────────

data class ParlezChatMessage(val isHubert: Boolean, val text: String)

data class ParlezCategoryScore(val score: Int, val commentaire: String)

data class ParlezScores(
    val vocabulaire: ParlezCategoryScore = ParlezCategoryScore(0, ""),
    val grammaire:   ParlezCategoryScore = ParlezCategoryScore(0, ""),
    val coherence:   ParlezCategoryScore = ParlezCategoryScore(0, ""),
    val fluidite:    ParlezCategoryScore = ParlezCategoryScore(0, ""),
    val effort:      ParlezCategoryScore = ParlezCategoryScore(0, "")
)

data class ParlezError(
    val original:   String,
    val correction: String,
    val explication: String
)

data class ParlezEvaluation(
    val scores:     ParlezScores,
    val total:      Int,
    val erreurs:    List<ParlezError>,
    val motsAppris: List<String>,
    val conseil:    String
)

data class ParlezState(
    // Navigation flags
    val isTopicSelection: Boolean = false,
    val isPlaying:        Boolean = false,
    val isEvaluating:     Boolean = false,
    val isGameOver:       Boolean = false,
    val showSettings:     Boolean = false,
    val needsApiKey:      Boolean = false,

    // Stored credentials
    val geminiApiKey: String = "",
    val azureKey:     String = "",
    val azureRegion:  String = "",

    // Topic selection
    val availableTopics:  List<ParlezTopic>  = emptyList(),
    val selectedTopic:    ParlezTopic?       = null,
    val selectedNiveau:   String             = "A1",
    val topicHighScores:  Map<String, Int>   = emptyMap(),  // topicId -> best score

    // Conversation
    val messages:         List<ParlezChatMessage> = emptyList(),
    val timeRemainingMs:  Long  = CONVERSATION_MS,
    val timerFraction:    Float = 1f,

    // Input state
    val isRecording:   Boolean = false,
    val isProcessing:  Boolean = false,  // STT or LLM in flight
    val timerExpired:  Boolean = false,  // timer hit 0, waiting for TTS to finish
    val errorMessage:  String? = null,

    // Results
    val evaluation:      ParlezEvaluation? = null,
    val score:           Int     = 0,
    val isNewHighScore:  Boolean = false,
    val highScore:       Int     = 0,
    val durationMs:      Long    = 0L
) {
    companion object {
        const val CONVERSATION_MS = 60_000L
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ParlezViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tts: FrenchTts,
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParlezState())
    val uiState: StateFlow<ParlezState> = _uiState.asStateFlow()

    private var audioRecorder: AudioRecorder? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var gameStartMs = 0L
    private val gson = Gson()

    init {
        viewModelScope.launch {
            // Load saved settings + topic scores
            context.parlezDataStore.data.first().let { prefs ->
                _uiState.update {
                    it.copy(
                        geminiApiKey    = prefs[KEY_GEMINI_KEY]   ?: "",
                        azureKey        = prefs[KEY_AZURE_KEY]    ?: "",
                        azureRegion     = prefs[KEY_AZURE_REGION] ?: "",
                        topicHighScores = parseTopicScores(prefs[KEY_TOPIC_SCORES] ?: "")
                    )
                }
            }
            // High score + topics
            val hs = statisticsRepository.getHighestScore("parlez")
            _uiState.update { it.copy(highScore = hs) }
            loadTopics()
        }
    }

    private fun loadTopics() {
        viewModelScope.launch {
            try {
                val json = context.assets.open("parlez_topics.json").bufferedReader().readText()
                val topics = gson.fromJson(json, Array<ParlezTopic>::class.java).toList()
                _uiState.update { it.copy(availableTopics = topics) }
            } catch (e: Exception) {
                android.util.Log.e("ParlezVM", "Failed to load topics", e)
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun onGameSelected() {
        val s = _uiState.value
        if (s.geminiApiKey.isBlank() || s.azureKey.isBlank() || s.azureRegion.isBlank()) {
            _uiState.update { it.copy(showSettings = true, needsApiKey = true) }
        } else {
            _uiState.update { it.copy(isTopicSelection = true) }
        }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true, needsApiKey = false) }
    }

    fun saveSettings(geminiKey: String, azureKey: String, azureRegion: String) {
        viewModelScope.launch {
            context.parlezDataStore.edit { prefs ->
                prefs[KEY_GEMINI_KEY]   = geminiKey
                prefs[KEY_AZURE_KEY]    = azureKey
                prefs[KEY_AZURE_REGION] = azureRegion
            }
            _uiState.update {
                it.copy(
                    geminiApiKey  = geminiKey,
                    azureKey      = azureKey,
                    azureRegion   = azureRegion,
                    showSettings  = false,
                    needsApiKey   = false,
                    isTopicSelection = true
                )
            }
        }
    }

    fun dismissSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun selectNiveau(niveau: String) {
        _uiState.update { it.copy(selectedNiveau = niveau) }
    }

    fun selectTopic(topic: ParlezTopic) {
        _uiState.update { it.copy(selectedTopic = topic) }
    }

    fun startConversation() {
        val topic = _uiState.value.selectedTopic ?: return
        gameStartMs = System.currentTimeMillis()

        val starter = ParlezChatMessage(isHubert = true, text = topic.starterFr)
        _uiState.update {
            it.copy(
                isTopicSelection = false,
                isPlaying        = true,
                messages         = listOf(starter),
                timeRemainingMs  = ParlezState.CONVERSATION_MS,
                timerFraction    = 1f,
                errorMessage     = null
            )
        }
        tts.speak(topic.starterFr)
        startTimer()
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) stopRecording() else startRecording()
    }

    fun resetToMenu() {
        timerJob?.cancel()
        recordingJob?.cancel()
        audioRecorder?.release()
        audioRecorder = null
        val s = _uiState.value
        _uiState.value = ParlezState(
            geminiApiKey    = s.geminiApiKey,
            azureKey        = s.azureKey,
            azureRegion     = s.azureRegion,
            availableTopics = s.availableTopics,
            highScore       = s.highScore,
            topicHighScores = s.topicHighScores
        )
    }

    // ── Timer ──────────────────────────────────────────────────────────────────

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(500L)
                val s = _uiState.value
                if (!s.isPlaying) break
                val newTime = (s.timeRemainingMs - 500L).coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        timeRemainingMs = newTime,
                        timerFraction   = newTime / ParlezState.CONVERSATION_MS.toFloat()
                    )
                }
                if (newTime == 0L) {
                    _uiState.update { it.copy(timerExpired = true) }
                    // Wait for STT / LLM to finish
                    while (_uiState.value.isProcessing) {
                        delay(200L)
                    }
                    // Give TTS time to finish speaking the last reply (~4 s)
                    delay(4_000L)
                    finishConversation()
                    break
                }
            }
        }
    }

    // ── Recording ──────────────────────────────────────────────────────────────

    private fun startRecording() {
        if (_uiState.value.isProcessing) return
        audioRecorder = AudioRecorder()
        _uiState.update { it.copy(isRecording = true, errorMessage = null) }
        recordingJob = viewModelScope.launch {
            try {
                audioRecorder?.startRecording()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(isRecording = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecording = false, errorMessage = "Mikrofon: ${e.message}") }
            }
        }
    }

    private fun stopRecording() {
        val wav = audioRecorder?.stop() ?: return
        audioRecorder = null
        recordingJob?.cancel()
        _uiState.update { it.copy(isRecording = false, isProcessing = true) }

        viewModelScope.launch {
            try {
                val s = _uiState.value

                // 1. Azure STT
                val transcript = AzurePronunciationApi.transcribe(s.azureRegion, s.azureKey, wav)
                if (transcript.isBlank()) {
                    _uiState.update { it.copy(isProcessing = false) }
                    return@launch
                }

                // 2. Add player message
                _uiState.update { it.copy(messages = it.messages + ParlezChatMessage(false, transcript)) }

                // 3. Gemini response (read fresh state so history is complete)
                val currentState = _uiState.value
                val reply = getHubertReply(currentState)

                if (reply.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            messages     = it.messages + ParlezChatMessage(true, reply),
                            isProcessing = false
                        )
                    }
                    tts.speak(reply)
                } else {
                    _uiState.update { it.copy(isProcessing = false) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(isRecording = false, isProcessing = false) }
                throw e  // must rethrow so coroutine machinery works correctly
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Fehler: ${e.message}") }
            }
        }
    }

    // ── Gemini conversation ────────────────────────────────────────────────────

    private suspend fun getHubertReply(state: ParlezState): String {
        val topic = state.selectedTopic ?: return ""

        // Convert message history to Gemini format (alternating user/model)
        // The last message in state.messages is the player's, which becomes the userMessage param.
        val allMessages = state.messages
        val history = allMessages.dropLast(1).takeLast(9).map { msg ->
            GeminiApi.Message(
                role = if (msg.isHubert) "model" else "user",
                text = msg.text
            )
        }
        val playerMessage = allMessages.last().text

        return GeminiApi.chat(
            apiKey        = state.geminiApiKey,
            systemPrompt  = buildSystemPrompt(topic, state.selectedNiveau),
            history       = history,
            userMessage   = playerMessage
        )
    }

    // ── Finish + evaluate ──────────────────────────────────────────────────────

    private fun finishConversation() {
        timerJob?.cancel()
        recordingJob?.cancel()
        audioRecorder?.release()
        audioRecorder = null

        val durationMs = System.currentTimeMillis() - gameStartMs
        _uiState.update {
            it.copy(
                isPlaying    = false,
                isEvaluating = true,
                isRecording  = false,
                isProcessing = false,
                durationMs   = durationMs
            )
        }
        viewModelScope.launch { evaluateConversation() }
    }

    private suspend fun evaluateConversation() {
        val state = _uiState.value
        val topic = state.selectedTopic

        if (topic == null) {
            _uiState.update { it.copy(isEvaluating = false, isGameOver = true) }
            return
        }

        val playerMessages = state.messages.filter { !it.isHubert }.map { it.text }
        if (playerMessages.isEmpty()) {
            _uiState.update {
                it.copy(isEvaluating = false, isGameOver = true, evaluation = emptyEvaluation(), score = 0)
            }
            return
        }

        try {
            val fullTranscript = state.messages.joinToString("\n") { msg ->
                if (msg.isHubert) "Hubert: ${msg.text}" else "Spieler: ${msg.text}"
            }
            val evalPrompt = buildEvaluationPrompt(topic, state.selectedNiveau, playerMessages, fullTranscript)
            android.util.Log.d("ParlezVM", "Sending evaluation for ${playerMessages.size} player messages")
            val rawJson = GeminiApi.evaluate(state.geminiApiKey, evalPrompt)
            android.util.Log.d("ParlezVM", "Evaluation raw: $rawJson")
            val evaluation = parseEvaluation(rawJson)

            statisticsRepository.saveSession(
                gameType     = "parlez",
                score        = evaluation.total,
                totalCorrect = playerMessages.size,
                totalWrong   = evaluation.erreurs.size,
                bestStreak   = 0,
                durationMs   = state.durationMs
            )

            // Update per-topic high score
            val topicId = topic.id
            val prevTopicBest = state.topicHighScores[topicId] ?: 0
            val newTopicScores = if (evaluation.total > prevTopicBest) {
                val updated = state.topicHighScores + (topicId to evaluation.total)
                saveTopicScores(updated)
                updated
            } else {
                state.topicHighScores
            }

            val newHigh = evaluation.total > state.highScore
            _uiState.update {
                it.copy(
                    isEvaluating    = false,
                    isGameOver      = true,
                    evaluation      = evaluation,
                    score           = evaluation.total,
                    isNewHighScore  = newHigh,
                    highScore       = if (newHigh) evaluation.total else state.highScore,
                    topicHighScores = newTopicScores
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ParlezVM", "Evaluation failed: ${e.message}", e)
            _uiState.update {
                it.copy(
                    isEvaluating = false,
                    isGameOver   = true,
                    evaluation   = emptyEvaluation(),
                    score        = 0,
                    errorMessage = "Bewertung fehlgeschlagen: ${e.message}"
                )
            }
        }
    }

    // ── JSON parsing ───────────────────────────────────────────────────────────

    private fun parseEvaluation(raw: String): ParlezEvaluation {
        android.util.Log.d("ParlezVM", "Raw evaluation response: $raw")
        // Extract the outermost JSON object — robust against markdown fences or leading text
        val start = raw.indexOf('{')
        val end   = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            android.util.Log.e("ParlezVM", "No JSON object found in response")
            return emptyEvaluation()
        }
        val json = raw.substring(start, end + 1)

        return try {
            val obj = JSONObject(json)
            val scoresObj = obj.getJSONObject("scores")

            fun cat(key: String): ParlezCategoryScore {
                val c = scoresObj.optJSONObject(key) ?: return ParlezCategoryScore(0, "")
                return ParlezCategoryScore(c.optInt("score", 0), c.optString("commentaire", ""))
            }

            val errs = obj.optJSONArray("erreurs") ?: JSONArray()
            val errors = (0 until errs.length()).map { i ->
                val e = errs.getJSONObject(i)
                ParlezError(
                    original    = e.optString("original", ""),
                    correction  = e.optString("correction", ""),
                    explication = e.optString("explication", "")
                )
            }

            val mots = obj.optJSONArray("mots_appris") ?: JSONArray()
            val motsAppris = (0 until mots.length()).map { i -> mots.getString(i) }

            ParlezEvaluation(
                scores = ParlezScores(
                    vocabulaire = cat("vocabulaire"),
                    grammaire   = cat("grammaire"),
                    coherence   = cat("coherence"),
                    fluidite    = cat("fluidite"),
                    effort      = cat("effort")
                ),
                total      = obj.optInt("total", 0),
                erreurs    = errors,
                motsAppris = motsAppris,
                conseil    = obj.optString("conseil", "")
            )
        } catch (e: Exception) {
            android.util.Log.e("ParlezVM", "JSON parse failed: $json", e)
            emptyEvaluation()
        }
    }

    private fun emptyEvaluation() = ParlezEvaluation(
        scores     = ParlezScores(),
        total      = 0,
        erreurs    = emptyList(),
        motsAppris = emptyList(),
        conseil    = "Versuche beim nächsten Mal mehr zu sprechen!"
    )

    // ── Prompts ────────────────────────────────────────────────────────────────

    private fun buildSystemPrompt(topic: ParlezTopic, niveau: String) = """
Tu es Hubert, un assistant de conversation français amical et patient.

RÔLE: Tu aides un germanophone à pratiquer le français oral dans une conversation de 2 minutes sur le thème: "${topic.themeFr}".

RÈGLES DE CONVERSATION:
1. Parle UNIQUEMENT en français, niveau $niveau
2. Pose des questions ouvertes pour faire parler le joueur
3. Reste STRICTEMENT dans le thème
   → Si le joueur sort du thème, ramène-le avec une question
4. Reformule les erreurs importantes correctement, sans explication
5. Réponses TRÈS COURTES (max 2 phrases, 10-15 mots)
6. Termine TOUJOURS par une question
7. Adapte ton vocabulaire au niveau
8. Si réponse courte ("oui", "non"), relance avec une question simple
9. Si mot allemand → donne le mot français et continue

PERSONNALITÉ:
- Enthousiaste mais pas exagéré
- Curieux — pose des questions de suivi
- Encourage les détails ("Ah intéressant ! Et pourquoi ?")
- Utilise des expressions idiomatiques simples
- Ne traduis JAMAIS en allemand sauf si le joueur est complètement bloqué

FORMAT DE RÉPONSE:
Réponds uniquement avec ton texte de conversation. Pas de métadonnées, pas d'annotations, pas de corrections explicites entre crochets.
    """.trimIndent()

    private fun buildEvaluationPrompt(
        topic: ParlezTopic,
        niveau: String,
        playerMessages: List<String>,
        fullTranscript: String
    ) = """
Tu es un professeur de français certifié. Évalue la performance d'un germanophone dans une conversation de 1 minutes en français.

TRANSCRIPTION DU JOUEUR:
${playerMessages.joinToString("\n")}

TRANSCRIPTION COMPLÈTE (avec les réponses de Hubert):
$fullTranscript

THÈME: ${topic.themeFr}
NIVEAU CIBLE: $niveau

Évalue selon 5 critères (chacun sur 20 pts):
1. VOCABULAIRE — variété, adéquation au thème
2. GRAMMAIRE — conjugaisons, accords, articles
3. COHÉRENCE — fil rouge, logique
4. FLUIDITÉ — longueur des réponses, connecteurs
5. EFFORT — structures complexes, prise de risque

IMPORTANT: Sois TRÈS CONCIS. Chaque commentaire: max 10 mots. Max 3 erreurs. Max 5 mots_appris.

Réponds UNIQUEMENT avec un objet JSON valide — sans markdown, sans backticks, sans texte avant ou après:
{
  "scores": {
    "vocabulaire": { "score": <0-20>, "commentaire": "<max 10 mots>" },
    "grammaire":   { "score": <0-20>, "commentaire": "<max 10 mots>" },
    "coherence":   { "score": <0-20>, "commentaire": "<max 10 mots>" },
    "fluidite":    { "score": <0-20>, "commentaire": "<max 10 mots>" },
    "effort":      { "score": <0-20>, "commentaire": "<max 10 mots>" }
  },
  "total": <somme 0-100>,
  "erreurs": [ { "original": "<fehler>", "correction": "<korrekt>", "explication": "<kurz auf Deutsch>" } ],
  "mots_appris": [ "<mot>" ],
  "conseil": "<1 Satz auf Deutsch>"
}
    """.trimIndent()

    // ── Topic score persistence ────────────────────────────────────────────────

    private fun parseTopicScores(json: String): Map<String, Int> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.optInt(it, 0) }
        } catch (_: Exception) { emptyMap() }
    }

    private suspend fun saveTopicScores(scores: Map<String, Int>) {
        val obj = JSONObject()
        scores.forEach { (id, score) -> obj.put(id, score) }
        context.parlezDataStore.edit { prefs -> prefs[KEY_TOPIC_SCORES] = obj.toString() }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        audioRecorder?.release()
    }
}
