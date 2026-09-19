package com.rbagent.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import com.rbagent.assistant.data.SettingsManager
import com.rbagent.assistant.voice.VoiceEngineManager

class CallAnnounceReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallAnnounceReceiver"
        private const val PREFS = "rb_agent_settings"
        private const val K_LAST_STATE = "last_call_state"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED && intent.action != "android.intent.action.PHONE_STATE") return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val settings = SettingsManager.getInstance(context)
        if (!settings.isCallAnnounceEnabled()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastState = prefs.getString(K_LAST_STATE, null)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING) return
                prefs.edit().putString(K_LAST_STATE, state).apply()
                val callerName = resolveCallerName(context, incomingNumber)
                val announcement = buildAnnouncement(callerName, incomingNumber, settings)
                VoiceEngineManager.getInstance(context).speak(announcement, flushQueue = true)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK, TelephonyManager.EXTRA_STATE_IDLE -> {
                prefs.edit().putString(K_LAST_STATE, state).apply()
                VoiceEngineManager.getInstance(context).stop()
            }
        }
    }

    private fun resolveCallerName(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, projection, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } finally { cursor?.close() }
        } catch (e: Exception) { Log.e(TAG, "Contact lookup failed", e); null }
    }

    private fun buildAnnouncement(callerName: String?, number: String?, settings: SettingsManager): String {
        val userName = settings.getUserName()
        val display = when { !callerName.isNullOrBlank() -> callerName; !number.isNullOrBlank() -> number; else -> "an unknown number" }
        return when { !callerName.isNullOrBlank() -> "$userName, $callerName is calling."
                       else -> "$userName, you have an incoming call from $display." }
    }
}
