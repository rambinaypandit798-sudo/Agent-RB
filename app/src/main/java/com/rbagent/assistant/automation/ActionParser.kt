package com.rbagent.assistant.automation

import android.util.Log
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

/**
 * ActionParser — Regex + JSON hybrid parser.
 * Pehle local deterministic match, phir Gemini functionCall fallback.
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
        compile("""(?i)^(?:stop|close|hide|dismiss|exit|end)\s+(?:the\s+)?(?:assistant|agent|rb|overlay|bubble)$""") { _ -> RBAction.CloseAssistant },
        compile("""(?i)^(?:goodbye|bye)\s+(?:rb|agent|assistant)$""") { _ -> RBAction.CloseAssistant },

        // YouTube
        compile("""(?i)^(?:open\s+|launch\s+)?youtube\s+(?:and\s+)?(?:play|search|find)\s+(?:song\s+|video\s+)?(.+)$""") { m -> RBAction.PlayYouTube(m.group(1)!!.trim()) },
        compile("""(?i)^(?:play|search|find)\s+(?:song\s+|video\s+)?(.+?)\s+(?:on|in)\s+youtube$""") { m -> RBAction.PlayYouTube(m.group(1)!!.trim()) },
        compile("""(?i)^(?:play)\s+(?:some\s+)?(.+?)\s+(?:songs?|music|videos?)$""") { m -> RBAction.PlayYouTube(m.group(1)!!.trim() + " songs") },
        compile("""(?i)^(?:play|open)\s+youtube$""") { _ -> RBAction.PlayYouTube("") },
        compile("""(?i)^youtube$""") { _ -> RBAction.PlayYouTube("") },

        // Calls
        compile("""(?i)^(?:call|phone|dial|ring)\s+(?:up\s+)?([a-zA-Z\u0900-\u097F][a-zA-Z0-9\u0900-\u097F\s]{0,40})$""") { m ->
            val who = m.group(1)!!.trim()
            if (who.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) RBAction.DialNumber(who)
            else RBAction.CallContact(who)
        },

        // SMS
        compile("""(?i)^(?:send\s+)?(?:sms|message|text|msg)\s+(?:to\s+)?([a-zA-Z\u0900-\u097F][a-zA-Z0-9\u0900-\u097F\s]{0,40}?)\s+(?:saying|that|about|with|:)\s+(.+)$""") { m ->
            RBAction.SendSms(m.group(1)!!.trim(), m.group(2)!!.trim())
        },

        // Email
        compile("""(?i)^(?:send\s+)?email\s+to\s+([^\s]+@[^\s]+)\s+(?:about|subject)\s+(.+?)(?:\s+(?:saying|body)\s+(.+))?$""") { m ->
            RBAction.SendEmail(m.group(1)!!.trim(), m.group(2)!!.trim(), m.group(3)?.trim().orEmpty())
        },

        // Flashlight
        compile("""(?i)^(?:turn\s+on|enable|open)\s+(?:the\s+)?(?:flashlight|torch|flash)$""") { _ -> RBAction.SetFlashlight(true) },
        compile("""(?i)^(?:turn\s+off|disable|close)\s+(?:the\s+)?(?:flashlight|torch|flash)$""") { _ -> RBAction.SetFlashlight(false) },
        compile("""(?i)^(?:flashlight|torch)$""") { _ -> RBAction.SetFlashlight(true) },

        // WiFi/Bluetooth
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
        compile("""(?i)^(?:open|launch|start|run|show)\s+(?:the\s+)?(.+?)(?:\s+app)?$""") { m ->
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
            if (first !in setOf("play","open","launch","call","dial","search","stop","close")) return null
        }
        for ((pattern, builder) in PATTERNS) {
            val m = pattern.matcher(text)
            if (m.matches()) {
                val a = try { builder(m) } catch (e: Exception) { Log.w(TAG, "crash", e); null }
                if (a != null && a !is RBAction.NoOp) return a
            }
        }
        return null
    }

    fun parseFunction(name: String, args: JSONObject): RBAction? = try {
        when (name) {
            "open_app"        -> RBAction.OpenApp(args.optString("app_name").ifBlank { return null }, args.optString("query").takeIf { it.isNotBlank() })
            "play_youtube"    -> RBAction.PlayYouTube(args.optString("query"))
            "call_contact"    -> RBAction.CallContact(args.optString("name").ifBlank { return null })
            "dial_number"     -> RBAction.DialNumber(args.optString("number").ifBlank { return null })
            "send_sms"        -> RBAction.SendSms(args.optString("recipient").ifBlank { return null }, args.optString("message"))
            "send_email"      -> RBAction.SendEmail(args.optString("to").ifBlank { return null }, args.optString("subject"), args.optString("body"))
            "web_search"      -> RBAction.WebSearch(args.optString("query").ifBlank { return null })
            "open_url"        -> RBAction.OpenUrl(args.optString("url").ifBlank { return null })
            "flashlight_on"   -> RBAction.SetFlashlight(true)
            "flashlight_off"  -> RBAction.SetFlashlight(false)
            "open_settings"   -> RBAction.OpenSettingsPanel(args.optString("panel", "settings"))
            "set_alarm"       -> RBAction.SetAlarm(args.optInt("hour", 7), args.optInt("minute", 0), args.optString("label"))
            "set_timer"       -> RBAction.SetTimer(args.optInt("seconds", 60))
            "open_camera"     -> RBAction.OpenCamera
            "navigate_to"     -> RBAction.NavigateTo(args.optString("place").ifBlank { return null })
            "close_assistant" -> RBAction.CloseAssistant
            else              -> null
        }
    } catch (e: Exception) { Log.e(TAG, "parseFunction fail", e); null }

    fun functionDeclarationsJson(): String = """
    [
      {"name":"open_app","description":"Launch installed app","parameters":{"type":"OBJECT","properties":{"app_name":{"type":"STRING"},"query":{"type":"STRING"}},"required":["app_name"]}},
      {"name":"play_youtube","description":"Play on YouTube","parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING"}},"required":["query"]}},
      {"name":"call_contact","description":"Call contact","parameters":{"type":"OBJECT","properties":{"name":{"type":"STRING"}},"required":["name"]}},
      {"name":"send_sms","description":"Send SMS","parameters":{"type":"OBJECT","properties":{"recipient":{"type":"STRING"},"message":{"type":"STRING"}},"required":["recipient"]}},
      {"name":"send_email","description":"Compose email","parameters":{"type":"OBJECT","properties":{"to":{"type":"STRING"},"subject":{"type":"STRING"},"body":{"type":"STRING"}},"required":["to"]}},
      {"name":"web_search","description":"Google search","parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING"}},"required":["query"]}},
      {"name":"open_url","description":"Open URL","parameters":{"type":"OBJECT","properties":{"url":{"type":"STRING"}},"required":["url"]}},
      {"name":"flashlight_on","description":"Flashlight on","parameters":{"type":"OBJECT","properties":{}}},
      {"name":"flashlight_off","description":"Flashlight off","parameters":{"type":"OBJECT","properties":{}}},
      {"name":"set_alarm","description":"Set alarm","parameters":{"type":"OBJECT","properties":{"hour":{"type":"INTEGER"},"minute":{"type":"INTEGER"}},"required":["hour","minute"]}},
      {"name":"navigate_to","description":"Google Maps navigation","parameters":{"type":"OBJECT","properties":{"place":{"type":"STRING"}},"required":["place"]}},
      {"name":"close_assistant","description":"Close floating overlay","parameters":{"type":"OBJECT","properties":{}}}
    ]
    """.trimIndent()
}
