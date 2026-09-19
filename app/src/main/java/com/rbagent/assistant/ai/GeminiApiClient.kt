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
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
    }

    sealed class Result {
        data class Success(val text: String) : Result()
        data class Failure(val userMessage: String, val technical: String? = null, val httpCode: Int? = null) : Result()
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

    suspend fun generateContent(params: RequestParams): Result = withContext(Dispatchers.IO) {
        if (params.apiKey.isBlank()) return@withContext Result.Failure(
            "Gemini API key is missing. Open Settings → 🔑 Gemini API Key and save your key.",
            "blank_api_key")
        if (params.newUserMessage.isBlank()) return@withContext Result.Failure(
            "Empty message — nothing to send.", "blank_prompt")

        val url = "$BASE_URL?key=${URLEncoder.encode(params.apiKey, "UTF-8")}"
        val bodyJson = buildRequestBody(params).toString()
        var conn: HttpURLConnection? = null
        try {
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
            val isSuccess = code in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream
            val responseText = stream?.let { s -> BufferedReader(InputStreamReader(s, "UTF-8")).use { it.readText() } } ?: ""
            if (!isSuccess) return@withContext mapHttpError(code, responseText)
            parseSuccessResponse(responseText)
        } catch (e: java.net.SocketTimeoutException) {
            Result.Failure("Request timed out. Check your internet connection and try again.", e.message)
        } catch (e: java.net.UnknownHostException) {
            Result.Failure("No internet connection. Please check your network.", e.message)
        } catch (e: Exception) {
            Result.Failure("Unexpected error: ${e.message ?: "unknown"}", e.stackTraceToString())
        } finally { conn?.disconnect() }
    }

    private fun buildRequestBody(p: RequestParams): JSONObject {
        val root = JSONObject()
        root.put("systemInstruction", JSONObject().put("parts",
            JSONArray().put(JSONObject().put("text", buildSystemPrompt(p)))))
        val contents = JSONArray()
        p.conversationHistory.filter { it.role != ChatStorageManager.Role.SYSTEM }
            .takeLast(30).forEach { msg ->
                contents.put(JSONObject().apply {
                    put("role", if (msg.role == ChatStorageManager.Role.USER) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                })
            }
        contents.put(JSONObject().apply {
            put("role", "user"); put("parts", JSONArray().put(JSONObject().put("text", p.newUserMessage)))
        })
        root.put("contents", contents)
        root.put("generationConfig", JSONObject().apply {
            put("temperature", p.temperature.toDouble())
            put("maxOutputTokens", p.maxOutputTokens)
            put("topP", 0.95); put("topK", 40)
        })
        root.put("safetySettings", JSONArray().apply {
            listOf("HARM_CATEGORY_HARASSMENT","HARM_CATEGORY_HATE_SPEECH",
                   "HARM_CATEGORY_SEXUALLY_EXPLICIT","HARM_CATEGORY_DANGEROUS_CONTENT").forEach { cat ->
                put(JSONObject().apply { put("category", cat); put("threshold", "BLOCK_ONLY_HIGH") })
            }
        })
        return root
    }

    private fun buildSystemPrompt(p: RequestParams): String {
        val sb = StringBuilder()
        sb.append(p.personalitySystemPrompt.trim()).append("\n\n")
        sb.append("=== USER CONTEXT ===\n")
        val name = p.userName.ifBlank { "Sir" }
        sb.append("You are speaking with ").append(name).append(". Address them as \"").append(name).append("\" naturally.\n")
        if (p.memoryFacts.isNotEmpty()) {
            sb.append("\n=== LONG-TERM MEMORY (things you already know about ").append(name).append(") ===\n")
            p.memoryFacts.forEach { sb.append("• ").append(it.trim()).append("\n") }
            sb.append("\nUse this memory naturally — never announce that you are \"recalling memory\".\n")
        }
        sb.append("\n=== RESPONSE RULES ===\n")
        sb.append("• Reply in the user's language. Hinglish → Hinglish. Hindi → Hindi. English → English.\n")
        sb.append("• Be concise but warm. Never output raw JSON unless requested.\n")
        sb.append("• For device actions (calls, SMS, alarms) confirm briefly; the app triggers the intent.\n")
        sb.append("• Never mention Gemini, Google, or that you are an LLM. You are \"RB Agent\".\n")
        return sb.toString()
    }

    private fun parseSuccessResponse(raw: String): Result {
        if (raw.isBlank()) return Result.Failure("Empty response from Gemini.", "blank_response")
        return try {
            val root = JSONObject(raw)
            val feedback = root.optJSONObject("promptFeedback")
            val blockReason = feedback?.optString("blockReason", null)
            if (blockReason != null && !root.has("candidates"))
                return Result.Failure("The message was blocked by safety filters. Try rephrasing.", "blocked: $blockReason")
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0)
                return Result.Failure("No response generated. Please try again.", "no_candidates")
            val first = candidates.getJSONObject(0)
            val finishReason = first.optString("finishReason", "")
            val content = first.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = buildString {
                if (parts != null) for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: return@for
                    val t = part.optString("text", "")
                    if (t.isNotEmpty()) append(t)
                }
            }.trim()
            if (text.isEmpty()) {
                val msg = when (finishReason) {
                    "SAFETY" -> "Response was filtered for safety. Try a different phrasing."
                    "MAX_TOKENS" -> "Response was cut off. Try asking a shorter question."
                    "RECITATION" -> "Response blocked due to recitation policy."
                    else -> "Gemini returned an empty response."
                }
                return Result.Failure(msg, "finish=$finishReason")
            }
            Result.Success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Parse failure: $raw", e)
            Result.Failure("Failed to parse Gemini response.", e.message)
        }
    }

    private fun mapHttpError(code: Int, body: String): Result {
        val apiMsg = try {
            JSONObject(body).optJSONObject("error")?.optString("message", "")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        val userMsg = when (code) {
            400 -> apiMsg ?: "Bad request. Your API key or prompt may be invalid."
            401, 403 -> "API key rejected. Open Settings → 🔑 Gemini API Key and re-save a valid key."
            404 -> "Model not found. Confirm gemini-1.5-flash is enabled for your key."
            429 -> "Rate limit or quota exceeded. Wait a moment and retry."
            in 500..599 -> "Gemini server error ($code). Try again shortly."
            else -> apiMsg ?: "Request failed (HTTP $code)."
        }
        Log.e(TAG, "HTTP $code: $body")
        return Result.Failure(userMsg, body, code)
    }
}
