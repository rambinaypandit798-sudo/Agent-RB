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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

@Composable
fun TopHeaderBar(
    title: String,
    subtitle: String,
    onMenuClick: () -> Unit,
    onLiveVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FrostedSlateHeavy)
            .border(1.dp, FrostedBorder)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp).clip(CircleShape)
                .background(Color(0x33E0F2FE))
                .border(1.dp, FrostedBorder, CircleShape)
                .clickable { onMenuClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Menu, "Open drawer", tint = NeonCyan, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = SlateMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .size(42.dp).clip(CircleShape)
                .background(Color(0x33E0F2FE))
                .border(1.dp, FrostedBorder, CircleShape)
                .clickable { onLiveVoiceClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.GraphicEq, "Live voice", tint = NeonCyan, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun FrostedIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = NeonCyan,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .background(Color(0x33E0F2FE))
            .border(1.dp, FrostedBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}
