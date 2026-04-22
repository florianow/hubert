package com.hubert.utils

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
