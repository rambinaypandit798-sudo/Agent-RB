package com.rbagent.assistant.automation

import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

/**
 * ActionParser — Pure regex matcher (no function-calling, no JSON).
 * Deterministic, fast, 100% local.
 */
object ActionParser {

    private const val TAG = "ActionParser"

    val APP_PACKAGES: Map<String, String> = mapOf(
        "youtube" to "com.google.android.youtube",
        "yt" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "camera" to "com.android.camera2",
        "calculator" to "com.google.android.calculator",
        "calc" to "com.google.android.calculator",
        "settings" to "com.android.settings",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "fb" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "spotify" to "com.spotify.music",
        "play store" to "com.android.vending",
        "netflix" to "com.netflix.mediaclient",
        "twitter" to "com.twitter.android",
        "snapchat" to "com.snapchat.android",
        "linkedin" to "com.linkedin.android",
        "zoom" to "us.zoom.videomeetings",
        "drive" to "com.google.android.apps.docs",
        "photos" to "com.google.android.apps.photos",
        "clock" to "com.google.android.deskclock",
        "calendar" to "com.google.android.calendar",
        "contacts" to "com.google.android.contacts",
        "phone" to "com.google.android.dialer",
        "dialer" to "com.google.android.dialer",
        "messages" to "com.google.android.apps.messaging",
        "files" to "com.google.android.documentsui"
    )

    private fun compile(
        regex: String,
        builder: (java.util.regex.Matcher) -> RBAction?
    ): Pair<Pattern, (java.util.regex.Matcher) -> RBAction?> =
        Pattern.compile(regex) to builder

