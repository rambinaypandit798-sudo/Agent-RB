package com.rbagent.assistant.automation

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.URLEncoder

/**
 * ActionDispatcher — Native Android intents execute karta hai.
 * Har external app launch se PEHLE floating overlay arm hota hai.
 */
object ActionDispatcher {
    private const val TAG = "ActionDispatcher"

    fun execute(context: Context, action: RBAction): RBAction.ExecutionResult {
        return try {
            when (action) {
                is RBAction.OpenApp           -> openApp(context, action)
                is RBAction.PlayYouTube       -> playYouTube(context, action.query)
                is RBAction.CallContact       -> callContact(context, action.contactName)
                is RBAction.DialNumber        -> dialNumber(context, action.number)
                is RBAction.SendSms           -> sendSms(context, action)
                is RBAction.SendEmail         -> sendEmail(context, action)
                is RBAction.WebSearch         -> webSearch(context, action.query)
                is RBAction.OpenUrl           -> openUrl(context, action.url)
                is RBAction.SetFlashlight     -> setFlashlight(context, action.on)
                is RBAction.OpenSettingsPanel -> openSettingsPanel(context, action.panel)
                is RBAction.SetAlarm          -> setAlarm(context, action)
                is RBAction.SetTimer          -> setTimer(context, action)
                RBAction.OpenCamera           -> openCamera(context)
                is RBAction.NavigateTo        -> navigateTo(context, action.place)
                RBAction.CloseAssistant       -> closeAssistant(context)
                RBAction.NoOp                 -> fail("No action")
            }
        } catch (e: ActivityNotFoundException) {
            fail("Koi app is action ko handle nahi kar sakta.")
        } catch (e: SecurityException) {
            fail("Permission nahi mili — Settings → Permissions mein grant karo.")
        } catch (e: Exception) {
            Log.e(TAG, "dispatch fail", e); fail("Fail: ${e.message ?: "unknown"}")
        }
    }

    /** Bubble auto-show jab tak overlay permission granted hai. */
    private fun armOverlay(context: Context, hint: String) {
        if (!OverlayPermissionHelper.canDrawOverlays(context)) return
        try { AssistantOverlayService.show(context, hint) }
        catch (e: Exception) { Log.e(TAG, "overlay start fail", e) }
    }

    private fun openApp(context: Context, action: RBAction.OpenApp): RBAction.ExecutionResult {
        val lower = action.appName.lowercase().trim()
        if (lower == "youtube" || lower == "yt") return playYouTube(context, action.query ?: "")

        val pkg = ActionParser.APP_PACKAGES[lower]
            ?: ActionParser.APP_PACKAGES.entries
                .firstOrNull { lower.contains(it.key) || it.key.contains(lower) }?.value

        if (pkg == null) {
            val launch = context.packageManager.getLaunchIntentForPackage(action.appName)
                ?: return fail("\"${action.appName}\" app nahi mili.")
            armOverlay(context, "Opening ${action.appName}")
            launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launch)
            return ok("${action.appName} khol diya.", "launch:${action.appName}")
        }

