package com.rbagent.assistant

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rbagent.assistant.data.ChatStorageManager
import com.rbagent.assistant.data.MemoryManager
import com.rbagent.assistant.data.SettingsManager
import com.rbagent.assistant.voice.ForegroundVoiceService
import com.rbagent.assistant.voice.VoiceEngineManager

class RBAgentApplication : Application() {

    companion object { private const val TAG = "RBAgentApp" }

    lateinit var settings: SettingsManager; private set
    lateinit var chatStorage: ChatStorageManager; private set
    lateinit var memory: MemoryManager; private set
    lateinit var voiceEngine: VoiceEngineManager; private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsManager.getInstance(this)
        chatStorage = ChatStorageManager.getInstance(this)
        memory = MemoryManager.getInstance(this)
        voiceEngine = VoiceEngineManager.getInstance(this)
        restoreChatHistory(); restoreVoiceProfile(); restartHotwordIfEnabled()
        Log.i(TAG, "RB Agent initialized | apiKey=${settings.hasApiKey()} | user=${settings.getUserName()} | personality=${settings.getPersonality().id}")
    }

    private fun restoreChatHistory() {
        try { val s = chatStorage.getOrCreateActiveSession(); Log.d(TAG, "Active session: ${s.title} (${s.messages.size} msgs)") }
        catch (e: Exception) { Log.e(TAG, "Chat restore failed", e) }
    }

    private fun restoreVoiceProfile() {
        try { settings.applyToVoiceEngine(voiceEngine) }
        catch (e: Exception) { Log.e(TAG, "Voice profile restore failed", e) }
    }

    private fun restartHotwordIfEnabled() {
        if (!settings.isHotwordEnabled()) return
        try {
            val intent = Intent(this, ForegroundVoiceService::class.java).apply { action = ForegroundVoiceService.ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) { Log.e(TAG, "Failed to restart hotword service", e) }
    }

    override fun onTerminate() { try { voiceEngine.shutdown() } catch (_: Exception) {}; super.onTerminate() }
}
