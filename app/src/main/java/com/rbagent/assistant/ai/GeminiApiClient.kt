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
         * Fast, current models — ordered for reliability & speed.
         * gemini-pro was retired; it is intentionally excluded.
         */
        private val MODEL_FALLBACKS = listOf(
            "gemini-3.6-flash-latest",
            "gemini-3.6-flash",
            "gemini-3.6-flash",
            "gemini-3.6-flash"
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
        val attachmentBase64: String? = null,
        val attachmentMime: String? = null,
        val temperature: Float = 0.9f,
        val maxOutputTokens: Int = 2048
    )

    suspend fun generateContent(params: RequestParams): Result = withContext(Dispatchers.IO) {
        if (params.apiKey.isBlank()) return@withContext Result.Failure(
            "Gemini API key is missing. Open Settings → 🔑 Gemini API Key and save your key.",
            "blank_api_key")
        if (params.newUserMessage.isBlank() && params.attachmentBase64.isNullOrBlank())
            return@withContext Result.Failure("Nothing to send.", "blank_prompt")

        val bodyJson = buildRequestBody(params).toString()
        var lastFailure: Result.Failure? = null

        for (model in MODEL_FALLBACKS) {
            Log.d(TAG, "Trying model: $model")
            when (val r = callModel(model, params.apiKey, bodyJson)) {
                is Result.Success -> { Log.i(TAG, "Success with: $model"); return@withContext r }
                is Result.Failure -> {
                    lastFailure = r
                    if (r.httpCode == 404) { Log.w(TAG, "$model 404 — next"); continue }
                    return@withContext r
                }
            }
        }
        lastFailure ?: Result.Failure(
            "No Gemini model available for your key. Verify your key has Gemini API access enabled.",
            "all_models_failed")
    }

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
            val text: String = if (stream != null)
                BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() } else ""
            if (code !in 200..299) mapHttpError(model, code, text) else parseSuccessResponse(text)
        } catch (e: java.net.SocketTimeoutException) {
            Result.Failure("Request timed out. Check your connection and retry.", e.message)
        } catch (e: java.net.UnknownHostException) {
            Result.Failure("No internet connection.", e.message)
        } catch (e: Exception) {
            Result.Failure("Unexpected error: " + (e.message ?: "unknown"), e.stackTraceToString())
        } finally { conn?.disconnect() }
    }

    private fun buildRequestBody(p: RequestParams): JSONObject {
        val root = JSONObject()
        val sysParts = JSONArray().put(JSONObject().put("text", buildSystemPrompt(p)))
        root.put("systemInstruction", JSONObject().put("parts", sysParts))

        val contents = JSONArray()
        val history = p.conversationHistory
            .filter { it.role != ChatStorageManager.Role.SYSTEM }
            .takeLast(30)
        for (msg in history) {
            val role = if (msg.role == ChatStorageManager.Role.USER) "user" else "model"
            val parts = JSONArray().put(JSONObject().put("text", msg.content.ifBlank {
                msg.attachmentName?.let { "[Attached: $it]" } ?: ""
            }))
            contents.put(JSONObject().put("role", role).put("parts", parts))
        }

        val userParts = JSONArray()
        if (p.newUserMessage.isNotBlank())
            userParts.put(JSONObject().put("text", p.newUserMessage))
        if (!p.attachmentBase64.isNullOrBlank() && !p.attachmentMime.isNullOrBlank()) {
            val inline = JSONObject()
                .put("mimeType", p.attachmentMime)
                .put("data", p.attachmentBase64)
            userParts.put(JSONObject().put("inlineData", inline))
        }
        if (userParts.length() == 0) userParts.put(JSONObject().put("text", "(empty)"))
        contents.put(JSONObject().put("role", "user").put("parts", userParts))
        root.put("contents", contents)

        root.put("generationConfig", JSONObject().apply {
            put("temperature", p.temperature.toDouble())
            put("maxOutputTokens", p.maxOutputTokens)
            put("topP", 0.95); put("topK", 40)
        })

        val safety = JSONArray()
        listOf("HARM_CATEGORY_HARASSMENT","HARM_CATEGORY_HATE_SPEECH",
               "HARM_CATEGORY_SEXUALLY_EXPLICIT","HARM_CATEGORY_DANGEROUS_CONTENT").forEach { cat ->
            safety.put(JSONObject().put("category", cat).put("threshold", "BLOCK_ONLY_HIGH"))
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
            .append(". Address them as \"").append(name).append("\" naturally.\n")
        if (p.memoryFacts.isNotEmpty()) {
            sb.append("\n=== LONG-TERM MEMORY (about ").append(name).append(") ===\n")
            for (f in p.memoryFacts) sb.append("• ").append(f.trim()).append("\n")
            sb.append("\nUse this memory naturally — never announce that you are recalling it.\n")
        }
        sb.append("\n=== RESPONSE RULES ===\n")
        sb.append("• Match user's language (Hinglish / Hindi / English).\n")
        sb.append("• Concise but warm. No raw JSON unless asked.\n")
        sb.append("• If an image/document is attached, describe/analyze it directly.\n")
        sb.append("• Never mention Gemini, Google, or that you are an LLM. You are \"RB Agent\".\n")
        return sb.toString()
    }

    private fun parseSuccessResponse(raw: String): Result {
        if (raw.isBlank()) return Result.Failure("Empty response from Gemini.", "blank_response")
        return try {
            val root = JSONObject(raw)
            val block = root.optJSONObject("promptFeedback")?.optString("blockReason", null)
            if (block != null && !root.has("candidates"))
                return Result.Failure("Blocked by safety filters. Try rephrasing.", "blocked: $block")
            val cands = root.optJSONArray("candidates")
            if (cands == null || cands.length() == 0)
                return Result.Failure("No response generated. Please retry.", "no_candidates")
            val first = cands.getJSONObject(0)
            val finish = first.optString("finishReason", "")
            val parts = first.optJSONObject("content")?.optJSONArray("parts")
            val builder = StringBuilder()
            if (parts != null) {
                var i = 0
                while (i < parts.length()) {
                    val t = parts.optJSONObject(i)?.optString("text", "") ?: ""
                    if (t.isNotEmpty()) builder.append(t)
                    i++
                }
            }
            val text = builder.toString().trim()
            if (text.isEmpty()) {
                val msg = when (finish) {
                    "SAFETY" -> "Response filtered for safety. Try rephrasing."
                    "MAX_TOKENS" -> "Response cut off. Try a shorter question."
                    "RECITATION" -> "Blocked due to recitation policy."
                    else -> "Gemini returned an empty response."
                }
                return Result.Failure(msg, "finish=$finish")
            }
            Result.Success(text)
        } catch (e: Exception) {
            Log.e(TAG, "parse failed: $raw", e)
            Result.Failure("Failed to parse Gemini response.", e.message)
        }
    }

    private fun mapHttpError(model: String, code: Int, body: String): Result {
        val apiMsg = try {
            JSONObject(body).optJSONObject("error")?.optString("message", "")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        val msg = when (code) {
            400 -> apiMsg ?: "Bad request — check your API key or prompt."
            401, 403 -> "API key rejected. Re-save it in Settings."
            404 -> apiMsg ?: "Model \"$model\" unavailable for this key."
            429 -> "Rate limit / quota exceeded. Wait and retry."
            in 500..599 -> "Gemini server error ($code). Try again."
            else -> apiMsg ?: "Request failed (HTTP $code)."
        }
        Log.e(TAG, "HTTP $code on $model: $body")
        return Result.Failure(msg, body, code)
    }
}
