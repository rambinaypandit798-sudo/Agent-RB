package com.rbagent.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        private const val TAG = "RBAccessibilitySvc"
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${AccessibilityHelperService::class.java.name}"
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(flat)
            while (splitter.hasNext()) if (splitter.next().equals(expected, true)) return true
            return false
        }
        fun openSystemSettings(context: Context) {
            try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }
            catch (e: Exception) { Log.e(TAG, "Failed to open accessibility settings", e) }
        }
    }

    override fun onServiceConnected() { super.onServiceConnected(); Log.i(TAG, "connected") }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { Log.d(TAG, "interrupted") }
    override fun onUnbind(intent: Intent?): Boolean { Log.i(TAG, "unbound"); return super.onUnbind(intent) }
}
