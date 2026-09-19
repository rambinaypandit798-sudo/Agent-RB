package com.rbagent.assistant.voice

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class VoiceEngineManager private constructor(context: Context) {

    companion object {
        private const val TAG = "VoiceEngineManager"
        private const val UTTERANCE_PREFIX = "rb_agent_utt_"
        @Volatile private var INSTANCE: VoiceEngineManager? = null
        fun getInstance(context: Context): VoiceEngineManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceEngineManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    enum class Gender { MALE, FEMALE, NEUTRAL }
    enum class Lang { ENGLISH_IN, HINDI_IN, AUTO }
    enum class State { UNINITIALIZED, READY, SPEAKING, ERROR }

    private val _stateFlow = MutableStateFlow(State.UNINITIALIZED)
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()
    private val _isSpeakingFlow = MutableStateFlow(false)
    val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow.asStateFlow()

    @Volatile var pitch: Float = 1.0f
    @Volatile var speechRate: Float = 1.0f
    @Volatile var gender: Gender = Gender.FEMALE
    @Volatile var liveMode: Boolean = false
    @Volatile var preferredLang: Lang = Lang.AUTO

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingUtterance: PendingSpeak? = null
    private val readyListeners = mutableListOf<(Boolean) -> Unit>()
    private val completionListeners = mutableListOf<() -> Unit>()
    private data class PendingSpeak(val text: String, val lang: Lang, val flushQueue: Boolean)
    private val initLock = Any()

    init {
        synchronized(initLock) {
            tts = TextToSpeech(context) { status ->
                synchronized(initLock) {
                    if (status == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        _stateFlow.value = State.READY
                        configureEngine()
                        pendingUtterance?.let { p -> pendingUtterance = null; speak(p.text, p.lang, p.flushQueue) }
                        readyListeners.forEach { it(true) }
                    } else {
                        isInitialized = false; _stateFlow.value = State.ERROR
                        Log.e(TAG, "TTS init failed with status $status")
                        readyListeners.forEach { it(false) }
                    }
                    readyListeners.clear()
                }
            }
        }
    }

    private fun configureEngine() {
        val engine = tts ?: return
        try {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _isSpeakingFlow.value = true; _stateFlow.value = State.SPEAKING }
                override fun onDone(utteranceId: String?) { _isSpeakingFlow.value = false; _stateFlow.value = State.READY; notifyCompletion() }
                @Deprecated("Deprecated in API 21") override fun onError(utteranceId: String?) { handleSpeechError(utteranceId) }
                override fun onError(utteranceId: String?, errorCode: Int) { handleSpeechError(utteranceId, errorCode) }
            })
            applyVoiceProfile()
            engine.setPitch(pitch); engine.setSpeechRate(speechRate)
        } catch (e: Exception) { Log.e(TAG, "configureEngine failed", e); _stateFlow.value = State.ERROR }
    }

    private fun handleSpeechError(utteranceId: String?, errorCode: Int = -1) {
        Log.e(TAG, "TTS error utterance=$utteranceId code=$errorCode")
        _isSpeakingFlow.value = false; _stateFlow.value = State.READY; notifyCompletion()
    }

    fun applyVoiceProfile() {
        val engine = tts ?: return
        engine.setPitch(pitch.coerceIn(0.5f, 1.8f))
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 1.6f))
        selectVoiceFor(gender, engine)
    }

    private fun selectVoiceFor(gender: Gender, engine: TextToSpeech) {
        try {
            val localesToTry = when (preferredLang) {
                Lang.ENGLISH_IN -> listOf(Locale("en", "IN"))
                Lang.HINDI_IN -> listOf(Locale("hi", "IN"), Locale("en", "IN"))
                Lang.AUTO -> listOf(Locale("en", "IN"), Locale("hi", "IN"), Locale.US)
            }
            val allVoices: Set<Voice> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) engine.voices ?: emptySet() else emptySet()
            if (allVoices.isEmpty()) {
                for (loc in localesToTry) if (engine.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) { engine.language = loc; break }
                return
            }
            val targetNames = when (gender) {
                Gender.MALE -> listOf("male", "man", "#male", "voice-m")
                Gender.FEMALE -> listOf("female", "woman", "#female", "voice-f")
                Gender.NEUTRAL -> emptyList()
            }
            val candidates = allVoices.filter { v ->
                localesToTry.any { loc -> v.locale.language.equals(loc.language, true) &&
                    (loc.country.isEmpty() || v.locale.country.equals(loc.country, true)) } && !v.isNetworkConnectionRequired
            }
            val chosen: Voice? = when {
                candidates.isEmpty() -> null
                gender == Gender.NEUTRAL -> candidates.minByOrNull { it.name.length }
                else -> candidates.firstOrNull { v -> targetNames.any { n -> v.name.contains(n, true) } }
                    ?: candidates.minByOrNull { it.name.length }
            }
            if (chosen != null) { engine.voice = chosen; Log.d(TAG, "Selected voice: ${chosen.name}") }
            else for (loc in localesToTry) if (engine.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) { engine.language = loc; break }
        } catch (e: Exception) { Log.e(TAG, "selectVoiceFor failed", e) }
    }

    private fun detectLang(text: String): Locale = when (preferredLang) {
        Lang.ENGLISH_IN -> Locale("en", "IN")
        Lang.HINDI_IN -> Locale("hi", "IN")
        Lang.AUTO -> {
            val devanagari = text.count { it.code in 0x0900..0x097F }
            val latin = text.count { it.isLetter() && it.code < 0x0250 }
            if (devanagari > latin) Locale("hi", "IN") else Locale("en", "IN")
        }
    }

    fun speak(text: String, lang: Lang = Lang.AUTO, flushQueue: Boolean = true) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !isInitialized) { pendingUtterance = PendingSpeak(text, lang, flushQueue); return }
        try {
            val loc = detectLang(text)
            if (engine.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) engine.language = loc
            val effectiveRate = if (liveMode) (speechRate * 1.08f).coerceAtMost(1.6f) else speechRate
            engine.setSpeechRate(effectiveRate.coerceIn(0.5f, 1.6f))
            engine.setPitch(pitch.coerceIn(0.5f, 1.8f))
            val utteranceId = UTTERANCE_PREFIX + UUID.randomUUID().toString()
            val mode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
            val result = engine.speak(text, mode, params, utteranceId)
            if (result == TextToSpeech.ERROR) { Log.e(TAG, "speak() returned ERROR"); _isSpeakingFlow.value = false }
            else { _isSpeakingFlow.value = true; _stateFlow.value = State.SPEAKING }
        } catch (e: Exception) { Log.e(TAG, "speak failed", e); _isSpeakingFlow.value = false }
    }

    fun speakLive(text: String) { speak(text, Lang.AUTO, true) }

    fun stop() {
        try { tts?.stop() } catch (e: Exception) { Log.e(TAG, "stop failed", e) }
        _isSpeakingFlow.value = false
        _stateFlow.value = if (isInitialized) State.READY else State.UNINITIALIZED
    }

    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { Log.e(TAG, "shutdown failed", e) }
        tts = null; isInitialized = false
        _stateFlow.value = State.UNINITIALIZED; _isSpeakingFlow.value = false
    }

    fun onReady(listener: (Boolean) -> Unit) { if (isInitialized) listener(true) else readyListeners.add(listener) }
    fun addCompletionListener(listener: () -> Unit) { completionListeners.add(listener) }
    fun removeCompletionListener(listener: () -> Unit) { completionListeners.remove(listener) }
    private fun notifyCompletion() { completionListeners.toList().forEach { cb -> runCatching { cb() } } }

    fun isHindiAvailable(): Boolean = tts?.isLanguageAvailable(Locale("hi", "IN"))?.let { it >= TextToSpeech.LANG_AVAILABLE } ?: false
    fun isIndianEnglishAvailable(): Boolean = tts?.isLanguageAvailable(Locale("en", "IN"))?.let { it >= TextToSpeech.LANG_AVAILABLE } ?: false
}
