package com.hubert.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "GeminiApi"

/**
 * Minimal Google Gemini REST client (Google AI Studio, no SDK needed).
 * API key from https://aistudio.google.com/app/apikey
 */
object GeminiApi {

    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models"
    private const val CHAT_MODEL = "gemini-2.5-flash"  // fast, for live conversation
    private const val EVAL_MODEL = "gemini-2.5-flash"  // same model, higher token budget

    data class Message(val role: String, val text: String)  // role: "user" | "model"

    /**
     * Short conversational reply from Hubert.
     * [systemPrompt] is sent as Gemini's systemInstruction.
     * [history] is the alternating user/model conversation so far.
     * [userMessage] is the latest player message.
     */
    suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        history: List<Message>,
        userMessage: String
    ): String = withContext(Dispatchers.IO) {
        val contents = buildContents(history, userMessage)
        call(apiKey, CHAT_MODEL, systemPrompt, contents, maxTokens = 250, temperature = 0.8)
    }

    /**
     * Full evaluation call — returns raw response text (expected to be JSON).
     * Evaluation prompt goes as a single user message; no system instruction needed.
     */
    suspend fun evaluate(apiKey: String, evalPrompt: String): String =
        withContext(Dispatchers.IO) {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", evalPrompt) })
                    })
                })
            }
            call(apiKey, EVAL_MODEL, systemPrompt = null, contents, maxTokens = 3000, temperature = 0.2)
        }

    private fun buildContents(history: List<Message>, userMessage: String): JSONArray {
        return JSONArray().apply {
            history.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", msg.text) })
                    })
                })
            }
            // Add current user message
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", userMessage) })
                })
            })
        }
    }

    private fun call(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        contents: JSONArray,
        maxTokens: Int,
        temperature: Double
    ): String {
        val body = JSONObject().apply {
            put("contents", contents)
            if (systemPrompt != null) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
            }
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", maxTokens)
                put("temperature", temperature)
            })
        }

        val urlString = "$BASE_URL/$model:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 45_000
            conn.setRequestProperty("Content-Type", "application/json")

            val bodyBytes = body.toString().toByteArray(Charsets.UTF_8)
            DataOutputStream(conn.outputStream).use { out ->
                out.write(bodyBytes)
                out.flush()
            }

            val code = conn.responseCode
            if (code != 200) {
                val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" }
                          catch (_: Exception) { "" }
                Log.e(TAG, "Gemini error $code: $err")
                throw RuntimeException("Gemini API $code: $err")
            }

            val response = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "Gemini response: ${response.take(300)}")

            // Extract text from candidates[0].content.parts[0].text
            return JSONObject(response)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } finally {
            conn.disconnect()
        }
    }
}
