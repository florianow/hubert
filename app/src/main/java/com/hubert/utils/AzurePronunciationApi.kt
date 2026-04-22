package com.hubert.utils

import android.util.Base64
import android.util.Log
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.*
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentConfig
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGradingSystem
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGranularity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "AzurePronunciationApi"

/**
 * Azure Pronunciation Assessment via REST API.
 * Same lightweight approach as AnkiPA — just an HTTP POST with
 * Pronunciation-Assessment header, no SDK needed.
 *
 * See: https://learn.microsoft.com/en-us/azure/ai-services/speech-service/how-to-pronunciation-assessment
 */
object AzurePronunciationApi {

    /**
     * Result of a pronunciation assessment call.
     */
    data class PronunciationResult(
        val pronScore: Double,
        val accuracyScore: Double,
        val fluencyScore: Double,
        val completenessScore: Double,
        val words: List<WordResult>
    )

    data class WordResult(
        val word: String,
        val accuracyScore: Double,
        val errorType: String   // "None", "Mispronunciation", "Omission", "Insertion"
    )

    /**
     * Assess pronunciation of [audioWav] against [referenceText].
     *
     * @param region Azure region (e.g. "westeurope")
     * @param apiKey Azure Speech API key
     * @param referenceText The French sentence the user should have read
     * @param audioWav WAV audio bytes (16 kHz, 16-bit, mono)
     * @return [PronunciationResult] or throws on error
     */
    suspend fun assess(
        region: String,
        apiKey: String,
        referenceText: String,
        audioWav: ByteArray
    ): PronunciationResult = withContext(Dispatchers.IO) {

        // Build Pronunciation-Assessment header (Base64-encoded JSON)
        val pronParams = JSONObject().apply {
            put("ReferenceText", referenceText)
            put("GradingSystem", "HundredMark")
            put("Granularity", "Word")
            put("Dimension", "Comprehensive")
            put("EnableMiscue", true)
        }
        val pronHeaderValue = Base64.encodeToString(
            pronParams.toString().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        val urlString = "https://$region.stt.speech.microsoft.com/" +
            "speech/recognition/conversation/cognitiveservices/v1" +
            "?language=fr-FR"
        Log.d(TAG, "Calling Azure: $urlString")
        Log.d(TAG, "Audio size: ${audioWav.size} bytes, reference: \"$referenceText\"")

        val url = URL(urlString)

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            conn.setRequestProperty("Accept", "application/json;text/xml")
            conn.setRequestProperty("Content-Type",
                "audio/wav; codecs=audio/pcm; samplerate=16000")
            conn.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
            conn.setRequestProperty("Pronunciation-Assessment", pronHeaderValue)

            // Send audio data
            DataOutputStream(conn.outputStream).use { out ->
                out.write(audioWav)
                out.flush()
            }

            val responseCode = conn.responseCode
            Log.d(TAG, "Azure response code: $responseCode")
            if (responseCode != 200) {
                val errorBody = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: "No error body"
                } catch (_: Exception) { "Could not read error" }
                Log.e(TAG, "Azure error $responseCode: $errorBody")
                throw RuntimeException("Azure returned $responseCode: $errorBody")
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "Azure response: ${responseBody.take(500)}")
            parseResponse(responseBody)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Parse the Azure response JSON into our result model.
     *
     * The response has an NBest array; we take the first (best) entry.
     * The pronunciation scores are directly on the NBest entry (not nested):
     * {
     *   "NBest": [{
     *     "AccuracyScore": 95.0,
     *     "FluencyScore": 90.0,
     *     "CompletenessScore": 100.0,
     *     "PronScore": 93.0,
     *     "Words": [{
     *       "Word": "bonjour",
     *       "AccuracyScore": 98.0,
     *       "ErrorType": "None"
     *     }]
     *   }]
     * }
     */
    /**
     * Free-form transcription WITH per-word pronunciation scoring.
     * No reference text needed — Azure assesses what it recognized.
     * Returns the recognized text + an overall pronScore (0-100), or blank text on failure.
     */
    data class FreeAssessResult(val text: String, val pronScore: Double, val words: List<WordResult>)

    suspend fun transcribeAndAssess(
        region: String,
        apiKey: String,
        audioWav: ByteArray
    ): FreeAssessResult = withContext(Dispatchers.IO) {
        // Use Azure Speech SDK with continuous recognition to capture ALL utterances,
        // not just the first phrase (which was the REST API limitation).
        try {
            val speechConfig = SpeechConfig.fromSubscription(apiKey, region)
            speechConfig.speechRecognitionLanguage = "fr-FR"

            // Set up pronunciation assessment (no reference text = assess what is recognized)
            val pronConfig = PronunciationAssessmentConfig(
                "", // no reference text
                PronunciationAssessmentGradingSystem.HundredMark,
                PronunciationAssessmentGranularity.Word,
                false // no miscue without reference text
            )

            // Feed the recorded WAV into the SDK via PushAudioInputStream
            val audioFormat = AudioStreamFormat.getWaveFormatPCM(16000, 16.toShort(), 1.toShort())
            val pushStream = AudioInputStream.createPushStream(audioFormat)

            // Create audio config BEFORE writing/closing the stream
            val audioConfig = AudioConfig.fromStreamInput(pushStream)

            // Now write PCM data (skip 44-byte WAV header) and signal end
            if (audioWav.size > 44) {
                pushStream.write(audioWav.copyOfRange(44, audioWav.size))
            }
            pushStream.close() // Signal end of audio

            val recognizer = SpeechRecognizer(speechConfig, audioConfig)
            pronConfig.applyTo(recognizer)

            // Collect results from all recognized segments
            val allTexts = mutableListOf<String>()
            val allWords = mutableListOf<WordResult>()
            val allPronScores = mutableListOf<Double>()
            val done = CountDownLatch(1)
            var errorMsg: String? = null

            recognizer.recognized.addEventListener { _, e ->
                if (e.result.reason == ResultReason.RecognizedSpeech) {
                    val text = e.result.text
                    if (text.isNotBlank()) {
                        allTexts.add(text)
                    }

                    // Extract pronunciation assessment from result JSON
                    try {
                        val pronJson = e.result.properties.getProperty(
                            PropertyId.SpeechServiceResponse_JsonResult
                        )
                        val root = JSONObject(pronJson)
                        val nBest = root.optJSONArray("NBest")
                        if (nBest != null && nBest.length() > 0) {
                            val best = nBest.getJSONObject(0)
                            val scoreSource = best.optJSONObject("PronunciationAssessment") ?: best
                            val score = scoreSource.optDouble("PronScore", -1.0)
                            if (score >= 0) allPronScores.add(score)

                            val wordsArray = best.optJSONArray("Words")
                            if (wordsArray != null) {
                                for (i in 0 until wordsArray.length()) {
                                    val w = wordsArray.getJSONObject(i)
                                    val wSource = w.optJSONObject("PronunciationAssessment") ?: w
                                    allWords.add(WordResult(
                                        word = w.optString("Word", ""),
                                        accuracyScore = wSource.optDouble("AccuracyScore", 0.0),
                                        errorType = wSource.optString("ErrorType", "None")
                                    ))
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Log.w(TAG, "Could not parse pronunciation JSON: ${ex.message}")
                    }
                }
            }

            recognizer.canceled.addEventListener { _, e ->
                if (e.reason == CancellationReason.Error) {
                    errorMsg = "Azure SDK error: ${e.errorCode} - ${e.errorDetails}"
                    Log.e(TAG, errorMsg!!)
                }
                done.countDown()
            }

            recognizer.sessionStopped.addEventListener { _, _ ->
                done.countDown()
            }

            // Start continuous recognition and wait for completion
            recognizer.startContinuousRecognitionAsync().get()
            done.await(30, TimeUnit.SECONDS)

            try { recognizer.stopContinuousRecognitionAsync().get() } catch (_: Exception) {}
            try { recognizer.close() } catch (_: Exception) {}
            try { audioConfig.close() } catch (_: Exception) {}
            try { speechConfig.close() } catch (_: Exception) {}

            if (errorMsg != null) {
                Log.e(TAG, "Continuous recognition failed: $errorMsg")
                return@withContext FreeAssessResult("", 0.0, emptyList())
            }

            val fullText = allTexts.joinToString(" ")
            val avgScore = if (allPronScores.isNotEmpty()) allPronScores.average() else 0.0

            Log.d(TAG, "Continuous recognition result: ${allTexts.size} segments, " +
                "${allWords.size} words, text='$fullText'")

            FreeAssessResult(fullText, avgScore, allWords)
        } catch (e: Exception) {
            Log.e(TAG, "Speech SDK error: ${e.message}", e)
            FreeAssessResult("", 0.0, emptyList())
        }
    }

    /**
     * Plain speech-to-text transcription (no pronunciation scoring).
     * Returns the recognized French text, or blank on failure.
     */
    suspend fun transcribe(
        region: String,
        apiKey: String,
        audioWav: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val urlString = "https://$region.stt.speech.microsoft.com/" +
            "speech/recognition/conversation/cognitiveservices/v1" +
            "?language=fr-FR"

        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty(
                "Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000"
            )
            conn.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)

            DataOutputStream(conn.outputStream).use { out ->
                out.write(audioWav)
                out.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errBody = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                } catch (_: Exception) { "" }
                Log.e(TAG, "Azure STT error $responseCode: $errBody")
                return@withContext ""
            }

            val body = conn.inputStream.bufferedReader().readText()
            val json = org.json.JSONObject(body)
            if (json.optString("RecognitionStatus") != "Success") return@withContext ""
            json.optString("DisplayText", "")
        } finally {
            conn.disconnect()
        }
    }

    private fun parseResponse(json: String): PronunciationResult {
        val root = JSONObject(json)
        val nBest = root.optJSONArray("NBest")
            ?: throw RuntimeException("No NBest in Azure response: $json")

        if (nBest.length() == 0) {
            throw RuntimeException("Empty NBest array in Azure response")
        }

        val best = nBest.getJSONObject(0)

        // Scores may be flat on the NBest entry or nested in PronunciationAssessment
        val pronAssessment = best.optJSONObject("PronunciationAssessment")
        val scoreSource = pronAssessment ?: best

        val words = mutableListOf<WordResult>()
        val wordsArray = best.optJSONArray("Words")
        if (wordsArray != null) {
            for (i in 0 until wordsArray.length()) {
                val w = wordsArray.getJSONObject(i)
                // Word scores may also be flat or nested
                val wAssessment = w.optJSONObject("PronunciationAssessment")
                val wSource = wAssessment ?: w
                words.add(
                    WordResult(
                        word = w.getString("Word"),
                        accuracyScore = wSource.optDouble("AccuracyScore", 0.0),
                        errorType = wSource.optString("ErrorType", "None")
                    )
                )
            }
        }

        return PronunciationResult(
            pronScore = scoreSource.optDouble("PronScore", 0.0),
            accuracyScore = scoreSource.optDouble("AccuracyScore", 0.0),
            fluencyScore = scoreSource.optDouble("FluencyScore", 0.0),
            completenessScore = scoreSource.optDouble("CompletenessScore", 0.0),
            words = words
        )
    }
}