    private val PATTERNS: List<Pair<Pattern, (java.util.regex.Matcher) -> RBAction?>> = listOf(
        // Close assistant
        compile("""(?i)^(?:stop|close|hide|dismiss|exit|end)\s+(?:the\s+)?(?:assistant|agent|rb|overlay|bubble|listening)$""") { _ -> RBAction.CloseAssistant },
        compile("""(?i)^(?:goodbye|bye)\s+(?:rb|agent|assistant)$""") { _ -> RBAction.CloseAssistant },

        // YouTube — order matters (most specific first)
        compile("""(?i)^(?:open\s+|launch\s+|start\s+)?youtube\s+(?:app\s+)?(?:and\s+)?(?:play|search|find|sunao|bajao|chalao|dikhao)\s+(.+)$""") { m -> RBAction.PlayYouTube(m.group(1)!!.trim()) },
        compile("""(?i)^(?:play|search|find|sunao|bajao|chalao|dikhao)\s+(.+?)\s+(?:on|in|pe|par)\s+youtube$""") { m -> RBAction.PlayYouTube(m.group(1)!!.trim()) },
        compile("""(?i)^(.+?)\s+(?:songs?|gana|gaana|music|videos?)\s+(?:play|sunao|bajao|chalao|dikhao)\s*(?:karo|kar\s*do)?$""") { m -> RBAction.PlayYouTube(m.group(0)!!.trim()) },
        compile("""(?i)^(?:play|sunao|bajao|chalao)\s+(.+?)\s+(?:songs?|gana|gaana|music|videos?)$""") { m -> RBAction.PlayYouTube(m.group(1)!!.trim() + " songs") },
        compile("""(?i)^(?:hindi|english|punjabi|tamil|telugu|bhojpuri|marathi|bengali)\s+(?:songs?|gana|gaana|music)\s*(?:play|sunao|bajao|chalao)?\s*(?:karo|kar\s*do)?$""") { m -> RBAction.PlayYouTube(m.group(0)!!.trim()) },
        compile("""(?i)^(?:play|open)\s+youtube$""") { _ -> RBAction.PlayYouTube("") },
        compile("""(?i)^youtube$""") { _ -> RBAction.PlayYouTube("") },

        // Calls
        compile("""(?i)^(?:call|phone|dial|ring|milaao|lagao)\s+(?:up\s+)?([a-zA-Z\u0900-\u097F][a-zA-Z0-9\u0900-\u097F\s]{0,40})$""") { m ->
            val who = m.group(1)!!.trim()
            if (who.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) RBAction.DialNumber(who)
            else RBAction.CallContact(who)
        },

        // SMS
        compile("""(?i)^(?:send\s+)?(?:sms|message|text|msg)\s+(?:to\s+)?([a-zA-Z\u0900-\u097F][a-zA-Z0-9\u0900-\u097F\s]{0,40}?)\s+(?:saying|that|about|with|:)\s+(.+)$""") { m -> RBAction.SendSms(m.group(1)!!.trim(), m.group(2)!!.trim()) },

        // Email
        compile("""(?i)^(?:send\s+)?email\s+to\s+([^\s]+@[^\s]+)\s+(?:about|subject)\s+(.+?)(?:\s+(?:saying|body)\s+(.+))?$""") { m -> RBAction.SendEmail(m.group(1)!!.trim(), m.group(2)!!.trim(), m.group(3)?.trim().orEmpty()) },
        compile("""(?i)^(?:send\s+)?email\s+to\s+([^\s]+@[^\s]+)$""") { m -> RBAction.SendEmail(m.group(1)!!.trim(), "", "") },

        // Flashlight
        compile("""(?i)^(?:turn\s+on|enable|open|chalu\s+karo)\s+(?:the\s+)?(?:flashlight|torch|flash)$""") { _ -> RBAction.SetFlashlight(true) },
        compile("""(?i)^(?:turn\s+off|disable|close|band\s+karo)\s+(?:the\s+)?(?:flashlight|torch|flash)$""") { _ -> RBAction.SetFlashlight(false) },
        compile("""(?i)^(?:flashlight|torch)$""") { _ -> RBAction.SetFlashlight(true) },

        // Settings panels
        compile("""(?i)^(?:open|toggle|enable|disable)\s+(?:the\s+)?(?:wifi|wi-fi|wlan)\s*(?:settings)?$""") { _ -> RBAction.OpenSettingsPanel("wifi") },
        compile("""(?i)^(?:open|toggle|enable|disable)\s+(?:the\s+)?bluetooth\s*(?:settings)?$""") { _ -> RBAction.OpenSettingsPanel("bluetooth") },

        // Alarm / Timer
        compile("""(?i)^set\s+(?:an?\s+)?alarm\s+(?:for\s+)?(\d{1,2})[:.]?(\d{2})?\s*(am|pm)?(?:\s+(?:called|for)\s+(.+))?$""") { m ->
            var h = m.group(1)!!.toInt()
            val mn = m.group(2)?.toIntOrNull() ?: 0
            val ap = m.group(3)?.lowercase(Locale.ROOT)
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            RBAction.SetAlarm(h, mn, m.group(4)?.trim().orEmpty())
        },
        compile("""(?i)^set\s+(?:a\s+)?timer\s+(?:for\s+)?(\d+)\s*(second|sec|s|minute|min|m|hour|hr|h)s?$""") { m ->
            val n = m.group(1)!!.toInt()
            val u = m.group(2)!!.lowercase(Locale.ROOT)
            RBAction.SetTimer(when (u) { "second","sec","s" -> n; "minute","min","m" -> n*60; else -> n*3600 })
        },

        // Camera
        compile("""(?i)^(?:open|launch|take\s+a?\s*picture|take\s+photo|capture)\s*(?:camera|photo|picture|selfie)?$""") { _ -> RBAction.OpenCamera },

        // Navigation
        compile("""(?i)^(?:navigate|directions?)\s+to\s+(.+)$""") { m -> RBAction.NavigateTo(m.group(1)!!.trim()) },
        compile("""(?i)^(?:take\s+me\s+to|go\s+to)\s+(.+)$""") { m -> RBAction.NavigateTo(m.group(1)!!.trim()) },

        // Web search
        compile("""(?i)^(?:google|search|search\s+for|look\s+up)\s+(.+)$""") { m -> RBAction.WebSearch(m.group(1)!!.trim()) },

        // URL
        compile("""(?i)^(?:open|visit)\s+(https?://\S+)$""") { m -> RBAction.OpenUrl(m.group(1)!!.trim()) },

        // Generic app open
        compile("""(?i)^(?:open|launch|start|run|show|kholo)\s+(?:the\s+)?(.+?)(?:\s+app)?$""") { m ->
            val name = m.group(1)!!.trim()
            if (name.contains("http") || name.contains(".com")) null else RBAction.OpenApp(name)
        }
    )

    fun parseText(input: String): RBAction? {
        val text = input.trim()
        if (text.isEmpty() || text.length > 200) return null

        val lower = text.lowercase(Locale.ROOT)
        if (lower.endsWith("?")) {
            val first = lower.split(" ").firstOrNull() ?: return null
            if (first !in setOf("play","open","launch","call","dial","search","stop","close","sunao","bajao","chalao")) return null
        }

        for ((pattern, builder) in PATTERNS) {
            val m = pattern.matcher(text)
            if (m.matches()) {
                val a = try { builder(m) } catch (e: Exception) {
                    Log.w(TAG, "pattern crash", e); null
                }
                if (a != null && a !is RBAction.NoOp) {
                    Log.i(TAG, "Matched ${a::class.simpleName} from: $text")
                    return a
                }
            }
        }
        return null
    }
}
