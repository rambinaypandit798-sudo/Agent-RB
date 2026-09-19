package com.rbagent.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rbagent.assistant.ui.AiState
import com.rbagent.assistant.ui.theme.OrbIdle
import com.rbagent.assistant.ui.theme.OrbListening
import com.rbagent.assistant.ui.theme.OrbSpeaking
import com.rbagent.assistant.ui.theme.OrbThinking

@Composable
fun CentralOrb(
    state: AiState,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    onClick: () -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulseDuration = when (state) {
        AiState.IDLE -> 2000; AiState.LISTENING -> 700
        AiState.THINKING -> 1200; AiState.SPEAKING -> 900
    }
    val pulse by transition.animateFloat(
        initialValue = 0.90f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(pulseDuration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val color = when (state) {
        AiState.IDLE -> OrbIdle; AiState.LISTENING -> OrbListening
        AiState.THINKING -> OrbThinking; AiState.SPEAKING -> OrbSpeaking
    }

    Box(modifier = modifier.size(size).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val baseR = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.12f), Color.Transparent),
                    center = c, radius = baseR * pulse),
                radius = baseR * pulse, center = c
            )
            if (state == AiState.THINKING) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, color.copy(alpha = 0.6f),
                            Color.Transparent, color.copy(alpha = 0.6f), Color.Transparent),
                        center = c),
                    radius = baseR * 0.85f, center = c
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.45f), color.copy(alpha = 0.10f)),
                    center = c, radius = baseR * 0.68f),
                radius = baseR * 0.62f, center = c
            )
            val hc = Offset(c.x - baseR * 0.20f, c.y - baseR * 0.26f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.65f), Color.Transparent),
                    center = hc, radius = baseR * 0.30f),
                radius = baseR * 0.26f, center = hc
            )
        }
    }
}
