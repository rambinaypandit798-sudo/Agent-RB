package com.rbagent.assistant.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.rbagent.assistant.MainActivity
import com.rbagent.assistant.data.CommandBus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating "RB" bubble jo kisi bhi app ke upar rehta hai.
 * Drag, tap, quick-voice — sab supported.
 */
class AssistantOverlayService : Service() {

    companion object {
        private const val TAG = "AssistantOverlayService"
        const val ACTION_SHOW = "com.rbagent.assistant.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.rbagent.assistant.action.HIDE_OVERLAY"
        const val EXTRA_HINT = "hint"
        private const val CHANNEL_ID = "rb_agent_overlay_channel"
        private const val NOTIF_ID = 0x0A0B0D
        private const val BUBBLE_SIZE_DP = 64
        private const val BUBBLE_MARGIN_DP = 12

        @Volatile var isRunning: Boolean = false
            private set

        fun show(context: Context, hint: String? = null) {
            if (!OverlayPermissionHelper.canDrawOverlays(context)) return
            val i = Intent(context, AssistantOverlayService::class.java).apply {
                action = ACTION_SHOW
                if (!hint.isNullOrBlank()) putExtra(EXTRA_HINT, hint)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else context.startService(i)
        }

        fun hide(context: Context) {
            try { context.startService(
                Intent(context, AssistantOverlayService::class.java).apply { action = ACTION_HIDE })
            } catch (_: Exception) {}
        }
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var panelVisible = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> { teardown(); return START_NOT_STICKY }
            else -> {
                if (!OverlayPermissionHelper.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
                startForeground(NOTIF_ID, buildNotification())
                if (!isRunning) { showBubble(); isRunning = true }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { teardown(); super.onDestroy() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                    "RB Agent Floating Assistant", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Keeps RB Agent available on top of other apps."
                    setShowBadge(false); enableVibration(false); enableLights(false)
                })
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hideIntent = Intent(this, AssistantOverlayService::class.java).apply { action = ACTION_HIDE }
        val hidePending = PendingIntent.getService(this, 1, hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("RB Agent active")
            .setContentText("Tap bubble to give a command")
            .setContentIntent(openPending).addAction(0, "Close", hidePending)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val sizePx = dpToPx(BUBBLE_SIZE_DP)
        val marginPx = dpToPx(BUBBLE_MARGIN_DP)

        val container = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E60F172A"))
                setStroke(dpToPx(2), Color.parseColor("#FFD700"))
            }
            elevation = dpToPx(8).toFloat()
        }
        container.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22FFD700"))
            }
        }, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
        container.addView(TextView(this).apply {
            text = "RB"; setTextColor(Color.parseColor("#FFD700"))
            textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val params = WindowManager.LayoutParams(sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = marginPx
            y = (resources.displayMetrics.heightPixels * 0.35f).roundToInt()
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var touchX = 0f; private var touchY = 0f
            private var startTime = 0L; private var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = e.rawX; touchY = e.rawY
                        startTime = System.currentTimeMillis(); moved = false; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).roundToInt()
                        val dy = (e.rawY - touchY).roundToInt()
                        if (abs(dx) > dpToPx(6) || abs(dy) > dpToPx(6)) moved = true
                        params.x = (startX + dx).coerceAtLeast(0)
                        params.y = (startY + dy).coerceAtLeast(0)
                        try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved && System.currentTimeMillis() - startTime < 300) togglePanel()
                        return true
                    }
                }
                return false
            }
        })

        try { windowManager.addView(container, params); bubbleView = container; bubbleParams = params }
        catch (e: Exception) { Log.e(TAG, "bubble add fail", e) }
    }

    private fun togglePanel() { if (panelVisible) hidePanel() else showPanel() }

    private fun showPanel() {
        if (panelVisible) return
        panelVisible = true
        val pad = dpToPx(10); val width = dpToPx(280)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(20).toFloat()
                setColor(Color.parseColor("#F20F172A"))
                setStroke(dpToPx(1), Color.parseColor("#66FFD700"))
            }
            elevation = dpToPx(12).toFloat()
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "RB Agent"; setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(TextView(this).apply {
            text = "✕"; setTextColor(Color.parseColor("#FF2D55")); textSize = 16f
            setPadding(pad, dpToPx(2), pad, dpToPx(2))
            setOnClickListener { hidePanel() }
        })
        root.addView(titleRow)

        val edit = EditText(this).apply {
            hint = "Ask RB Agent…"; setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.parseColor("#E2E8F0")); textSize = 13f
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#330F172A"))
                setStroke(dpToPx(1), Color.parseColor("#33E0F2FE"))
            }
            setPadding(pad, dpToPx(8), pad, dpToPx(8))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND; maxLines = 2
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEND) {
                    val t = text.toString().trim()
                    if (t.isNotEmpty()) { CommandBus.send(t); setText(""); hidePanel() }
                    true
                } else false
            }
        }
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(6), 0, 0)
        }
        inputRow.addView(edit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(TextView(this).apply {
            text = "➤"; setTextColor(Color.parseColor("#0B0F19")); textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                val t = edit.text.toString().trim()
                if (t.isNotEmpty()) { CommandBus.send(t); edit.setText(""); hidePanel() }
            }
        }, LinearLayout.LayoutParams(dpToPx(42), dpToPx(42)).apply { marginStart = dpToPx(6) })
        root.addView(inputRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8), 0, 0)
        }
        actions.addView(TextView(this).apply {
            text = "🎤  Voice"; setTextColor(Color.parseColor("#0B0F19")); textSize = 12f
            gravity = Gravity.CENTER; setPadding(pad, dpToPx(8), pad, dpToPx(8))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat(); setColor(Color.parseColor("#FF2D55"))
            }
            setOnClickListener { startQuickListening(edit) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(TextView(this).apply {
            text = "Stop assistant"; setTextColor(Color.parseColor("#94A3B8")); textSize = 11f
            gravity = Gravity.CENTER; setPadding(pad, dpToPx(8), pad, dpToPx(8))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#331E293B"))
                setStroke(dpToPx(1), Color.parseColor("#33E0F2FE"))
            }
            setOnClickListener { teardown() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dpToPx(6) })
        root.addView(actions)

        val pp = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            val bp = bubbleParams
            x = ((bp?.x ?: 0) - width + dpToPx(BUBBLE_SIZE_DP)).coerceAtLeast(dpToPx(4))
            y = ((bp?.y ?: 200) + dpToPx(BUBBLE_SIZE_DP + 8))
        }
        try { windowManager.addView(root, pp); panelView = root }
        catch (e: Exception) { Log.e(TAG, "panel add fail", e); panelVisible = false }
    }

    private fun hidePanel() {
        panelView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panelView = null; panelVisible = false; stopListening()
    }

    private fun startQuickListening(edit: EditText) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) { edit.hint = "Mic permission required"; return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { edit.hint = "STT unavailable"; return }
        stopListening(); listening = true
        val sr = SpeechRecognizer.createSpeechRecognizer(this); recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { edit.hint = "Listening…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) { edit.hint = "Try again"; listening = false }
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                listening = false
                if (t.isNotBlank()) { CommandBus.send(t); edit.setText(""); hidePanel() }
                else edit.hint = "Nothing heard"
            }
            override fun onPartialResults(p: Bundle?) {
                p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { edit.setText(it) }
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try { sr.startListening(i) } catch (_: Exception) { listening = false }
    }

    private fun stopListening() {
        if (!listening) return
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null; listening = false
    }

    private fun teardown() {
        stopListening(); hidePanel()
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubbleView = null; bubbleParams = null; isRunning = false
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()
}
