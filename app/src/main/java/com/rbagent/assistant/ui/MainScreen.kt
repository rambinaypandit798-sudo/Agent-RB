package com.rbagent.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rbagent.assistant.ui.components.AttachmentSheet
import com.rbagent.assistant.ui.components.ChatBubble
import com.rbagent.assistant.ui.components.CentralOrb
import com.rbagent.assistant.ui.components.FrostedInputBar
import com.rbagent.assistant.ui.components.FrostedSideDrawer
import com.rbagent.assistant.ui.components.LiveVoiceOverlay
import com.rbagent.assistant.ui.components.QuickVoiceDialog
import com.rbagent.assistant.ui.components.TopHeaderBar
import com.rbagent.assistant.ui.theme.DeepSpaceEnd
import com.rbagent.assistant.ui.theme.DeepSpaceStart

@Composable
fun MainScreen(viewModel: MainViewModel) {

    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty())
            runCatching { listState.animateScrollToItem(state.messages.lastIndex) }
    }
    LaunchedEffect(state.drawerOpen) {
        if (state.drawerOpen) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed && state.drawerOpen)
            viewModel.closeDrawer()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHost.showSnackbar(it); viewModel.clearError() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize()
            ) {
                FrostedSideDrawer(
                    sessions = viewModel.allSessions(),
                    activeSessionId = state.activeSessionId,
                    onNewChat = { viewModel.newSession() },
                    onSelectSession = { id -> viewModel.switchSession(id) },
                    onDeleteSession = { id -> viewModel.deleteSession(id) },
                    onClose = { viewModel.closeDrawer() }
                )
            }
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepSpaceStart, DeepSpaceEnd)))
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHost) },
                topBar = {
                    TopHeaderBar(
                        title = "RB Agent",
                        subtitle = "Personal AI Assistant",
                        onMenuClick = { viewModel.toggleDrawer() },
                        onLiveVoiceClick = { viewModel.setLiveVoiceVisible(true) }
                    )
                },
                modifier = Modifier.imePadding()
            ) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(padding)
                        .statusBarsPadding()
                        .padding(bottom = 96.dp)
                ) {
                    if (state.messages.isEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CentralOrb(
                                state = state.aiState,
                                onClick = { viewModel.setLiveVoiceVisible(true) }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = state.messages, key = { it.id }) { msg ->
                            ChatBubble(message = msg)
                        }
                    }

                    FrostedInputBar(
                        text = state.userMessageDraft,
                        isSending = state.isSending,
                        modelLabel = state.modelLabel,
                        attachment = state.pendingAttachment,
                        onTextChange = viewModel::onDraftChange,
                        onSend = { viewModel.sendMessage() },
                        onMicClick = { viewModel.setQuickVoiceVisible(true) },
                        onLiveMicClick = { viewModel.setLiveVoiceVisible(true) },
                        onAttachClick = { viewModel.setAttachmentSheetVisible(true) },
                        onClearAttachment = { viewModel.clearAttachment() },
                        onModelClick = { },
                        onStopClick = { viewModel.stopSpeaking() }
                    )
                }
            }

            // ── Full-screen Live Voice Overlay ──
            if (state.liveVoiceVisible) {
                LiveVoiceOverlay(
                    isSpeaking = state.aiState == AiState.SPEAKING,
                    onDismiss = { viewModel.setLiveVoiceVisible(false) },
                    onTranscriptReady = { viewModel.onLiveVoiceResult(it) }
                )
            }

            // ── Quick STT dialog ──
            if (state.quickVoiceVisible) {
                QuickVoiceDialog(
                    onDismiss = { viewModel.setQuickVoiceVisible(false) },
                    onTranscriptReady = { viewModel.onQuickVoiceResult(it) }
                )
            }

            // ── Attachment bottom sheet ──
            if (state.attachmentSheetVisible) {
                AttachmentSheet(
                    onDismiss = { viewModel.setAttachmentSheetVisible(false) },
                    onPick = { uri, hint -> viewModel.onAttachmentSelected(uri, hint) }
                )
            }
        }
    }
}
