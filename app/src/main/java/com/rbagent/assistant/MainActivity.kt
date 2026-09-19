package com.rbagent.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.rbagent.assistant.automation.OverlayPermissionHelper
import com.rbagent.assistant.ui.MainNavigation
import com.rbagent.assistant.ui.MainViewModel
import com.rbagent.assistant.ui.theme.RBAgentTheme
import com.rbagent.assistant.voice.ForegroundVoiceService

class MainActivity : ComponentActivity() {
    companion object { private const val TAG = "MainActivity" }

    private val viewModel: MainViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { r -> r.forEach { (p, g) -> Log.d(TAG, "$p=$g") } }

    private val hotwordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ForegroundVoiceService.ACTION_HOTWORD_DETECTED)
                viewModel.setLiveVoiceVisible(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStartupPermissions()
        maybeRequestOverlayOnce()
        setContent { RBAgentTheme { MainNavigation(viewModel = viewModel) } }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter(ForegroundVoiceService.ACTION_HOTWORD_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(hotwordReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(hotwordReceiver, f)
        }
    }

    override fun onStop() {
        try { unregisterReceiver(hotwordReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun maybeRequestOverlayOnce() {
        if (OverlayPermissionHelper.canDrawOverlays(this)) return
        if (OverlayPermissionHelper.hasPromptedBefore(this)) return
        OverlayPermissionHelper.requestOverlayPermission(this)
    }

    /**
     * Startup permission batch — includes CALL_PHONE + SEND_SMS so that
     * commands like "call Papa" and "message Mom" execute DIRECTLY
     * without falling back to the dialer.
     */
    private fun requestStartupPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }
}
