package com.rbagent.assistant.ui.screens

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbagent.assistant.ai.PersonalityMode
import com.rbagent.assistant.data.ChatStorageManager
import com.rbagent.assistant.data.MemoryManager
import com.rbagent.assistant.data.SettingsManager
import com.rbagent.assistant.service.AccessibilityHelperService
import com.rbagent.assistant.ui.theme.DeepSpaceEnd
import com.rbagent.assistant.ui.theme.DeepSpaceStart
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary
import com.rbagent.assistant.voice.VoiceEngineManager

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val voice = remember { VoiceEngineManager.getInstance(context) }
    val memory = remember { MemoryManager.getInstance(context) }
    val chatStorage = remember { ChatStorageManager.getInstance(context) }

    var apiKey by remember { mutableStateOf(settings.getApiKey()) }
    var userName by remember { mutableStateOf(settings.getUserName()) }
    var primeNumber by remember { mutableStateOf(settings.getPrimeNumber()) }
    var pitch by remember { mutableFloatStateOf(settings.getPitch()) }
    var rate by remember { mutableFloatStateOf(settings.getRate()) }
    var gender by remember { mutableStateOf(settings.getGender()) }
    var ttsLang by remember { mutableStateOf(settings.getTtsLang()) }
    var liveMode by remember { mutableStateOf(settings.isLiveMode()) }
    var personality by remember { mutableStateOf(settings.getPersonality()) }
    var callAnnounce by remember { mutableStateOf(settings.isCallAnnounceEnabled()) }
    var hotwordEnabled by remember { mutableStateOf(settings.isHotwordEnabled()) }
    var memoryFacts by remember { mutableStateOf(memory.getAll()) }
    var newFact by remember { mutableStateOf("") }
    var showClearMemConfirm by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(AccessibilityHelperService.isEnabled(context)) }

    LaunchedEffect(Unit) { accessibilityEnabled = AccessibilityHelperService.isEnabled(context) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpaceStart, DeepSpaceEnd)))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 100.dp, start = 14.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Color(0x33E0F2FE))
                            .border(1.dp, FrostedBorder, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.ArrowBack, "Back", tint = NeonCyan, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Settings", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("RB Agent configuration", color = SlateMuted, fontSize = 11.sp)
                    }
                }
            }

            item {
                SettingsCard("🔑 Gemini API Key", "Required for AI responses") {
                    SecureField(apiKey, "Paste your Gemini API key…") { apiKey = it }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (settings.hasApiKey()) "✅ Key saved — you won't be asked again" else "⚠️ No key saved yet",
                        color = if (settings.hasApiKey()) NeonCyan else ElectricPink, fontSize = 11.sp)
                }
            }

            item {
                SettingsCard("🗣️ Voice Engine (100% Free, On-Device)", "en-IN · hi-IN · Native Android TTS") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hindi available:", color = SlateMuted, fontSize = 11.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(if (voice.isHindiAvailable()) "✅" else "❌ install in TTS settings",
                            color = if (voice.isHindiAvailable()) NeonCyan else ElectricPink, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    SliderRow("Pitch", pitch, 0.5f, 1.8f) { pitch = it }
                    SliderRow("Speech rate", rate, 0.5f, 1.6f) { rate = it }
                    Spacer(Modifier.height(8.dp))
                    Text("Tone / Gender", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceEngineManager.Gender.entries.forEach { g ->
                            Chip(g.name.lowercase().replaceFirstChar { it.uppercase() }, g == gender) { gender = g }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Language preference", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceEngineManager.Lang.entries.forEach { l ->
                            Chip(
                                when (l) {
                                    VoiceEngineManager.Lang.ENGLISH_IN -> "English (IN)"
                                    VoiceEngineManager.Lang.HINDI_IN -> "हिन्दी"
                                    VoiceEngineManager.Lang.AUTO -> "Auto"
                                }, l == ttsLang) { ttsLang = l }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Natural Voice (Live Mode)", color = TextPrimary, fontSize = 13.sp)
                            Text("Faster, warmer cadence for live conversations", color = SlateMuted, fontSize = 11.sp)
                        }
                        Switch(checked = liveMode, onCheckedChange = { liveMode = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.4f)))
                    }
                }
            }

            item {
                SettingsCard("👤 Your Name", "Used across all prompts and announcements") {
                    LabeledField(userName, "e.g. Arjun") { userName = it }
                }
            }

            item {
                PrimeContactCard(
                    number = primeNumber,
                    onNumberChange = { primeNumber = it },
                    onCall = { n -> placeCall(context, n) },
                    onDial = { n -> placeDial(context, n) },
                    onSms = { n -> sendSms(context, n) }
                )
            }

            item {
                SettingsCard("💖 Personality Mode", "Choose RB Agent's tone of voice") {
                    PersonalityMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { personality = mode }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioDot(selected = personality == mode)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("${mode.emoji}  ${mode.displayName}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(mode.subtitle, color = SlateMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard("🧠 Long-Term Memory", "${memoryFacts.size} facts stored") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(Color(0x331E293B))
                                .border(1.dp, FrostedBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            BasicTextField(
                                value = newFact, onValueChange = { newFact = it },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(NeonCyan), maxLines = 3,
                                decorationBox = { inner ->
                                    Box {
                                        if (newFact.isEmpty()) Text("Add a fact — e.g. \"I love cricket\"",
                                            color = SlateMuted, fontSize = 12.sp)
                                        inner()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(NeonCyan)
                                .clickable {
                                    if (memory.addFact(newFact.trim())) { newFact = ""; memoryFacts = memory.getAll() }
                                    else Toast.makeText(context, "Already stored", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("+", color = Color(0xFF04070D), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(12.dp))
                    memoryFacts.take(5).forEach { fact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0x221E293B))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fact.text, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape)
                                .clickable { memory.removeFact(fact.id); memoryFacts = memory.getAll() },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Delete, null, tint = ElectricPink, modifier = Modifier.size(13.dp)) }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (memoryFacts.size > 5) Text("+ ${memoryFacts.size - 5} more — view in Memory Inspector",
                        color = SlateMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PinkButton("Clear Memory") { showClearMemConfirm = true }
                        PinkButton("Clear Chat") { showClearChatConfirm = true }
                    }
                }
            }

            item {
                SettingsCard("🛡️ Permissions & System Services", "Grant access for full functionality") {
                    StatusRow("Device Administrator", isDeviceAdminActive(context))
                    Spacer(Modifier.height(8.dp))
                    StatusRow("Accessibility Service", accessibilityEnabled)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill("Grant Permissions") {
                            permLauncher.launch(arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.SEND_SMS))
                            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            runCatching { context.startActivity(i) }
                        }
                        ActionPill("Accessibility") { AccessibilityHelperService.openSystemSettings(context) }
                    }
                    Spacer(Modifier.height(8.dp))
                    ActionPill("Set as Default Assistant") { openDefaultAssistantSettings(context) }
                }
            }

            item {
                SettingsCard("📢 Call Announce", "RB Agent speaks the caller's name aloud") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Announce incoming calls", color = TextPrimary, fontSize = 14.sp)
                            Text("Uses on-device TTS. Requires READ_PHONE_STATE + READ_CONTACTS.",
                                color = SlateMuted, fontSize = 11.sp)
                        }
                        Switch(checked = callAnnounce, onCheckedChange = { callAnnounce = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.4f)))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Background hotword \"Hey RB\"", color = TextPrimary, fontSize = 14.sp)
                            Text("Listens for \"Hey RB\" / \"हे आरबी\" / \"सुनो आरबी\"",
                                color = SlateMuted, fontSize = 11.sp)
                        }
                        Switch(checked = hotwordEnabled, onCheckedChange = { hotwordEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricPink, checkedTrackColor = ElectricPink.copy(alpha = 0.4f)))
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(ElectricPink, Color(0xFFB91C4A))))
                        .clickable {
                            settings.saveSnapshot(SettingsManager.SettingsSnapshot(
                                apiKey = apiKey, userName = userName, personality = personality,
                                pitch = pitch, rate = rate, gender = gender, ttsLang = ttsLang,
                                liveMode = liveMode, primeNumber = primeNumber,
                                primeLabel = settings.getPrimeLabel(),
                                callAnnounce = callAnnounce, hotwordEnabled = hotwordEnabled))
                            settings.applyToVoiceEngine(voice)
                            Toast.makeText(context, "✅ Settings saved", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("💾  SAVE SETTINGS", color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }

    if (showClearMemConfirm) {
        ConfirmDialog("Clear all memory?",
            "RB Agent will forget every fact it has learned. This cannot be undone.",
            onConfirm = {
                memory.clearAll(); memoryFacts = memory.getAll(); showClearMemConfirm = false
                Toast.makeText(context, "Memory cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearMemConfirm = false })
    }
    if (showClearChatConfirm) {
        ConfirmDialog("Clear all chat history?",
            "Every conversation will be permanently deleted.",
            onConfirm = {
                chatStorage.clearAllHistory(); chatStorage.getOrCreateActiveSession()
                showClearChatConfirm = false
                Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearChatConfirm = false })
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(FrostedSlateHeavy)
            .border(1.dp, FrostedBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = SlateMuted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SecureField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0x331E293B))
            .border(1.dp, FrostedBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(NeonCyan),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(placeholder, color = SlateMuted, fontSize = 12.sp); inner() }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LabeledField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0x331E293B))
            .border(1.dp, FrostedBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(NeonCyan), singleLine = true,
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(placeholder, color = SlateMuted, fontSize = 13.sp); inner() }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(String.format("%.2f", value), color = NeonCyan, fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) NeonCyan.copy(alpha = 0.22f) else Color(0x221E293B))
            .border(1.dp, if (selected) NeonCyan.copy(alpha = 0.65f) else FrostedBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) NeonCyan else TextPrimary, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        modifier = Modifier.size(20.dp).clip(CircleShape)
            .border(1.5.dp, if (selected) NeonCyan else SlateMuted, CircleShape),
        contentAlignment = Alignment.Center
    ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(NeonCyan)) }
}

