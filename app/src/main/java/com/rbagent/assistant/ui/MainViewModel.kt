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

class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_ATTACH = 8 * 1024 * 1024
    }

    private val ctx = app.applicationContext
    private val settings = SettingsManager.getInstance(app)
    private val chat = ChatStorageManager.getInstance(app)
    private val memory = MemoryManager.getInstance(app)
    private val voice = VoiceEngineManager.getInstance(app)
    private val gemini = GeminiApiClient()

    private val _ui = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _ui.asStateFlow()

    init {
        val s = chat.getOrCreateActiveSession()
        _ui.update { it.copy(messages = s.messages.toList(),
            activeSessionId = s.id, activeSessionTitle = s.title) }
        viewModelScope.launch {
            voice.isSpeakingFlow.collect { sp ->
                if (sp) setAiState(AiState.SPEAKING)
                else if (_ui.value.aiState == AiState.SPEAKING) setAiState(AiState.IDLE)
            }
        }
        voice.addCompletionListener {
            if (_ui.value.aiState == AiState.SPEAKING) setAiState(AiState.IDLE)
        }
        // Floating overlay se commands yahan aate hain
        viewModelScope.launch {
            CommandBus.commands.collect { cmd -> sendMessage(cmd) }
        }
    }

    fun onDraftChange(t: String) { _ui.update { it.copy(userMessageDraft = t) } }
    fun toggleDrawer() { _ui.update { it.copy(drawerOpen = !it.drawerOpen) } }
    fun closeDrawer() { _ui.update { it.copy(drawerOpen = false) } }
    fun setAiState(s: AiState) { _ui.update { it.copy(aiState = s) } }
    fun clearError() { _ui.update { it.copy(errorMessage = null) } }
    fun setLiveVoiceVisible(v: Boolean) { _ui.update { it.copy(liveVoiceVisible = v,
        aiState = if (v) AiState.LISTENING else AiState.IDLE) } }
    fun setQuickVoiceVisible(v: Boolean) { _ui.update { it.copy(quickVoiceVisible = v) } }
    fun setAttachmentSheetVisible(v: Boolean) { _ui.update { it.copy(attachmentSheetVisible = v) } }

    fun onAttachmentSelected(uri: Uri?, hint: String?) {
        if (uri == null) { _ui.update { it.copy(attachmentSheetVisible = false) }; return }
        val mime = hint ?: ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = queryName(uri) ?: uri.lastPathSegment ?: "attachment"
        _ui.update { it.copy(pendingAttachment = PendingAttachment(uri.toString(), mime, name),
            attachmentSheetVisible = false) }
    }
    fun clearAttachment() { _ui.update { it.copy(pendingAttachment = null) } }

    private fun queryName(uri: Uri): String? = try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    } catch (_: Exception) { null }

    fun sendMessage(text: String = _ui.value.userMessageDraft) {
        val trimmed = text.trim()
        val att = _ui.value.pendingAttachment
        if (trimmed.isEmpty() && att == null) return
        if (_ui.value.isSending) return
        val sid = _ui.value.activeSessionId
        if (sid.isEmpty()) return

        val user = ChatStorageManager.ChatMessage(
            role = ChatStorageManager.Role.USER, content = trimmed,
            attachmentUri = att?.uri, attachmentMime = att?.mimeType,
            attachmentName = att?.displayName)
        chat.appendMessage(sid, user)
        if (trimmed.isNotBlank()) memory.autoLearnFromUserMessage(trimmed)

        _ui.update { it.copy(messages = it.messages + user, userMessageDraft = "",
            pendingAttachment = null, isSending = true, aiState = AiState.THINKING) }

        viewModelScope.launch {
            val local = if (att == null && trimmed.isNotBlank())
                ActionParser.parseText(trimmed) else null
            var hint: String? = null

            if (local != null) {
                val r = withContext(Dispatchers.Main) { ActionDispatcher.execute(ctx, local) }
                hint = if (r.success) "Action \"${r.actionLabel}\" success. ${r.message}"
                       else "Action \"${r.actionLabel}\" FAILED. ${r.message}"
                if (r.success) {
                    val ack = ChatStorageManager.ChatMessage(
                        role = ChatStorageManager.Role.ASSISTANT, content = r.message)
                    chat.appendMessage(sid, ack)
                    _ui.update { it.copy(messages = it.messages + ack,
                        isSending = false, aiState = AiState.SPEAKING) }
                    voice.speak(r.message); return@launch
                }
            }

            var b64: String? = null; var mime: String? = null
            if (att != null) {
                val bytes = readBytes(Uri.parse(att.uri))
                if (bytes != null && bytes.size <= MAX_ATTACH) {
                    b64 = Base64.encodeToString(bytes, Base64.NO_WRAP); mime = att.mimeType
                } else if (bytes != null) {
                    val e = ChatStorageManager.ChatMessage(role = ChatStorageManager.Role.ASSISTANT,
                        content = "Attachment too large. Max 8 MB.", isError = true)
                    chat.appendMessage(sid, e)
                    _ui.update { it.copy(messages = it.messages + e, isSending = false,
                        aiState = AiState.IDLE, errorMessage = "Attachment too large") }
                    return@launch
                }
            }

            val result = gemini.generateContent(GeminiApiClient.RequestParams(
                apiKey = settings.getApiKey(),
                personalitySystemPrompt = settings.getPersonality().systemPrompt,
                userName = settings.getUserName(),
                memoryFacts = memory.getFactStrings(),
                conversationHistory = _ui.value.messages.dropLast(1),
                newUserMessage = trimmed.ifBlank { "Analyze the attached file." },
                attachmentBase64 = b64, attachmentMime = mime,
                systemHint = hint, enableFunctionCalling = (local == null)))

            when (result) {
                is GeminiApiClient.Result.Success -> {
                    if (result.functionName != null && result.functionArgs != null) {
                        handleFunc(sid, result.functionName, result.functionArgs, trimmed); return@launch
                    }
                    val ai = ChatStorageManager.ChatMessage(
                        role = ChatStorageManager.Role.ASSISTANT, content = result.text)
                    chat.appendMessage(sid, ai)
                    _ui.update { it.copy(messages = it.messages + ai,
                        isSending = false, aiState = AiState.SPEAKING) }
                    voice.speak(result.text)
                }
                is GeminiApiClient.Result.Failure -> {
                    val t = hint ?: result.userMessage
                    val e = ChatStorageManager.ChatMessage(
                        role = ChatStorageManager.Role.ASSISTANT, content = t,
                        isError = hint == null)
                    chat.appendMessage(sid, e)
                    _ui.update { it.copy(messages = it.messages + e, isSending = false,
                        aiState = AiState.IDLE,
                        errorMessage = if (hint == null) result.userMessage else null) }
                }
            }
        }
    }

    private suspend fun handleFunc(sid: String, name: String,
                                   args: org.json.JSONObject, original: String) {
        val a = ActionParser.parseFunction(name, args)
        if (a == null) {
            val m = ChatStorageManager.ChatMessage(role = ChatStorageManager.Role.ASSISTANT,
                content = "Yeh kaam abhi nahi kar sakta.", isError = true)
            chat.appendMessage(sid, m)
            _ui.update { it.copy(messages = it.messages + m, isSending = false, aiState = AiState.IDLE) }
            return
        }
        val r = withContext(Dispatchers.Main) { ActionDispatcher.execute(ctx, a) }
        val hint = if (r.success) "Action \"${r.actionLabel}\" success. ${r.message}"
                   else "Action \"${r.actionLabel}\" FAILED. ${r.message}"
        val f = gemini.generateContent(GeminiApiClient.RequestParams(
            apiKey = settings.getApiKey(),
            personalitySystemPrompt = settings.getPersonality().systemPrompt,
            userName = settings.getUserName(), memoryFacts = memory.getFactStrings(),
            conversationHistory = _ui.value.messages.takeLast(6),
            newUserMessage = original, systemHint = hint, enableFunctionCalling = false))
        val text = when (f) {
            is GeminiApiClient.Result.Success -> if (f.text.isNotBlank()) f.text else r.message
            is GeminiApiClient.Result.Failure -> r.message
        }
        val ai = ChatStorageManager.ChatMessage(role = ChatStorageManager.Role.ASSISTANT,
            content = text, isError = !r.success)
        chat.appendMessage(sid, ai)
        _ui.update { it.copy(messages = it.messages + ai, isSending = false, aiState = AiState.SPEAKING) }
        voice.speak(text)
    }

    private suspend fun readBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
        catch (_: Exception) { null }
    }

    fun newSession() {
        val s = chat.createNewSession()
        _ui.update { it.copy(messages = emptyList(), activeSessionId = s.id,
            activeSessionTitle = s.title, drawerOpen = false, aiState = AiState.IDLE) }
    }
    fun switchSession(id: String) {
        val s = chat.getSession(id) ?: return
        chat.setActiveSessionId(id)
        _ui.update { it.copy(messages = s.messages.toList(), activeSessionId = s.id,
            activeSessionTitle = s.title, drawerOpen = false) }
    }
    fun deleteSession(id: String) {
        chat.deleteSession(id)
        if (id == _ui.value.activeSessionId) {
            val n = chat.loadAllSessions().firstOrNull()
            if (n != null) switchSession(n.id) else newSession()
        }
    }
    fun allSessions() = chat.loadAllSessions()

    fun onQuickVoiceResult(t: String) { setQuickVoiceVisible(false)
        _ui.update { it.copy(userMessageDraft = t) } }
    fun onLiveVoiceResult(t: String) { setLiveVoiceVisible(false)
        if (t.isNotBlank()) sendMessage(t) }
    fun stopSpeaking() { voice.stop(); setAiState(AiState.IDLE) }
}
