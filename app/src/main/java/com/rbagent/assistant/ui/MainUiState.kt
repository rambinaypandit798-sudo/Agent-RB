package com.rbagent.assistant.ui

import com.rbagent.assistant.data.ChatStorageManager

enum class AiState { IDLE, LISTENING, THINKING, SPEAKING }

enum class BottomTab(val label: String) {
    HOME("Home"), WEB("Web"), FILES("Files"), CODE("Code"), SETTINGS("Settings")
}

data class PendingAttachment(
    val uri: String,
    val mimeType: String,
    val displayName: String
)

data class MainUiState(
    val messages: List<ChatStorageManager.ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val aiState: AiState = AiState.IDLE,
    val drawerOpen: Boolean = false,
    val liveVoiceVisible: Boolean = false,
    val quickVoiceVisible: Boolean = false,
    val attachmentSheetVisible: Boolean = false,
    val pendingAttachment: PendingAttachment? = null,
    val activeSessionId: String = "",
    val activeSessionTitle: String = "New Chat",
    val modelLabel: String = "Gemini 1.5 Flash",
    val userMessageDraft: String = "",
    val errorMessage: String? = null
)
