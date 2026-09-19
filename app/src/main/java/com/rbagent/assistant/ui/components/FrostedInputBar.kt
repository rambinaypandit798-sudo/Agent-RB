package com.rbagent.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

@Composable
fun FrostedInputBar(
    text: String, isSending: Boolean, modelLabel: String,
    onTextChange: (String) -> Unit, onSend: () -> Unit,
    onMicClick: () -> Unit, onLiveMicClick: () -> Unit,
    onAttachClick: () -> Unit, onModelClick: () -> Unit,
    onStopClick: () -> Unit, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(FrostedSlateHeavy)
            .border(1.dp, FrostedBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModelPill(label = modelLabel, onClick = onModelClick)
            Spacer(Modifier.weight(1f))
            MiniFrostedButton(Icons.Filled.Add, NeonCyan, onAttachClick)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = text, onValueChange = onTextChange,
                textStyle = LocalTextStyle.current.merge(TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 20.sp)),
                cursorBrush = SolidColor(NeonCyan), maxLines = 6,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("आस्क RB एजेंट...", color = SlateMuted, fontSize = 15.sp)
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(6.dp))
            MiniFrostedButton(Icons.Filled.Mic, NeonCyan, onMicClick)
            Spacer(Modifier.width(6.dp))
            MiniFrostedButton(Icons.Filled.GraphicEq, ElectricPink, onLiveMicClick)
            Spacer(Modifier.width(6.dp))
            if (isSending) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(ElectricPink)
                        .clickable { onStopClick() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Stop, "Stop", tint = Color.White, modifier = Modifier.size(20.dp)) }
            } else {
                val enabled = text.isNotBlank()
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(if (enabled) NeonCyan else Color(0x3338BDF8))
                        .clickable(enabled = enabled) { onSend() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.ArrowUpward, "Send", tint = if (enabled) Color(0xFF04070D) else SlateMuted, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable
private fun ModelPill(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(Color(0x2238BDF8))
            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = NeonCyan, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MiniFrostedButton(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape)
            .background(Color(0x221E293B))
            .border(1.dp, FrostedBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}
