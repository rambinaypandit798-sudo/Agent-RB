package com.rbagent.assistant.ai

import android.util.Log
import com.rbagent.assistant.data.ChatStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GeminiApiClient {

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val API_ROOT = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000

        /**
         * Ordered fallback list — tried top-down. Retries only on HTTP 404
         * ("model not found"). Any other failure (auth, quota, network) is
         * surfaced immediately so the user can act on it.
         */
        private val MODEL_FALLBACKS = listOf(
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash-002",
            "gemini-1.5-flash-001",
            "gemini-pro"
        )
    }

    sealed class Result {
        data class Success(val text: String) : Result()
        data class Failure(
            val userMessage: String,
            val technical: String? = null,
            val httpCode: Int? = null
        ) : Result()
    }

    data class RequestParams(
        val apiKey: String,
        val personalitySystemPrompt: String,
        val userName: String,
        val memoryFacts: List<String>,
        val conversationHistory: List<ChatStorageManager.ChatMessage>,
        val newUserMessage: String,
        val temperature: Float = 0.9f,
        val maxOutputTokens: Int = 2048
    )

    // ----------------------------------------------------------------
    // PUBLIC ENTRY POINT — walks the fallback chain
    // ----------------------------------------------------------------
    suspend fun generateContent(params: RequestParams): Result = withContext(Dispatchers.IO) {
        if (params.apiKey.isBlank()) {
            return@withContext Result.Failure(
                userMessage = "Gemini API key is missing. Open Settings → 🔑 Gemini API Key and save your key.",
                technical = "blank_api_key"
            )
        }
        if (params.newUserMessage.isBlank()) {
            return@withContext Result.Failure(
                userMessage = "Empty message — nothing to send.",
                technical = "blank_prompt"
            )
        }

        val bodyJson = buildRequestBody(params).toString()
        var lastFailure: Result.Failure? = null

        for (model in MODEL_FALLBACKS) {
            Log.d(TAG, "Trying model: $model")
            val result = callModel(model, params.apiKey, bodyJson)

            when (result) {
                is Result.Success -> {
                    Log.i(TAG, "Success with model: $model")
                    return@withContext result
                }
                is Result.Failure -> {
                    lastFailure = result
                    if (result.httpCode == 404) {
                        Log.w(TAG, "Model $model not found — trying next fallback")
                        continue
                    } else {
                        // Non-404 error: don't waste calls, surface it now
                        return@withContext result
                    }
                }
            }
        }

        lastFailure ?: Result.Failure(
            userMessage = "No Gemini model is available for your API key. Please verify your key has Gemini API access enabled.",
            technical = "all_models_failed"
        )
    }

    // ----------------------------------------------------------------
    // SINGLE MODEL CALL
    // ----------------------------------------------------------------
    private fun callModel(model: String, apiKey: String, bodyJson: String): Result {
        val url = API_ROOT + model + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(bodyJson) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText: String = if (stream != null) {
                BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
            } else ""

            if (code !in 200..299) {
                mapHttpError(model, code, responseText)
            } else {
                parseSuccessResponse(responseText)
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout on $model", e)
            Result.Failure(
                userMessage = "Request timed out. Check your internet connection and try again.",
                technical = e.message
            )
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "No network on $model", e)
            Result.Failure(
                userMessage = "No internet connection. Please check your network.",
                technical = e.message
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error on $model", e)
            Result.Failure(
                userMessage = "Unexpected error: " + (e.message ?: "unknown"),
                technical = e.stackTraceToString()
            )
        } finally {
            conn?.disconnect()
        }
    }

    // ----------------------------------------------------------------
    // REQUEST BODY
    // ----------------------------------------------------------------
    private fun buildRequestBody(p: RequestParams): JSONObject {
        val root = JSONObject()

        val systemParts = JSONArray()
        systemParts.put(JSONObject().put("text", buildSystemPrompt(p)))
        root.put("systemInstruction", JSONObject().put("parts", systemParts))

        val contents = JSONArray()
        val history = p.conversationHistory
            .filter { it.role != ChatStorageManager.Role.SYSTEM }
            .takeLast(30)

        for (msg in history) {
            val role = if (msg.role == ChatStorageManager.Role.USER) "user" else "model"
            val parts = JSONArray()
            parts.put(JSONObject().put("text", msg.content))
            val entry = JSONObject()
            entry.put("role", role)
            entry.put("parts", parts)
            contents.put(entry)
        }

        val userParts = JSONArray()
        userParts.put(JSONObject().put("text", p.newUserMessage))
        val userEntry = JSONObject()
        userEntry.put("role", "user")
        userEntry.put("parts", userParts)
        contents.put(userEntry)
        root.put("contents", contents)

        val genConfig = JSONObject()
        genConfig.put("temperature", p.temperature.toDouble())
        genConfig.put("maxOutputTokens", p.maxOutputTokens)
        genConfig.put("topP", 0.95)
        genConfig.put("topK", 40)
        root.put("generationConfig", genConfig)

        val safety = JSONArray()
        val categories = listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        )
        for (cat in categories) {
            val entry = JSONObject()
            entry.put("category", cat)
            entry.put("threshold", "BLOCK_ONLY_HIGH")
            safety.put(entry)
        }
        root.put("safetySettings", safety)

        return root
    }

    private fun buildSystemPrompt(p: RequestParams): String {
        val sb = StringBuilder()
        sb.append(p.personalitySystemPrompt.trim()).append("\n\n")
        sb.append("=== USER CONTEXT ===\n")
        val name = p.userName.ifBlank { "Sir" }
        sb.append("You are speaking with ").append(name)
        sb.append(". Address them as \"").append(name).append("\" naturally.\n")

        if (p.memoryFacts.isNotEmpty()) {
            sb.append("\n=== LONG-TERM MEMORY (things you already know about ")
                .append(name).append(") ===\n")
            for (fact in p.memoryFacts) {
                sb.append("• ").append(fact.trim()).append("\n")
            }
            sb.append("\nUse this memory naturally — never announce that you are " +
                    "\"recalling memory\". Reference it like a close friend would.\n")
        }

        sb.append("\n=== RESPONSE RULES ===\n")
        sb.append("• Reply in the user's language. Hinglish → Hinglish. Hindi → Hindi. English → English.\n")
        sb.append("• Be concise but warm. Never output raw JSON or code fences unless requested.\n")
        sb.append("• For device actions (calls, SMS, alarms, opening apps) confirm briefly; the app triggers the intent.\n")
        sb.append("• Never mention Gemini, Google, or that you are an LLM. You are \"RB Agent\".\n")

        return sb.toString()
    }

    // ----------------------------------------------------------------
    // RESPONSE PARSING
    // ----------------------------------------------------------------
    private fun parseSuccessResponse(raw: String): Result {
        if (raw.isBlank()) {
            return Result.Failure("Empty response from Gemini.", "blank_response")
        }
        return try {
            val root = JSONObject(raw)

            val feedback = root.optJSONObject("promptFeedback")
            val blockReason = feedback?.optString("blockReason", null)
            if (blockReason != null && !root.has("candidates")) {
                return Result.Failure(
                    "The message was blocked by safety filters. Try rephrasing.",
                    "blocked: " + blockReason
                )
            }

            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.Failure("No response generated. Please try again.", "no_candidates")
            }

            val first = candidates.getJSONObject(0)
            val finishReason = first.optString("finishReason", "")
            val content = first.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            val builder = StringBuilder()
            if (parts != null) {
                var i = 0
                while (i < parts.length()) {
                    val part = parts.optJSONObject(i)
                    if (part != null) {
                        val t = part.optString("text", "")
                        if (t.isNotEmpty()) builder.append(t)
                    }
                    i++
                }
            }
            val text = builder.toString().trim()

            if (text.isEmpty()) {
                val msg: String = when (finishReason) {
                    "SAFETY" -> "Response was filtered for safety. Try a different phrasing."
                    "MAX_TOKENS" -> "Response was cut off. Try asking a shorter question."
                    "RECITATION" -> "Response blocked due to recitation policy."
                    else -> "Gemini returned an empty response."
                }
                return Result.Failure(msg, "finish=" + finishReason)
            }

            Result.Success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Parse failure: " + raw, e)
            Result.Failure("Failed to parse Gemini response.", e.message)
        }
    }

    private fun mapHttpError(model: String, code: Int, body: String): Result {
        val apiMsg: String? = try {
            val err = JSONObject(body).optJSONObject("error")
            val m = err?.optString("message", "")
            if (m.isNullOrBlank()) null else m
        } catch (_: Exception) { null }

        val userMsg: String = when (code) {
            400 -> apiMsg ?: "Bad request. Your API key or prompt may be invalid."
            401, 403 -> "API key rejected. Open Settings → 🔑 Gemini API Key and re-save a valid key."
            404 -> apiMsg ?: ("Model \"" + model + "\" not available for this key.")
            429 -> "Rate limit or quota exceeded. Wait a moment and retry."
            in 500..599 -> "Gemini server error (" + code + "). Try again shortly."
            else -> apiMsg ?: ("Request failed (HTTP " + code + ").")
        }
        Log.e(TAG, "HTTP " + code + " on " + model + ": " + body)
        return Result.Failure(userMessage = userMsg, technical = body, httpCode = code)
    }
}
