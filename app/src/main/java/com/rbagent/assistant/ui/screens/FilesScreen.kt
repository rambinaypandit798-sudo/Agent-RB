package com.rbagent.assistant.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbagent.assistant.data.ProjectStorageManager
import com.rbagent.assistant.ui.components.AddProjectDialog
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val projectStore = remember { ProjectStorageManager.getInstance(context) }
    val rootDir = remember {
        (context.getExternalFilesDir(null) ?: context.filesDir).absoluteFile
    }
    var projects by remember { mutableStateOf(projectStore.getAll()) }
    var currentDir by remember { mutableStateOf(rootDir) }
    var entries by remember { mutableStateOf(listFiles(currentDir)) }
    var showAddProject by remember { mutableStateOf(false) }

    fun refreshFiles() { entries = listFiles(currentDir) }
    fun refreshProjects() { projects = projectStore.getAll() }

    Column(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 96.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(FrostedSlateHeavy)
                .border(1.dp, FrostedBorder)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(Color(0x3338BDF8))
                    .border(1.dp, NeonCyan.copy(alpha = 0.55f), CircleShape)
                    .clickable(enabled = currentDir != rootDir) {
                        currentDir.parentFile?.let { currentDir = it; refreshFiles() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (currentDir != rootDir) Icons.Filled.ArrowBack
                                   else Icons.Filled.Folder,
                    contentDescription = "Up", tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Files & Projects", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    currentDir.absolutePath.replace(rootDir.parent ?: "", "…"),
                    color = SlateMuted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(Color(0x2238BDF8))
                    .border(1.dp, NeonCyan.copy(alpha = 0.45f), CircleShape)
                    .clickable { showAddProject = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, "Add project", tint = NeonCyan,
                    modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Projects section ──
            item {
                SectionLabel("PROJECTS (${projects.size})")
            }
            if (projects.isEmpty()) {
                item {
                    EmptyRow(
                        icon = Icons.Filled.Workspaces,
                        text = "No projects yet",
                        hint = "Tap + to create one"
                    )
                }
            } else {
                items(projects, key = { "p-${it.id}" }) { p ->
                    ProjectRow(
                        name = p.name,
                        description = p.description,
                        createdAt = p.createdAt,
                        onDelete = {
                            projectStore.deleteProject(p.id)
                            refreshProjects()
                        }
                    )
                }
            }

            // ── Device files section ──
            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel("FILES (${entries.size})")
            }
            if (entries.isEmpty()) {
                item {
                    EmptyRow(
                        icon = Icons.Filled.Description,
                        text = "Folder is empty",
                        hint = currentDir.absolutePath
                    )
                }
            } else {
                items(entries, key = { "f-${it.absolutePath}" }) { file ->
                    FileRow(
                        file = file,
                        onClick = {
                            if (file.isDirectory) {
                                currentDir = file
                                refreshFiles()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddProject) {
        AddProjectDialog(onDismiss = { showAddProject = false }) { _, _, _ ->
            refreshProjects()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = SlateMuted, fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}

@Composable
private fun EmptyRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    hint: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x800F172A))
            .border(1.dp, FrostedBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = SlateMuted, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text, color = SlateMuted, fontSize = 13.sp)
            Text(hint, color = SlateMuted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProjectRow(
    name: String,
    description: String,
    createdAt: Long,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(Color(0x3338BDF8)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Workspaces, null, tint = NeonCyan,
                modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                if (description.isNotBlank()) append(description).append(" · ")
                append(SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(Date(createdAt)))
            }
            Text(meta, color = SlateMuted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape)
                .background(Color(0x22FF2D55)).clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Delete, "Delete", tint = ElectricPink,
                modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun FileRow(file: File, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, FrostedBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(Color(0x2238BDF8)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Filled.Folder
                              else Icons.Filled.InsertDriveFile,
                contentDescription = null, tint = NeonCyan,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                if (file.isDirectory) append("Folder")
                else append(Formatter.formatShortFileSize(context, file.length()))
                append(" · ")
                append(SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                    .format(Date(file.lastModified())))
            }
            Text(meta, color = SlateMuted, fontSize = 10.sp)
        }
    }
}

private fun listFiles(dir: File): List<File> {
    val children = dir.listFiles() ?: return emptyList()
    return children.sortedWith(
        compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
    )
}
