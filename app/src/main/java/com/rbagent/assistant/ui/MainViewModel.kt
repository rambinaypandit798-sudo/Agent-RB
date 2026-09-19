package com.rbagent.assistant.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rbagent.assistant.ai.GeminiApiClient
import com.rbagent.assistant.data.ChatStorageManager
import com.rbagent.assistant.data.MemoryManager
import com.rbagent.assistant.data.SettingsManager
import com.rbagent.assistant.voice.VoiceEngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object { private const val TAG = "MainViewModel" }

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
            it.copy(messages = session.messages.toList(),
                activeSessionId = session.id,
                activeSessionTitle = session.title)
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
    }

    fun onDraftChange(text: String) { _uiState.update { it.copy(userMessageDraft = text) } }
    fun toggleDrawer() { _uiState.update { it.copy(drawerOpen = !it.drawerOpen) } }
    fun closeDrawer() { _uiState.update { it.copy(drawerOpen = false) } }
    fun setLiveVoiceVisible(visible: Boolean) {
        _uiState.update { it.copy(liveVoiceVisible = visible,
            aiState = if (visible) AiState.LISTENING else AiState.IDLE) }
    }
    fun setAiState(state: AiState) { _uiState.update { it.copy(aiState = state) } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }

    fun sendMessage(text: String = _uiState.value.userMessageDraft) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isSending) return
        val sessionId = _uiState.value.activeSessionId
        if (sessionId.isEmpty()) { Log.e(TAG, "sendMessage: no active session"); return }

        val userMsg = ChatStorageManager.ChatMessage(role = ChatStorageManager.Role.USER, content = trimmed)
        chatStorage.appendMessage(sessionId, userMsg)
        memory.autoLearnFromUserMessage(trimmed)
        _uiState.update {
            it.copy(messages = it.messages + userMsg, userMessageDraft = "",
                isSending = true, aiState = AiState.THINKING)
        }

        viewModelScope.launch {
            val apiKey = settings.getApiKey()
            val personality = settings.getPersonality()
            val userName = settings.getUserName()
            val facts = memory.getFactStrings()
            val history = _uiState.value.messages.dropLast(1)

            val result = gemini.generateContent(
                GeminiApiClient.RequestParams(
                    apiKey = apiKey,
                    personalitySystemPrompt = personality.systemPrompt,
                    userName = userName,
                    memoryFacts = facts,
                    conversationHistory = history,
                    newUserMessage = trimmed
                )
            )

            when (result) {
                is GeminiApiClient.Result.Success -> {
                    val aiMsg = ChatStorageManager.ChatMessage(
                        role = ChatStorageManager.Role.ASSISTANT, content = result.text)
                    chatStorage.appendMessage(sessionId, aiMsg)
                    _uiState.update {
                        it.copy(messages = it.messages + aiMsg,
                            isSending = false, aiState = AiState.SPEAKING)
                    }
                    voice.speak(result.text)
                }
                is GeminiApiClient.Result.Failure -> {
                    val errMsg = ChatStorageManager.ChatMessage(
                        role = ChatStorageManager.Role.ASSISTANT,
                        content = result.userMessage, isError = true)
                    chatStorage.appendMessage(sessionId, errMsg)
                    _uiState.update {
                        it.copy(messages = it.messages + errMsg,
                            isSending = false, aiState = AiState.IDLE,
                            errorMessage = result.userMessage)
                    }
                }
            }
        }
    }

    fun newSession() {
        val session = chatStorage.createNewSession()
        _uiState.update {
            it.copy(messages = emptyList(), activeSessionId = session.id,
                activeSessionTitle = session.title, drawerOpen = false, aiState = AiState.IDLE)
        }
    }

    fun switchSession(sessionId: String) {
        val s = chatStorage.getSession(sessionId) ?: return
        chatStorage.setActiveSessionId(sessionId)
        _uiState.update {
            it.copy(messages = s.messages.toList(), activeSessionId = s.id,
                activeSessionTitle = s.title, drawerOpen = false)
        }
    }

    fun deleteSession(sessionId: String) {
        chatStorage.deleteSession(sessionId)
        if (sessionId == _uiState.value.activeSessionId) {
            val next = chatStorage.loadAllSessions().firstOrNull()
            if (next != null) switchSession(next.id) else newSession()
        }
    }

    fun allSessions(): List<ChatStorageManager.ChatSession> = chatStorage.loadAllSessions()

    fun onLiveVoiceResult(transcript: String) {
        setLiveVoiceVisible(false)
        if (transcript.isNotBlank()) sendMessage(transcript)
    }

    fun stopSpeaking() { voice.stop(); setAiState(AiState.IDLE) }
}
