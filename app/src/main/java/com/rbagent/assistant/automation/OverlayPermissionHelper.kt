package com.rbagent.assistant.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * "Display over other apps" permission ka ek-baar ka grant flow.
 */
object OverlayPermissionHelper {
    private const val TAG = "OverlayPerm"
    private const val PREFS = "rb_agent_overlay"
    private const val K_PROMPTED = "prompted_once"

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun hasPromptedBefore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(K_PROMPTED, false)

    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(K_PROMPTED, true).apply()
    }

    fun requestOverlayPermission(context: Context): Boolean {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName))
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )
            markPrompted(context); true
        } catch (e: Exception) {
            Log.e(TAG, "Overlay settings open nahi hua", e)
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                )
                markPrompted(context); true
            } catch (_: Exception) { false }
        }
    }
}