@Composable
private fun PinkButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(Color(0x33FF2D55))
            .border(1.dp, ElectricPink.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(label, color = ElectricPink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ActionPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(Color(0x2238BDF8))
            .border(1.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(label, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun StatusRow(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null,
            tint = if (active) NeonCyan else SlateMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (active) "✅ Running" else "❌ Off",
            color = if (active) NeonCyan else ElectricPink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrimeContactCard(
    number: String, onNumberChange: (String) -> Unit,
    onCall: (String) -> Unit, onDial: (String) -> Unit, onSms: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0x33FF2D55), Color(0x220F172A))))
            .border(1.dp, ElectricPink.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(ElectricPink).padding(horizontal = 8.dp, vertical = 3.dp)
            ) { Text("PRIME", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Star, null, tint = ElectricPink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Priority Contact", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text("Say: \"Call my close friend\" or \"Message my love\"", color = SlateMuted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        LabeledField(number, "+91 90000 00000", onNumberChange)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Call") { if (number.isNotBlank()) onCall(number) }
            ActionPill("Dial") { if (number.isNotBlank()) onDial(number) }
            ActionPill("SMS") { if (number.isNotBlank()) onSms(number) }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = SlateMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm", color = ElectricPink, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SlateMuted) } },
        containerColor = FrostedSlateHeavy
    )
}

private fun placeCall(context: Context, number: String) {
    val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED else true
    try {
        if (hasPerm) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } else placeDial(context, number)
    } catch (e: Exception) { Toast.makeText(context, "Cannot place call: ${e.message}", Toast.LENGTH_SHORT).show() }
}

private fun placeDial(context: Context, number: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (e: Exception) { Toast.makeText(context, "Cannot dial: ${e.message}", Toast.LENGTH_SHORT).show() }
}

private fun sendSms(context: Context, number: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:${Uri.encode(number)}"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (e: Exception) { Toast.makeText(context, "Cannot open SMS: ${e.message}", Toast.LENGTH_SHORT).show() }
}

private fun openDefaultAssistantSettings(context: Context) {
    val candidates = listOf(
        Settings.ACTION_VOICE_INPUT_SETTINGS,
        "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
        Settings.ACTION_SETTINGS
    )
    for (action in candidates) {
        try { context.startActivity(Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }); return }
        catch (_: Exception) { }
    }
    Toast.makeText(context, "Open Settings → Apps → Default apps → Digital assistant", Toast.LENGTH_LONG).show()
}

private fun isDeviceAdminActive(context: Context): Boolean = try {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(context, "com.rbagent.assistant.receiver.RBDeviceAdminReceiver")
    dpm.isAdminActive(admin)
} catch (_: Exception) { false }
