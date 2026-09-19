package com.rbagent.assistant.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbagent.assistant.data.ChatStorageManager
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateLight
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

@Composable
fun ChatBubble(message: ChatStorageManager.ChatMessage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isUser = message.role == ChatStorageManager.Role.USER
    val isError = message.isError
    val bubbleColor = when { isError -> Color(0x33FF2D55); isUser -> Color(0x2E38BDF8); else -> FrostedSlateLight }
    val borderColor = when { isError -> ElectricPink.copy(alpha = 0.55f); isUser -> NeonCyan.copy(alpha = 0.45f); else -> FrostedBorder }
    val labelColor = when { isError -> ElectricPink; isUser -> NeonCyan; else -> SlateMuted }
    val label = when { isError -> "RB Agent · Error"; isUser -> "You"; else -> "RB Agent" }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bubbleColor)
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                .clickable { copyToClipboard(context, message.content) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp)); Text("·", color = SlateMuted, fontSize = 11.sp)
                Spacer(Modifier.width(6.dp)); Text(formatTime(message.timestamp), color = SlateMuted, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ContentCopy, "Copy", tint = SlateMuted, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.height(6.dp))
            if (isUser) Text(message.content, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
            else MarkdownText(text = message.content)
        }
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(block.text, color = NeonCyan,
                    fontSize = when (block.level) { 1 -> 18.sp; 2 -> 16.sp; else -> 15.sp },
                    fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                is MdBlock.Bullet -> Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = NeonCyan, fontSize = 15.sp, modifier = Modifier.width(14.dp))
                    Text(inlineMarkdown(block.text), color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
                }
                is MdBlock.Paragraph -> Text(inlineMarkdown(block.text), color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
                is MdBlock.CodeBlock -> Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B1220))
                        .border(1.dp, FrostedBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) { Text(block.code, color = NeonCyan, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 18.sp) }
            }
        }
    }
}

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
}

private fun parseBlocks(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val sb = StringBuilder()
    var inCode = false
    val codeBuf = StringBuilder()
    fun flush() { if (sb.isNotBlank()) { out.add(MdBlock.Paragraph(sb.toString().trim())); sb.clear() } }
    input.split('\n').forEach { line ->
        if (line.trim().startsWith("```")) {
            if (inCode) { out.add(MdBlock.CodeBlock(codeBuf.toString().trimEnd())); codeBuf.clear(); inCode = false }
            else { flush(); inCode = true }
            return@forEach
        }
        if (inCode) { codeBuf.append(line).append('\n'); return@forEach }
        when {
            line.startsWith("### ") -> { flush(); out.add(MdBlock.Heading(3, line.removePrefix("### "))) }
            line.startsWith("## ") -> { flush(); out.add(MdBlock.Heading(2, line.removePrefix("## "))) }
            line.startsWith("# ") -> { flush(); out.add(MdBlock.Heading(1, line.removePrefix("# "))) }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flush(); out.add(MdBlock.Bullet(line.trimStart().substring(2)))
            }
            line.isBlank() -> flush()
            else -> sb.append(line).append(' ')
        }
    }
    if (inCode && codeBuf.isNotBlank()) out.add(MdBlock.CodeBlock(codeBuf.toString().trimEnd()))
    flush()
    return out
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) { append(text.substring(i + 2, end)) }; i = end + 2 }
                else { append(text[i]); i++ }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end > 0) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = NeonCyan, background = Color(0xFF0B1220))) { append(text.substring(i + 1, end)) }; i = end + 1 }
                else { append(text[i]); i++ }
            }
            text.startsWith("*", i) && i + 1 < text.length && text[i + 1] != ' ' -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }; i = end + 1 }
                else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("RB Agent", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

private fun formatTime(ts: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
