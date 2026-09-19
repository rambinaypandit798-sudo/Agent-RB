package com.rbagent.assistant.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rbagent.assistant.ai.PersonalityMode
import com.rbagent.assistant.voice.VoiceEngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {

    companion object {
        private const val TAG = "SettingsManager"
        private const val PREFS_NAME = "rb_agent_settings"
        private const val K_API_KEY = "gemini_api_key"
        private const val K_USER_NAME = "user_name"
        private const val K_PERSONALITY = "personality_mode"
        private const val K_TTS_PITCH = "tts_pitch"
        private const val K_TTS_RATE = "tts_rate"
        private const val K_TTS_GENDER = "tts_gender"
        private const val K_TTS_LANG = "tts_lang"
        private const val K_LIVE_MODE = "live_mode_enabled"
        private const val K_PRIME_NUMBER = "prime_contact_number"
        private const val K_PRIME_LABEL = "prime_contact_label"
        private const val K_CALL_ANNOUNCE = "call_announce_enabled"
        private const val K_HOTWORD_ENABLED = "hotword_enabled"
        private const val DEFAULT_USER_NAME = "Sir"
        private const val DEFAULT_PRIME_LABEL = "Prime Contact"
        private const val DEFAULT_PITCH = 1.05f
        private const val DEFAULT_RATE = 1.0f

        @Volatile private var INSTANCE: SettingsManager? = null
        fun getInstance(context: Context): SettingsManager =
            INSTANCE ?: synchronized(this) { INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it } }
    }

    private val prefs: SharedPreferences = createSecurePrefs(context)

    private fun createSecurePrefs(context: Context): SharedPreferences = try {
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, PREFS_NAME, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) {
        Log.e(TAG, "Encrypted prefs failed — falling back to standard prefs", e)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _apiKeyFlow = MutableStateFlow(prefs.getString(K_API_KEY, "").orEmpty())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()
    private val _userNameFlow = MutableStateFlow(prefs.getString(K_USER_NAME, DEFAULT_USER_NAME).orEmpty().ifBlank { DEFAULT_USER_NAME })
    val userNameFlow: StateFlow<String> = _userNameFlow.asStateFlow()
    private val _personalityFlow = MutableStateFlow(PersonalityMode.fromId(prefs.getString(K_PERSONALITY, PersonalityMode.ASSISTANT.id)))
    val personalityFlow: StateFlow<PersonalityMode> = _personalityFlow.asStateFlow()
    private val _pitchFlow = MutableStateFlow(prefs.getFloat(K_TTS_PITCH, DEFAULT_PITCH))
    val pitchFlow: StateFlow<Float> = _pitchFlow.asStateFlow()
    private val _rateFlow = MutableStateFlow(prefs.getFloat(K_TTS_RATE, DEFAULT_RATE))
    val rateFlow: StateFlow<Float> = _rateFlow.asStateFlow()
    private val _genderFlow = MutableStateFlow(runCatching {
        VoiceEngineManager.Gender.valueOf(prefs.getString(K_TTS_GENDER, VoiceEngineManager.Gender.FEMALE.name) ?: VoiceEngineManager.Gender.FEMALE.name)
    }.getOrDefault(VoiceEngineManager.Gender.FEMALE))
    val genderFlow: StateFlow<VoiceEngineManager.Gender> = _genderFlow.asStateFlow()
    private val _ttsLangFlow = MutableStateFlow(runCatching {
        VoiceEngineManager.Lang.valueOf(prefs.getString(K_TTS_LANG, VoiceEngineManager.Lang.AUTO.name) ?: VoiceEngineManager.Lang.AUTO.name)
    }.getOrDefault(VoiceEngineManager.Lang.AUTO))
    val ttsLangFlow: StateFlow<VoiceEngineManager.Lang> = _ttsLangFlow.asStateFlow()
    private val _liveModeFlow = MutableStateFlow(prefs.getBoolean(K_LIVE_MODE, false))
    val liveModeFlow: StateFlow<Boolean> = _liveModeFlow.asStateFlow()
    private val _primeNumberFlow = MutableStateFlow(prefs.getString(K_PRIME_NUMBER, "").orEmpty())
    val primeNumberFlow: StateFlow<String> = _primeNumberFlow.asStateFlow()
    private val _primeLabelFlow = MutableStateFlow(prefs.getString(K_PRIME_LABEL, DEFAULT_PRIME_LABEL).orEmpty().ifBlank { DEFAULT_PRIME_LABEL })
    val primeLabelFlow: StateFlow<String> = _primeLabelFlow.asStateFlow()
    private val _callAnnounceFlow = MutableStateFlow(prefs.getBoolean(K_CALL_ANNOUNCE, false))
    val callAnnounceFlow: StateFlow<Boolean> = _callAnnounceFlow.asStateFlow()
    private val _hotwordFlow = MutableStateFlow(prefs.getBoolean(K_HOTWORD_ENABLED, false))
    val hotwordFlow: StateFlow<Boolean> = _hotwordFlow.asStateFlow()

    fun getApiKey(): String = prefs.getString(K_API_KEY, "").orEmpty()
    fun hasApiKey(): Boolean = getApiKey().isNotBlank()
    fun getUserName(): String = prefs.getString(K_USER_NAME, DEFAULT_USER_NAME).orEmpty().ifBlank { DEFAULT_USER_NAME }
    fun getPersonality(): PersonalityMode = PersonalityMode.fromId(prefs.getString(K_PERSONALITY, PersonalityMode.ASSISTANT.id))
    fun getPrimeNumber(): String = prefs.getString(K_PRIME_NUMBER, "").orEmpty()
    fun getPrimeLabel(): String = prefs.getString(K_PRIME_LABEL, DEFAULT_PRIME_LABEL).orEmpty().ifBlank { DEFAULT_PRIME_LABEL }
    fun isCallAnnounceEnabled(): Boolean = prefs.getBoolean(K_CALL_ANNOUNCE, false)
    fun isHotwordEnabled(): Boolean = prefs.getBoolean(K_HOTWORD_ENABLED, false)
    fun isLiveMode(): Boolean = prefs.getBoolean(K_LIVE_MODE, false)
    fun getPitch(): Float = prefs.getFloat(K_TTS_PITCH, DEFAULT_PITCH)
    fun getRate(): Float = prefs.getFloat(K_TTS_RATE, DEFAULT_RATE)
    fun getGender(): VoiceEngineManager.Gender = _genderFlow.value
    fun getTtsLang(): VoiceEngineManager.Lang = _ttsLangFlow.value

    fun setApiKey(value: String) { val t = value.trim(); prefs.edit().putString(K_API_KEY, t).apply(); _apiKeyFlow.value = t }
    fun setUserName(value: String) { val c = value.trim().ifBlank { DEFAULT_USER_NAME }; prefs.edit().putString(K_USER_NAME, c).apply(); _userNameFlow.value = c }
    fun setPersonality(mode: PersonalityMode) { prefs.edit().putString(K_PERSONALITY, mode.id).apply(); _personalityFlow.value = mode }
    fun setPitch(value: Float) { val v = value.coerceIn(0.5f, 1.8f); prefs.edit().putFloat(K_TTS_PITCH, v).apply(); _pitchFlow.value = v }
    fun setRate(value: Float) { val v = value.coerceIn(0.5f, 1.6f); prefs.edit().putFloat(K_TTS_RATE, v).apply(); _rateFlow.value = v }
    fun setGender(g: VoiceEngineManager.Gender) { prefs.edit().putString(K_TTS_GENDER, g.name).apply(); _genderFlow.value = g }
    fun setTtsLang(l: VoiceEngineManager.Lang) { prefs.edit().putString(K_TTS_LANG, l.name).apply(); _ttsLangFlow.value = l }
    fun setLiveMode(e: Boolean) { prefs.edit().putBoolean(K_LIVE_MODE, e).apply(); _liveModeFlow.value = e }
    fun setPrimeNumber(v: String) { val c = v.trim(); prefs.edit().putString(K_PRIME_NUMBER, c).apply(); _primeNumberFlow.value = c }
    fun setPrimeLabel(v: String) { val c = v.trim().ifBlank { DEFAULT_PRIME_LABEL }; prefs.edit().putString(K_PRIME_LABEL, c).apply(); _primeLabelFlow.value = c }
    fun setCallAnnounce(e: Boolean) { prefs.edit().putBoolean(K_CALL_ANNOUNCE, e).apply(); _callAnnounceFlow.value = e }
    fun setHotwordEnabled(e: Boolean) { prefs.edit().putBoolean(K_HOTWORD_ENABLED, e).apply(); _hotwordFlow.value = e }

    data class SettingsSnapshot(
        val apiKey: String, val userName: String, val personality: PersonalityMode,
        val pitch: Float, val rate: Float, val gender: VoiceEngineManager.Gender,
        val ttsLang: VoiceEngineManager.Lang, val liveMode: Boolean,
        val primeNumber: String, val primeLabel: String,
        val callAnnounce: Boolean, val hotwordEnabled: Boolean
    )

    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        getApiKey(), getUserName(), getPersonality(), getPitch(), getRate(), getGender(),
        getTtsLang(), isLiveMode(), getPrimeNumber(), getPrimeLabel(), isCallAnnounceEnabled(), isHotwordEnabled())

    fun saveSnapshot(s: SettingsSnapshot) {
        prefs.edit().apply {
            putString(K_API_KEY, s.apiKey.trim())
            putString(K_USER_NAME, s.userName.trim().ifBlank { DEFAULT_USER_NAME })
            putString(K_PERSONALITY, s.personality.id)
            putFloat(K_TTS_PITCH, s.pitch.coerceIn(0.5f, 1.8f))
            putFloat(K_TTS_RATE, s.rate.coerceIn(0.5f, 1.6f))
            putString(K_TTS_GENDER, s.gender.name)
            putString(K_TTS_LANG, s.ttsLang.name)
            putBoolean(K_LIVE_MODE, s.liveMode)
            putString(K_PRIME_NUMBER, s.primeNumber.trim())
            putString(K_PRIME_LABEL, s.primeLabel.trim().ifBlank { DEFAULT_PRIME_LABEL })
            putBoolean(K_CALL_ANNOUNCE, s.callAnnounce)
            putBoolean(K_HOTWORD_ENABLED, s.hotwordEnabled)
            apply()
        }
        _apiKeyFlow.value = s.apiKey.trim()
        _userNameFlow.value = s.userName.trim().ifBlank { DEFAULT_USER_NAME }
        _personalityFlow.value = s.personality
        _pitchFlow.value = s.pitch.coerceIn(0.5f, 1.8f)
        _rateFlow.value = s.rate.coerceIn(0.5f, 1.6f)
        _genderFlow.value = s.gender
        _ttsLangFlow.value = s.ttsLang
        _liveModeFlow.value = s.liveMode
        _primeNumberFlow.value = s.primeNumber.trim()
        _primeLabelFlow.value = s.primeLabel.trim().ifBlank { DEFAULT_PRIME_LABEL }
        _callAnnounceFlow.value = s.callAnnounce
        _hotwordFlow.value = s.hotwordEnabled
    }

    fun applyToVoiceEngine(engine: VoiceEngineManager) {
        engine.pitch = getPitch(); engine.speechRate = getRate(); engine.gender = getGender()
        engine.liveMode = isLiveMode(); engine.preferredLang = getTtsLang(); engine.applyVoiceProfile()
    }
}
