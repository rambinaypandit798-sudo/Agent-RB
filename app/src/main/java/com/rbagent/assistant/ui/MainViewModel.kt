package com.rbagent.assistant.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rbagent.assistant.ai.GeminiApiClient
import com.rbagent.assistant.automation.ActionDispatcher
import com.rbagent.assistant.automation.ActionParser
import com.rbagent.assistant.data.ChatStorageManager
import com.rbagent.assistant.data.CommandBus
import com.rbagent.assistant.data.MemoryManager
import com.rbagent.assistant.data.SettingsManager
import com.rbagent.assistant.voice.VoiceEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainViewModel
 * Chat pipeline + Local action dispatcher.
 *
 * IMPORTANT: Action detection is fully local (regex via ActionParser).
 * Gemini is called ONLY for conversational replies and returns plain
 * text — no function-calling, no systemHint, no enableFunctionCalling.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024
    }

    private val appContext = app.applicationContext
    private val settings = SettingsManager.getInstance(app)
    private val chatStorage = ChatStorageManager.getInstance(app)
    private val memory = MemoryManager.getInstance(app)
    private val voice = VoiceEngineManager.getInstance(app)
    private val gemini = GeminiApiClient()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val session = chatStorage.getOrCreateActiveSession()
        _uiState.update {
            it.copy(
                messages = session.messages.toList(),
                activeSessionId = session.id,
                activeSessionTitle = session.title
            )
        }
        viewModelScope.launch {
            voice.isSpeakingFlow.collect { speaking ->
                if (speaking) setAiState(AiState.SPEAKING)
                else if (_uiState.value.aiState == AiState.SPEAKING) setAiState(AiState.IDLE)
            }
        }
        voice.addCompletionListener {
            if (_uiState.value.aiState == AiState.SPEAKING) setAiState(AiState.IDLE)
        }
        viewModelScope.launch {
            CommandBus.commands.collect { cmd ->
                Log.i(TAG, "CommandBus: $cmd")
                sendMessage(cmd)
            }
        }
    }

    // ────────────────────────────────────────────────────────────
    // UI ACTIONS
    // ────────────────────────────────────────────────────────────
    fun onDraftChange(t: String) { _uiState.update { it.copy(userMessageDraft = t) } }
    fun toggleDrawer() { _uiState.update { it.copy(drawerOpen = !it.drawerOpen) } }
    fun closeDrawer() { _uiState.update { it.copy(drawerOpen = false) } }
    fun setAiState(s: AiState) { _uiState.update { it.copy(aiState = s) } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }

    fun setLiveVoiceVisible(v: Boolean) {
        _uiState.update {
            it.copy(
                liveVoiceVisible = v,
                aiState = if (v) AiState.LISTENING else AiState.IDLE
            )
        }
    }

    fun setQuickVoiceVisible(v: Boolean) { _uiState.update { it.copy(quickVoiceVisible = v) } }
    fun setAttachmentSheetVisible(v: Boolean) { _uiState.update { it.copy(attachmentSheetVisible = v) } }

    fun onAttachmentSelected(uri: Uri?, mimeHint: String?) {
        if (uri == null) {
            _uiState.update { it.copy(attachmentSheetVisible = false) }
            return
        }
        val mime = mimeHint ?: appContext.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "attachment"
        _uiState.update {
            it.copy(
                pendingAttachment = PendingAttachment(uri.toString(), mime, name),
                attachmentSheetVisible = false
            )
        }
    }

    fun clearAttachment() { _uiState.update { it.copy(pendingAttachment = null) } }

    private fun queryDisplayName(uri: Uri): String? = try {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }

    // ────────────────────────────────────────────────────────────
    // SEND MESSAGE — Local action first, Gemini for chat
    // ────────────────────────────────────────────────────────────
    fun sendMessage(text: String = _uiState.value.userMessageDraft) {
        val trimmed = text.trim()
        val attachment = _uiState.value.pendingAttachment
        if (trimmed.isEmpty() && attachment == null) return
        if (_uiState.value.isSending) return

        val sessionId = _uiState.value.activeSessionId
        if (sessionId.isEmpty()) return

        // 1) Save user message
        val userMsg = ChatStorageManager.ChatMessage(
            role = ChatStorageManager.Role.USER,
            content = trimmed,
            attachmentUri = attachment?.uri,
            attachmentMime = attachment?.mimeType,
            attachmentName = attachment?.displayName
        )
        chatStorage.appendMessage(sessionId, userMsg)
        if (trimmed.isNotBlank()) memory.autoLearnFromUserMessage(trimmed)

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                userMessageDraft = "",
                pendingAttachment = null,
                isSending = true,
                aiState = AiState.THINKING
            )
        }

        viewModelScope.launch {
            // 2) LOCAL deterministic action dispatch (regex only)
            val localAction = if (attachment == null && trimmed.isNotBlank())
                ActionParser.parseText(trimmed) else null

            if (localAction != null) {
                val result = withContext(Dispatchers.Main) {
                    ActionDispatcher.execute(appContext, localAction)
                }
                val ack = ChatStorageManager.ChatMessage(
                    role = ChatStorageManager.Role.ASSISTANT,
                    content = result.message,
                    isError = !result.success
                )
                chatStorage.appendMessage(sessionId, ack)
                _uiState.update {
                    it.copy(
                        messages = it.messages + ack,
                        isSending = false,
                        aiState = if (result.success) AiState.SPEAKING else AiState.IDLE,
                        errorMessage = if (result.success) null else result.message
                    )
                }
                if (result.success) voice.speak(result.message)
                return@launch
            }

            // 3) Optional attachment payload
            var b64: String? = null
            var mime: String? = null
            if (attachment != null) {
                val bytes = readBytes(Uri.parse(attachment.uri))
                if (bytes != null && bytes.size <= MAX_ATTACHMENT_BYTES) {
                    b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    mime = attachment.mimeType
                } else if (bytes != null) {
                    val errMsg = ChatStorageManager.ChatMessage(
                        role = ChatStorageManager.Role.ASSISTANT,
                        content = "Attachment too large. Max is 8 MB.",
                        isError = true
                    )
                    chatStorage.appendMessage(sessionId, errMsg)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + errMsg,
                            isSending = false,
                            aiState = AiState.IDLE,
                            errorMessage = "Attachment too large"
                        )
                    }
                    return@launch
                }
            }

            // 4) Gemini plain-text chat
            val params = GeminiApiClient.RequestParams(
                apiKey = settings.getApiKey(),
                personalitySystemPrompt = settings.getPersonality().systemPrompt,
                userName = settings.getUserName(),
                memoryFacts = memory.getFactStrings(),
                conversationHistory = _uiState.value.messages.dropLast(1),
                newUserMessage = trimmed.ifBlank { "Analyze the attached file." },
                attachmentBase64 = b64,
                attachmentMime = mime
            )

            when (val result = gemini.generateContent(params)) {
                is GeminiApiClient.Result.Success -> {
                    val aiMsg = ChatStorageManager.ChatMessage(
                        role = ChatStorageManager.Role.ASSISTANT,
                        content = result.text
                    )
                    chatStorage.appendMessage(sessionId, aiMsg)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + aiMsg,
                            isSending = false,
                            aiState = AiState.SPEAKING
                        )
                    }
                    voice.speak(result.text)
                }
                is GeminiApiClient.Result.Failure -> {
                    val errMsg = ChatStorageManager.ChatMessage(
                        role = ChatStorageManager.Role.ASSISTANT,
                        content = result.userMessage,
                        isError = true
                    )
                    chatStorage.appendMessage(sessionId, errMsg)
                    _uiState.update {
                        it.copy(
                            messages = it.messages + errMsg,
                            isSending = false,
                            aiState = AiState.IDLE,
                            errorMessage = result.userMessage
                        )
                    }
                }
            }
        }
    }

    private suspend fun readBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try { appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
        catch (e: Exception) { Log.e(TAG, "readBytes failed", e); null }
    }

    // ────────────────────────────────────────────────────────────
    // SESSIONS
    // ────────────────────────────────────────────────────────────
    fun newSession() {
        val s = chatStorage.createNewSession()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                activeSessionId = s.id,
                activeSessionTitle = s.title,
                drawerOpen = false,
                aiState = AiState.IDLE
            )
        }
    }

    fun switchSession(id: String) {
        val s = chatStorage.getSession(id) ?: return
        chatStorage.setActiveSessionId(id)
        _uiState.update {
            it.copy(
                messages = s.messages.toList(),
                activeSessionId = s.id,
                activeSessionTitle = s.title,
                drawerOpen = false
            )
        }
    }

    fun deleteSession(id: String) {
        chatStorage.deleteSession(id)
        if (id == _uiState.value.activeSessionId) {
            val next = chatStorage.loadAllSessions().firstOrNull()
            if (next != null) switchSession(next.id) else newSession()
        }
    }

    fun allSessions(): List<ChatStorageManager.ChatSession> = chatStorage.loadAllSessions()

    // ────────────────────────────────────────────────────────────
    // VOICE
    // ────────────────────────────────────────────────────────────
    fun onQuickVoiceResult(transcript: String) {
        setQuickVoiceVisible(false)
        _uiState.update { it.copy(userMessageDraft = transcript) }
    }

    fun onLiveVoiceResult(transcript: String) {
        setLiveVoiceVisible(false)
        if (transcript.isNotBlank()) sendMessage(transcript)
    }

    fun stopSpeaking() { voice.stop(); setAiState(AiState.IDLE) }
}
