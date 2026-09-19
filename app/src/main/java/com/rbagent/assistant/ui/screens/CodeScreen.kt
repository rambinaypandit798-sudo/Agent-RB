package com.rbagent.assistant.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

@Composable
fun CodeScreen() {
    val context = LocalContext.current
    var code by remember {
        mutableStateOf(
            """
            // RB Agent · Scratchpad
            // Write anything here, then Copy or Clear.

            fun greet(name: String): String {
                return "Hey ${'$'}name, RB Agent here!"
            }

            fun main() {
                println(greet("Sir"))
            }
            """.trimIndent()
        )
    }
    var lineCount by remember { mutableStateOf(code.lines().size) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 96.dp)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FrostedSlateHeavy)
                .border(1.dp, FrostedBorder)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(Color(0x3338BDF8))
                    .border(1.dp, NeonCyan.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Code, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Code", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("$lineCount lines · scratchpad", color = SlateMuted, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Editor ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1220))
                .border(1.dp, FrostedBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            BasicTextField(
                value = code,
                onValueChange = {
                    code = it
                    lineCount = it.lines().size
                },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(NeonCyan),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Action bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChip(
                icon = Icons.Filled.ContentCopy,
                label = "Copy",
                tint = NeonCyan,
                modifier = Modifier.weight(1f)
            ) { copyToClipboard(context, code) }

            ActionChip(
                icon = Icons.Filled.PlayArrow,
                label = "Clear",
                tint = ElectricPink,
                modifier = Modifier.weight(1f)
            ) { code = ""; lineCount = 0 }

            ActionChip(
                icon = Icons.Filled.Delete,
                label = "Reset",
                tint = SlateMuted,
                modifier = Modifier.weight(1f)
            ) {
                code = """
                // RB Agent · Scratchpad
                """.trimIndent()
                lineCount = code.lines().size
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("RB Agent Code", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}
