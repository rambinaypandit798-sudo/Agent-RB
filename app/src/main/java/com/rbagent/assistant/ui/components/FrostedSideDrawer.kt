package com.rbagent.assistant.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbagent.assistant.data.ChatStorageManager
import com.rbagent.assistant.data.ProjectStorageManager
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateUltra
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

@Composable
fun FrostedSideDrawer(
    sessions: List<ChatStorageManager.ChatSession>,
    activeSessionId: String,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projectStore = remember { ProjectStorageManager.getInstance(context) }
    var projects by remember { mutableStateOf(projectStore.getAll()) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var showSkillDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxHeight().width(300.dp)
            .background(FrostedSlateUltra)
            .border(1.dp, FrostedBorder, RoundedCornerShape(0.dp))
            .padding(vertical = 22.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(Color(0x3338BDF8))
                    .border(1.dp, NeonCyan.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("RB", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("RB Agent", color = TextPrimary, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp)
                Text("Personal AI Assistant", color = SlateMuted, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .background(Color(0x221E293B)).clickable { onClose() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Close, "Close", tint = SlateMuted,
                modifier = Modifier.size(16.dp)) }
        }

        Spacer(Modifier.height(16.dp))
        DrawerAction(Icons.Filled.Add, "New Chat",
            "Start a fresh conversation", NeonCyan, onNewChat)
        DrawerAction(Icons.Filled.Workspaces, "New Project",
            "Group chats into a project", NeonCyan) { showProjectDialog = true }
        DrawerAction(Icons.Filled.AutoAwesome, "Custom Skill",
            "Save a prompt as a reusable skill", ElectricPink) { showSkillDialog = true }
        DrawerAction(Icons.Filled.Psychology, "Memory Inspector",
            "See what RB remembers about you", NeonCyan) { showMemoryDialog = true }

        Spacer(Modifier.height(14.dp))

        // ── Projects list ──
        if (projects.isNotEmpty()) {
            SectionHeader("PROJECTS")
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(
                    (projects.size * 52).coerceAtMost(180).dp
                ).padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(projects, key = { it.id }) { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1A38BDF8))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Workspaces, null, tint = NeonCyan,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = TextPrimary, fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.description.isNotBlank()) Text(p.description,
                                color = SlateMuted, fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape)
                                .clickable {
                                    projectStore.deleteProject(p.id)
                                    projects = projectStore.getAll()
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Delete, "Delete", tint = SlateMuted,
                            modifier = Modifier.size(12.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Chats ──
        SectionHeader("CONVERSATIONS")
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sessions, key = { it.id }) { s ->
                SessionRow(
                    session = s,
                    isActive = s.id == activeSessionId,
                    onClick = { onSelectSession(s.id) },
                    onDelete = {
                        onDeleteSession(s.id)
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("RB Agent · v1.0 · Local-first", color = SlateMuted, fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 18.dp))
    }

    if (showProjectDialog) {
        AddProjectDialog(onDismiss = { showProjectDialog = false }) { n, _, ok ->
            projects = projectStore.getAll()
            val msg = if (ok) "Project \"$n\" created" else "Project \"$n\" already exists"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    if (showSkillDialog) {
        CustomSkillDialog(onDismiss = { showSkillDialog = false }) { n, _ ->
            Toast.makeText(context, "Skill \"$n\" saved", Toast.LENGTH_SHORT).show()
        }
    }
    if (showMemoryDialog) {
        MemoryInspectorDialog(onDismiss = { showMemoryDialog = false })
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Chat, null, tint = SlateMuted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = SlateMuted, fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
private fun DrawerAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
                .border(1.dp, tint.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = SlateMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatStorageManager.ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Color(0x3338BDF8) else Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(session.title, color = if (isActive) NeonCyan else TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${session.messages.size} messages", color = SlateMuted, fontSize = 10.sp)
        }
        Box(Modifier.size(26.dp).clip(CircleShape).clickable { onDelete() },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Delete, "Delete", tint = SlateMuted,
                modifier = Modifier.size(14.dp))
        }
    }
}
