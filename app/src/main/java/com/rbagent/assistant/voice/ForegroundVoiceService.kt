package com.rbagent.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rbagent.assistant.MainActivity
import com.rbagent.assistant.R
import com.rbagent.assistant.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class ForegroundVoiceService : Service() {

    companion object {
        private const val TAG = "ForegroundVoiceService"
        const val ACTION_START = "com.rbagent.assistant.action.START_HOTWORD"
        const val ACTION_STOP = "com.rbagent.assistant.action.STOP_HOTWORD"
        const val ACTION_HOTWORD_DETECTED = "com.rbagent.assistant.action.HOTWORD_DETECTED"
        const val EXTRA_WAKE_WORD = "wake_word"
        private const val CHANNEL_ID = "rb_agent_hotword_channel"
        private const val NOTIF_ID = 0x0A0B0C
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK_MS = 100
        private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MS / 1000
        private const val RMS_GATE = 900.0
        private const val WAKE_LOCK_TAG = "rb_agent:hotword"
        private val WAKE_WORDS = listOf("hey rb", "hey are bee", "hey r b", "हे आरबी", "हे आर बी", "सुनो आरबी", "सुनो आर बी", "अरे आरबी")

        @Volatile var isRunning: Boolean = false
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val stopFlag = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var settings: SettingsManager
    private lateinit var recognizerBridge: SpeechRecognizerBridge

    override fun onCreate() {
        super.onCreate()
        settings = SettingsManager.getInstance(this)
        recognizerBridge = SpeechRecognizerBridge(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            else -> {
                if (!settings.isHotwordEnabled()) { Log.d(TAG, "Hotword disabled"); shutdown(); return START_NOT_STICKY }
                startForeground(NOTIF_ID, buildNotification())
                startListening()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { shutdown(); super.onDestroy() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "RB Agent Hotword Listener", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps RB Agent listening for \"Hey RB\" in the background."
                    setShowBadge(false); enableVibration(false); enableLights(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, ForegroundVoiceService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rb_agent_notification)
            .setContentTitle("RB Agent is listening")
            .setContentText("Say \"Hey RB\" to start a live conversation")
            .setContentIntent(openPending).addAction(0, "Stop", stopPending)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    private fun startListening() {
        if (isRunning) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { Log.e(TAG, "Invalid min buffer"); return }
        val bufferSize = maxOf(minBuf, CHUNK_SAMPLES * 2 * 8)
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        } catch (e: SecurityException) { Log.e(TAG, "RECORD_AUDIO missing", e); shutdown(); return }
          catch (e: Exception) { Log.e(TAG, "AudioRecord init failed", e); shutdown(); return }
        val record = audioRecord ?: return
        if (record.state != AudioRecord.STATE_INITIALIZED) { Log.e(TAG, "AudioRecord not initialized"); shutdown(); return }
        stopFlag.set(false); isRunning = true
        captureJob = serviceScope.launch { captureLoop(record) }
        Log.d(TAG, "Hotword listener started")
    }

    private suspend fun captureLoop(record: AudioRecord) {
        record.startRecording()
        val buf = ShortArray(CHUNK_SAMPLES)
        try {
            while (serviceScope.isActive && !stopFlag.get()) {
                val read = record.read(buf, 0, buf.size)
                if (read <= 0) { delay(20); continue }
                val rms = computeRms(buf, read)
                if (rms >= RMS_GATE) {
                    acquireTemporaryWakeLock()
                    recognizerBridge.feedUtterance { recognizedText ->
                        val normalized = recognizedText.lowercase().trim()
                        val matched = WAKE_WORDS.firstOrNull { normalized.contains(it) }
                        if (matched != null) {
                            Log.i(TAG, "Hotword detected: $matched")
                            fireHotwordBroadcast(matched)
                            serviceScope.launch { delay(4_000) }
                        }
                    }
                    delay(80)
                } else delay(CHUNK_MS.toLong())
            }
        } catch (e: Exception) { Log.e(TAG, "captureLoop crashed", e) }
        finally { runCatching { record.stop() }; releaseWakeLock() }
    }

    private fun computeRms(buf: ShortArray, len: Int): Double {
        if (len <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until len) { val v = buf[i].toDouble(); sum += v * v }
        return sqrt(sum / len)
    }

    private fun acquireTemporaryWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false); acquire(3_000L)
            }
        } catch (e: Exception) { Log.e(TAG, "WakeLock acquire failed", e) }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
    }

    private fun fireHotwordBroadcast(word: String) {
        val intent = Intent(ACTION_HOTWORD_DETECTED).apply {
            setPackage(packageName); putExtra(EXTRA_WAKE_WORD, word)
        }
        sendBroadcast(intent)
    }

    private fun shutdown() {
        stopFlag.set(true); captureJob?.cancel(); captureJob = null
        try { audioRecord?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop(); it.release() } }
        catch (e: Exception) { Log.e(TAG, "AudioRecord release failed", e) }
        audioRecord = null; recognizerBridge.destroy(); releaseWakeLock(); isRunning = false
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }
}