        val intent = if (!action.query.isNullOrBlank()) {
            Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg); putExtra(SearchManager.QUERY, action.query)
            }
        } else context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return fail("${action.appName} install nahi hai.")

        armOverlay(context, "Opening ${action.appName}")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return ok("${action.appName} khol diya.", "launch:$pkg")
    }

    private fun playYouTube(context: Context, query: String): RBAction.ExecutionResult {
        val ytPkg = "com.google.android.youtube"
        armOverlay(context, if (query.isBlank()) "Opening YouTube" else "Playing: $query")

        if (isInstalled(context, ytPkg)) {
            val intent = if (query.isBlank())
                context.packageManager.getLaunchIntentForPackage(ytPkg)
            else Intent(Intent.ACTION_SEARCH).apply {
                setPackage(ytPkg); putExtra("query", query)
            }
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    context.startActivity(intent)
                    val msg = if (query.isBlank()) "YouTube khol raha hoon."
                              else "\"$query\" YouTube pe chala raha hoon."
                    return ok(msg, "youtube:$query")
                } catch (_: Exception) {}
            }
        }
        if (query.isNotBlank()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("vnd.youtube:${Uri.encode(query)}"))
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return ok("\"$query\" YouTube pe chala raha hoon.", "youtube-deep:$query")
            } catch (_: Exception) {}
        }
        val url = if (query.isBlank()) "https://m.youtube.com"
                  else "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return ok("YouTube browser mein khol raha hoon.", "youtube-web:$query")
    }

    private fun callContact(context: Context, name: String): RBAction.ExecutionResult {
        if (!hasPerm(context, Manifest.permission.READ_CONTACTS))
            return fail("\"$name\" dhundhne ke liye Contacts permission chahiye.")
        val number = lookupContactNumber(context, name)
            ?: return fail("\"$name\" contacts mein nahi mila.")
        return placeCall(context, number, name)
    }

    private fun dialNumber(context: Context, number: String) =
        placeCall(context, number, number)

    private fun placeCall(context: Context, number: String, label: String): RBAction.ExecutionResult {
        val clean = number.replace(Regex("[^0-9+]"), "")
        if (clean.isBlank()) return fail("Invalid phone number.")
        val canCall = hasPerm(context, Manifest.permission.CALL_PHONE)
        val intent = if (canCall) Intent(Intent.ACTION_CALL, Uri.parse("tel:$clean"))
                     else Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        return try {
            armOverlay(context, "Calling $label")
            context.startActivity(intent)
            ok((if (canCall) "Call kar raha hoon " else "Dialer khol raha hoon ") + "$label.", "call:$clean")
        } catch (e: Exception) { fail("Call nahi lag payi: ${e.message}") }
    }

    private fun lookupContactNumber(context: Context, name: String): String? = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
        var c: Cursor? = null
        try {
            c = context.contentResolver.query(uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            if (c != null && c.moveToFirst()) {
                val i = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (i >= 0) c.getString(i) else null
            } else null
        } finally { c?.close() }
    } catch (_: Exception) { null }

    private fun sendSms(context: Context, a: RBAction.SendSms): RBAction.ExecutionResult {
        val target = a.recipient.trim()
        val number = if (target.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) target
        else {
            if (!hasPerm(context, Manifest.permission.READ_CONTACTS))
                return fail("Contacts permission chahiye \"$target\" ke liye.")
            lookupContactNumber(context, target) ?: return fail("\"$target\" contacts mein nahi mila.")
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (a.message.isNotBlank()) putExtra("sms_body", a.message)
        }
        return try {
            armOverlay(context, "Messaging $target")
            context.startActivity(intent)
            ok("SMS $target ko khol raha hoon.", "sms:$number")
        } catch (_: Exception) { fail("SMS app nahi mili.") }
    }

    private fun sendEmail(context: Context, a: RBAction.SendEmail): RBAction.ExecutionResult {
        val mailTo = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${a.to}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (a.subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, a.subject)
            if (a.body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, a.body)
        }
        return try {
            armOverlay(context, "Emailing ${a.to}")
            context.startActivity(mailTo)
            ok("Email draft kar raha hoon ${a.to} ko.", "email:${a.to}")
        } catch (_: Exception) { fail("Email app nahi mili.") }
    }

    private fun webSearch(context: Context, q: String): RBAction.ExecutionResult {
        val i = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8")))
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        return try {
            armOverlay(context, "Searching: $q")
            context.startActivity(i); ok("\"$q\" search kar raha hoon.", "search:$q")
        } catch (_: Exception) { fail("Browser nahi mila.") }
    }

    private fun openUrl(context: Context, url: String): RBAction.ExecutionResult {
        val fixed = if (url.startsWith("http")) url else "https://$url"
        return try {
            armOverlay(context, "Opening $url")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixed))
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            ok("$url khol raha hoon.", "url:$fixed")
        } catch (_: Exception) { fail("URL open nahi hua.") }
    }

    private fun setFlashlight(context: Context, on: Boolean): RBAction.ExecutionResult = try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cid ->
            cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return fail("Flashlight nahi hai is device mein.")
        cm.setTorchMode(id, on)
        ok(if (on) "Flashlight on." else "Flashlight off.", "flashlight:$on")
    } catch (e: Exception) { fail("Flashlight control fail: ${e.message}") }

    private fun openSettingsPanel(context: Context, panel: String): RBAction.ExecutionResult {
        val p = panel.lowercase()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val a = when (p) {
                "wifi", "wi-fi", "wlan" -> Settings.Panel.ACTION_WIFI
                "bluetooth", "bt"       -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                "volume", "sound"       -> Settings.Panel.ACTION_VOLUME
                else -> null
            }
            if (a != null) try {
                context.startActivity(Intent(a).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return ok("$p settings khol raha hoon.", "settings:$p")
            } catch (_: Exception) {}
        }
        val fb = when (p) {
            "wifi", "wi-fi", "wlan" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bt"       -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location", "gps"       -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "sound", "volume"       -> Settings.ACTION_SOUND_SETTINGS
            "display"               -> Settings.ACTION_DISPLAY_SETTINGS
            "battery"               -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps"                  -> Settings.ACTION_APPLICATION_SETTINGS
            else                    -> Settings.ACTION_SETTINGS
        }
        return try {
            armOverlay(context, "Opening $p settings")
            context.startActivity(Intent(fb).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            ok("$p settings khol raha hoon.", "settings:$p")
        } catch (_: Exception) { fail("Settings nahi khuli.") }
    }

    private fun setAlarm(context: Context, a: RBAction.SetAlarm) = try {
        context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(AlarmClock.EXTRA_HOUR, a.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (a.label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, a.label)
        })
        ok("Alarm set %02d:%02d.".format(a.hour, a.minute), "alarm:${a.hour}:${a.minute}")
    } catch (_: Exception) { fail("Alarm set nahi hua.") }

    private fun setTimer(context: Context, a: RBAction.SetTimer) = try {
        context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(AlarmClock.EXTRA_LENGTH, a.seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        })
        ok("Timer ${a.seconds}s ke liye set.", "timer:${a.seconds}")
    } catch (_: Exception) { fail("Timer start nahi hua.") }

    private fun openCamera(context: Context): RBAction.ExecutionResult {
        for (i in listOf(Intent("android.media.action.STILL_IMAGE_CAMERA"),
                         Intent("android.media.action.IMAGE_CAPTURE"))) {
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (context.packageManager.resolveActivity(i, 0) != null) {
                try { armOverlay(context, "Opening camera"); context.startActivity(i)
                    return ok("Camera khol raha hoon.", "camera") } catch (_: Exception) {}
            }
        }
        return fail("Camera app nahi mili.")
    }

    private fun navigateTo(context: Context, place: String): RBAction.ExecutionResult {
        val enc = Uri.encode(place)
        return try {
            armOverlay(context, "Navigating to $place")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$enc"))
                .apply { setPackage("com.google.android.apps.maps")
                         flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            ok("$place navigate kar raha hoon.", "nav:$place")
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$enc"))
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                ok("Directions $place ke liye.", "nav-web:$place")
            } catch (_: Exception) { fail("Navigation nahi khuli.") }
        }
    }

    private fun closeAssistant(context: Context): RBAction.ExecutionResult = try {
        AssistantOverlayService.hide(context)
        ok("Assistant band kar diya.", "close:assistant")
    } catch (e: Exception) { fail("Close fail: ${e.message}") }

    private fun hasPerm(context: Context, p: String): Boolean =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun isInstalled(context: Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun ok(m: String, l: String) = RBAction.ExecutionResult(true, m, l)
    private fun fail(m: String) = RBAction.ExecutionResult(false, m, "none")
}
