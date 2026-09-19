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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

/**
 * QuickVoiceDialog
 * Lightweight one-shot STT — captures a single utterance, drops
 * the transcript into the composer, and dismisses. Sending is
 * still up to the user (matches the "quick dictation" UX).
 */
@Composable
fun QuickVoiceDialog(
    onDismiss: () -> Unit,
    onTranscriptReady: (String) -> Unit
) {
    val context = LocalContext.current
    var partial by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf("") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { g -> permissionGranted = g }

    LaunchedEffect(Unit) { if (!permissionGranted) permLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("QuickVoiceDialog", "unavailable"); return@LaunchedEffect
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { Log.d("QuickVoiceDialog", "err $error") }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) finalText = text
            }
            override fun onPartialResults(pr: Bundle?) {
                partial = pr?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
            }
            override fun onEvent(e: Int, p: Bundle?) {}
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
        try { sr.startListening(intent) } catch (e: Exception) { Log.e("QuickVoiceDialog", "start", e) }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recognizer?.stopListening() } catch (_: Exception) {}
            try { recognizer?.destroy() } catch (_: Exception) {}
            recognizer = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FrostedSlateHeavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(ElectricPink.copy(alpha = 0.18f))
                        .border(1.dp, ElectricPink.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Mic, null, tint = ElectricPink,
                        modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Quick voice", color = TextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Tap check when done speaking", color = SlateMuted, fontSize = 11.sp)
                }
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x331E293B))
                    .border(1.dp, FrostedBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = finalText.ifBlank { partial }.ifBlank { "Speak now…" },
                    color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Start
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = finalText.ifBlank { partial }.trim()
                if (t.isNotBlank()) onTranscriptReady(t) else onDismiss()
            }) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Check, null, tint = NeonCyan,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use", color = NeonCyan, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Close, null, tint = SlateMuted,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel", color = SlateMuted)
                }
            }
        }
    )
}
