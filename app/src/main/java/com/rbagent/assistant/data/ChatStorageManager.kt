package com.rbagent.assistant.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ChatStorageManager private constructor(context: Context) {

    companion object {
        private const val TAG = "ChatStorageManager"
        private const val PREFS_NAME = "rb_agent_chat_cache"
        private const val KEY_SESSIONS = "sessions_json"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"

        @Volatile private var INSTANCE: ChatStorageManager? = null
        fun getInstance(context: Context): ChatStorageManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatStorageManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Role { USER, ASSISTANT, SYSTEM }

    data class ChatMessage(
        val id: String = UUID.randomUUID().toString(),
        val role: Role,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isError: Boolean = false,
        val attachmentUri: String? = null,
        val attachmentMime: String? = null,
        val attachmentName: String? = null
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("role", role.name); put("content", content)
            put("timestamp", timestamp); put("isError", isError)
            attachmentUri?.let { put("attachmentUri", it) }
            attachmentMime?.let { put("attachmentMime", it) }
            attachmentName?.let { put("attachmentName", it) }
        }
        companion object {
            fun fromJson(obj: JSONObject): ChatMessage = ChatMessage(
                id = obj.optString("id", UUID.randomUUID().toString()),
                role = runCatching { Role.valueOf(obj.getString("role")) }.getOrDefault(Role.USER),
                content = obj.optString("content", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                isError = obj.optBoolean("isError", false),
                attachmentUri = obj.optString("attachmentUri", "").takeIf { it.isNotBlank() },
                attachmentMime = obj.optString("attachmentMime", "").takeIf { it.isNotBlank() },
                attachmentName = obj.optString("attachmentName", "").takeIf { it.isNotBlank() }
            )
        }
    }

    data class ChatSession(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val messages: MutableList<ChatMessage> = mutableListOf(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("title", title)
            put("createdAt", createdAt); put("updatedAt", updatedAt)
            val arr = JSONArray(); messages.forEach { arr.put(it.toJson()) }
            put("messages", arr)
        }
        companion object {
            fun fromJson(obj: JSONObject): ChatSession {
                val messages = mutableListOf<ChatMessage>()
                val arr = obj.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) messages.add(ChatMessage.fromJson(arr.getJSONObject(i)))
                return ChatSession(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "New Chat"),
                    messages = messages,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        }
    }

    @Synchronized fun loadAllSessions(): List<ChatSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<ChatSession>(arr.length())
            for (i in 0 until arr.length()) list.add(ChatSession.fromJson(arr.getJSONObject(i)))
            list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            Log.e(TAG, "parse failed — resetting", e)
            prefs.edit().remove(KEY_SESSIONS).apply(); emptyList()
        }
    }

    @Synchronized fun saveAllSessions(sessions: List<ChatSession>) {
        try {
            val arr = JSONArray(); sessions.forEach { arr.put(it.toJson()) }
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
        } catch (e: Exception) { Log.e(TAG, "serialize failed", e) }
    }

    @Synchronized fun getSession(id: String): ChatSession? = loadAllSessions().firstOrNull { it.id == id }

    @Synchronized fun createNewSession(title: String = "New Chat"): ChatSession {
        val s = ChatSession(title = title)
        val all = loadAllSessions().toMutableList(); all.add(0, s)
        saveAllSessions(all); setActiveSessionId(s.id); return s
    }

    @Synchronized fun deleteSession(id: String) {
        val all = loadAllSessions().toMutableList(); all.removeAll { it.id == id }
        saveAllSessions(all)
        if (getActiveSessionId() == id) prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply()
    }

    @Synchronized fun appendMessage(sessionId: String, message: ChatMessage): ChatSession? {
        val all = loadAllSessions().toMutableList()
        val idx = all.indexOfFirst { it.id == sessionId }
        if (idx < 0) return null
        val s = all[idx]; s.messages.add(message)
        val newTitle = if (s.title == "New Chat" && message.role == Role.USER &&
            (message.content.isNotBlank() || message.attachmentName != null)) {
            val base = message.content.ifBlank { message.attachmentName ?: "Attachment" }
            val t = base.trim().replace("\n", " ")
            if (t.length > 40) t.substring(0, 40) + "…" else t
        } else s.title
        val updated = s.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        all[idx] = updated; saveAllSessions(all); return updated
    }

    @Synchronized fun clearSessionMessages(id: String) {
        val all = loadAllSessions().toMutableList()
        val idx = all.indexOfFirst { it.id == id }; if (idx < 0) return
        all[idx] = all[idx].copy(messages = mutableListOf(), updatedAt = System.currentTimeMillis())
        saveAllSessions(all)
    }

    @Synchronized fun clearAllHistory() {
        prefs.edit().remove(KEY_SESSIONS).remove(KEY_ACTIVE_SESSION_ID).apply()
    }

    fun setActiveSessionId(id: String) { prefs.edit().putString(KEY_ACTIVE_SESSION_ID, id).apply() }
    fun getActiveSessionId(): String? = prefs.getString(KEY_ACTIVE_SESSION_ID, null)

    @Synchronized fun getOrCreateActiveSession(): ChatSession {
        val active = getActiveSessionId()
        if (active != null) getSession(active)?.let { return it }
        val existing = loadAllSessions().firstOrNull()
        if (existing != null) { setActiveSessionId(existing.id); return existing }
        return createNewSession()
    }
}
