package com.rbagent.assistant.ui.components

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rbagent.assistant.data.MemoryManager
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

@Composable
fun MemoryInspectorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val memory = remember { MemoryManager.getInstance(context) }
    var facts by remember { mutableStateOf(memory.getAll()) }
    var draft by remember { mutableStateOf("") }
    fun refresh() { facts = memory.getAll() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(22.dp))
                .background(FrostedSlateHeavy)
                .border(1.dp, FrostedBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🧠 Memory Inspector", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${facts.size} facts RB Agent remembers", color = SlateMuted, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x221E293B))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, "Close", tint = SlateMuted, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(Color(0x331E293B))
                        .border(1.dp, FrostedBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = draft, onValueChange = { draft = it },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(NeonCyan), maxLines = 3,
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) Text("Add a fact… e.g. \"I love filter coffee\"",
                                    color = SlateMuted, fontSize = 13.sp)
                                inner()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(NeonCyan)
                        .clickable { if (memory.addFact(draft.trim())) { draft = ""; refresh() } },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Add, "Add", tint = Color(0xFF04070D), modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            if (facts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No memories yet.\nRB Agent will learn as you chat.",
                        color = SlateMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(facts, key = { it.id }) { fact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color(0x331E293B))
                                .border(1.dp, FrostedBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(fact.text, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                                Text(fact.source.name.lowercase().replace('_', ' '),
                                    color = SlateMuted, fontSize = 10.sp)
                            }
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                                    .clickable { memory.removeFact(fact.id); refresh() },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Delete, "Delete", tint = ElectricPink, modifier = Modifier.size(15.dp)) }
                        }
                    }
                }
            }
        }
    }
}
