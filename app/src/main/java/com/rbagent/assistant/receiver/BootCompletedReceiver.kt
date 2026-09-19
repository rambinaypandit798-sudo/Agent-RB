package com.rbagent.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rbagent.assistant.data.SettingsManager
import com.rbagent.assistant.voice.ForegroundVoiceService

class BootCompletedReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "BootCompletedReceiver" }
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON" && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val settings = SettingsManager.getInstance(context)
        if (!settings.isHotwordEnabled()) return
        try {
            val svc = Intent(context, ForegroundVoiceService::class.java).apply { this.action = ForegroundVoiceService.ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc) else context.startService(svc)
        } catch (e: Exception) { Log.e(TAG, "Failed after boot", e) }
    }
}
