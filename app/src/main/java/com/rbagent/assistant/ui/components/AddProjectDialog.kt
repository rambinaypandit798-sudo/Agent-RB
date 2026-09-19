package com.rbagent.assistant.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AddProjectDialog(onDismiss: () -> Unit, onCreated: (String, String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(FrostedSlateHeavy)
                .border(1.dp, FrostedBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("📁 New Project", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Group related chats into a project workspace.", color = SlateMuted, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            LabeledField(value = name, placeholder = "Project name", onValueChange = { name = it })
            Spacer(Modifier.height(10.dp))
            LabeledField(value = desc, placeholder = "Short description (optional)",
                onValueChange = { desc = it }, maxLines = 3)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = SlateMuted, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 8.dp))
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Create", color = Color(0xFF04070D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NeonCyan)
                        .clickable(enabled = name.isNotBlank()) {
                            persistProject(context, name.trim(), desc.trim())
                            onCreated(name.trim(), desc.trim()); onDismiss()
                        }.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun LabeledField(value: String, placeholder: String, onValueChange: (String) -> Unit, maxLines: Int = 1) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0x331E293B))
            .border(1.dp, FrostedBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(NeonCyan), maxLines = maxLines,
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(placeholder, color = SlateMuted, fontSize = 13.sp); inner() }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val PROJECTS_PREFS = "rb_agent_projects"
private const val K_PROJECTS = "projects_json"

private fun persistProject(context: Context, name: String, description: String) {
    try {
        val prefs = context.getSharedPreferences(PROJECTS_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(K_PROJECTS, null)
        val arr = if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw)
        arr.put(JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("name", name); put("description", description)
            put("createdAt", System.currentTimeMillis())
        })
        prefs.edit().putString(K_PROJECTS, arr.toString()).apply()
    } catch (_: Exception) {}
}
