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
import com.rbagent.assistant.ui.MainNavigation
import com.rbagent.assistant.ui.MainViewModel
import com.rbagent.assistant.ui.theme.RBAgentTheme
import com.rbagent.assistant.voice.ForegroundVoiceService

class MainActivity : ComponentActivity() {

    companion object { private const val TAG = "MainActivity" }

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> results.forEach { (p, g) -> Log.d(TAG, "Permission $p granted=$g") } }

    private val hotwordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ForegroundVoiceService.ACTION_HOTWORD_DETECTED) {
                Log.i(TAG, "Hotword broadcast received")
                viewModel.setLiveVoiceVisible(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStartupPermissions()
        setContent {
            RBAgentTheme { MainNavigation(viewModel = viewModel) }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ForegroundVoiceService.ACTION_HOTWORD_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hotwordReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(hotwordReceiver, filter)
        }
    }

    override fun onStop() {
        try { unregisterReceiver(hotwordReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun requestStartupPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}
