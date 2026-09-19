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

        val url = BASE_URL + "?key=" + URLEncoder.encode(params.apiKey, "UTF-8")
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

            val responseText: String = if (stream != null) {
                BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
            } else {
                ""
            }

            if (!isSuccess) {
                mapHttpError(code, responseText)
            } else {
                parseSuccessResponse(responseText)
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout", e)
            Result.Failure(
                userMessage = "Request timed out. Check your internet connection and try again.",
                technical = e.message
            )
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "No network", e)
            Result.Failure(
                userMessage = "No internet connection. Please check your network.",
                technical = e.message
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            Result.Failure(
                userMessage = "Unexpected error: " + (e.message ?: "unknown"),
                technical = e.stackTraceToString()
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildRequestBody(p: RequestParams): JSONObject {
        val root = JSONObject()

        // System instruction
        val systemParts = JSONArray()
        systemParts.put(JSONObject().put("text", buildSystemPrompt(p)))
        root.put("systemInstruction", JSONObject().put("parts", systemParts))

        // Contents: rolling window of history + new user message
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

        // Generation config
        val genConfig = JSONObject()
        genConfig.put("temperature", p.temperature.toDouble())
        genConfig.put("maxOutputTokens", p.maxOutputTokens)
        genConfig.put("topP", 0.95)
        genConfig.put("topK", 40)
        root.put("generationConfig", genConfig)

        // Safety settings
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
        sb.append(p.personalitySystemPrompt.trim())
        sb.append("\n\n")

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

    private fun parseSuccessResponse(raw: String): Result {
        if (raw.isBlank()) {
            return Result.Failure(
                userMessage = "Empty response from Gemini.",
                technical = "blank_response"
            )
        }
        return try {
            val root = JSONObject(raw)

            val feedback = root.optJSONObject("promptFeedback")
            val blockReason = feedback?.optString("blockReason", null)
            if (blockReason != null && !root.has("candidates")) {
                return Result.Failure(
                    userMessage = "The message was blocked by safety filters. Try rephrasing.",
                    technical = "blocked: " + blockReason
                )
            }

            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.Failure(
                    userMessage = "No response generated. Please try again.",
                    technical = "no_candidates"
                )
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
                return Result.Failure(userMessage = msg, technical = "finish=" + finishReason)
            }

            Result.Success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Parse failure: " + raw, e)
            Result.Failure(
                userMessage = "Failed to parse Gemini response.",
                technical = e.message
            )
        }
    }

    private fun mapHttpError(code: Int, body: String): Result {
        val apiMsg: String? = try {
            val err = JSONObject(body).optJSONObject("error")
            val m = err?.optString("message", "")
            if (m.isNullOrBlank()) null else m
        } catch (_: Exception) {
            null
        }

        val userMsg: String = when (code) {
            400 -> apiMsg ?: "Bad request. Your API key or prompt may be invalid."
            401, 403 -> "API key rejected. Open Settings → 🔑 Gemini API Key and re-save a valid key."
            404 -> "Model not found. Confirm gemini-1.5-flash is enabled for your key."
            429 -> "Rate limit or quota exceeded. Wait a moment and retry."
            in 500..599 -> "Gemini server error (" + code + "). Try again shortly."
            else -> apiMsg ?: ("Request failed (HTTP " + code + ").")
        }
        Log.e(TAG, "HTTP " + code + ": " + body)
        return Result.Failure(userMessage = userMsg, technical = body, httpCode = code)
    }
}
