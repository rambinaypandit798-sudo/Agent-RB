package com.rbagent.assistant.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rbagent.assistant.ui.AiState
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary
import kotlin.math.sin

@Composable
fun LiveVoiceOverlay(
    isSpeaking: Boolean,
    onDismiss: () -> Unit,
    onTranscriptReady: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var partial by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf("") }
    var rmsLevel by remember { mutableStateOf(0f) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) { if (!permissionGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { Log.w("LiveVoiceOverlay", "unavailable"); return@LaunchedEffect }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { rmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { rmsLevel = 0f }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) { finalText = text; onTranscriptReady(text) }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try { sr.startListening(intent) } catch (e: Exception) { Log.e("LiveVoiceOverlay", "start failed", e) }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recognizer?.stopListening() } catch (_: Exception) {}
            try { recognizer?.destroy() } catch (_: Exception) {}
            recognizer = null
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xF2080C14)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(22.dp).size(42.dp)
                .clip(CircleShape).background(Color(0x331E293B))
                .border(1.dp, FrostedBorder, CircleShape)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Close, "Close", tint = SlateMuted, modifier = Modifier.size(20.dp)) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp)
        ) {
            Text(
                if (isSpeaking) "RB AGENT · SPEAKING" else "RB AGENT · LISTENING",
                color = if (isSpeaking) NeonCyan else ElectricPink,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp
            )
            Spacer(Modifier.height(28.dp))
            CentralOrb(state = if (isSpeaking) AiState.SPEAKING else AiState.LISTENING, size = 200.dp, onClick = {})
            Spacer(Modifier.height(28.dp))
            Equalizer5Band(level = rmsLevel, active = !isSpeaking,
                modifier = Modifier.fillMaxWidth().height(70.dp))
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC0F172A))
                    .border(1.dp, FrostedBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    finalText.ifBlank { partial }.ifBlank { if (isSpeaking) "RB is answering…" else "Speak now…" },
                    color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(ElectricPink)
                    .clickable {
                        try { recognizer?.stopListening() } catch (_: Exception) {}
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) { Icon(if (isSpeaking) Icons.Filled.MicOff else Icons.Filled.Mic, "Stop", tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun Equalizer5Band(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(modifier = modifier) {
        val bars = 5
        val gap = size.width * 0.04f
        val barWidth = (size.width - gap * (bars + 1)) / bars
        val maxH = size.height
        val baseLevel = if (active) level.coerceIn(0.12f, 1f) else 0.35f
        for (i in 0 until bars) {
            val wobble = (sin(phase + i * 1.1f) * 0.5f + 0.5f)
            val hf = (baseLevel * 0.6f + wobble * 0.6f).coerceIn(0.08f, 1f)
            val h = maxH * hf
            val x = gap + i * (barWidth + gap)
            val y = (maxH - h) / 2f
            drawRoundRect(
                color = NeonCyan.copy(alpha = 0.9f),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
