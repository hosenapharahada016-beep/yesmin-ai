package com.yesmin.ai.automation

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings

/**
 * Result of an automation attempt. Android only tells us whether an intent could be
 * *started*, not whether the user finished the task, so wording is careful:
 * Opened = the target screen was launched; NeedsUser = the user must confirm in that screen;
 * Failed = Android could not launch it.
 */
sealed class ActionResult(val message: String) {
    class Opened(message: String) : ActionResult(message)
    class NeedsUser(message: String) : ActionResult(message)
    class Failed(message: String) : ActionResult(message)
}

class Actions(private val context: Context) {

    private fun launch(intent: Intent, ok: String, needsUser: Boolean = false): ActionResult =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (needsUser) ActionResult.NeedsUser(ok) else ActionResult.Opened(ok)
        } catch (e: ActivityNotFoundException) {
            ActionResult.Failed("No app on this phone can handle that.")
        } catch (e: SecurityException) {
            ActionResult.Failed("Android blocked this action (missing permission).")
        } catch (e: Exception) {
            ActionResult.Failed("Could not do that: ${e.javaClass.simpleName}")
        }

    /** Finds an installed launchable app whose label contains [name] (case-insensitive). */
    fun openApp(name: String): ActionResult {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val wanted = name.trim().lowercase()
        val match = pm.queryIntentActivities(main, 0)
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.length }
            .firstOrNull { it.second.lowercase().contains(wanted) }
            ?: return ActionResult.Failed("I couldn't find an app called \"$name\".")
        val intent = pm.getLaunchIntentForPackage(match.first.activityInfo.packageName)
            ?: return ActionResult.Failed("${match.second} can't be launched.")
        return launch(intent, "Opened ${match.second}.")
    }

    /** Opens YouTube search results. Autoplay is not possible without the user tapping a video. */
    fun youtubeSearch(query: String): ActionResult {
        val app = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
            .putExtra("query", query)
        val pm = context.packageManager
        return if (app.resolveActivity(pm) != null) {
            launch(app, "Opened YouTube results for \"$query\". Tap a video to play.", needsUser = true)
        } else {
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
            )
            launch(web, "Opened YouTube results for \"$query\" in your browser. Tap a video to play.", needsUser = true)
        }
    }

    fun webSearch(query: String): ActionResult =
        launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query), "Opened a web search for \"$query\".")

    fun openUrl(url: String): ActionResult {
        val u = if (url.startsWith("http")) url else "https://$url"
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Opened $u.")
    }

    fun openSettings(): ActionResult = launch(Intent(Settings.ACTION_SETTINGS), "Opened Settings.")
    fun openWifiSettings(): ActionResult = launch(Intent(Settings.ACTION_WIFI_SETTINGS), "Opened Wi-Fi settings.")
    fun openBluetoothSettings(): ActionResult = launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Opened Bluetooth settings.")

    fun openCamera(): ActionResult =
        launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Opened the camera.")

    fun openMaps(query: String): ActionResult =
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))), "Opened maps for \"$query\".")

    /** Opens the dialer with the number filled in. The user must press call. */
    fun dial(number: String): ActionResult =
        launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))), "Dialer is ready for $number. Press call to place it.", needsUser = true)

    /** Opens the SMS app with text prefilled. The user must press send. */
    fun composeSms(number: String, text: String): ActionResult =
        launch(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", text),
            "Message drafted. Press send in your messages app.",
            needsUser = true
        )

    /** Opens the clock app's alarm screen prefilled. The system may ask the user to confirm. */
    fun setAlarm(hour: Int, minute: Int, label: String): ActionResult =
        launch(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
            "Alarm for %02d:%02d sent to your clock app. Check it to confirm it's saved.".format(hour, minute),
            needsUser = true
        )

    fun setTimer(seconds: Int, label: String): ActionResult =
        launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
            "Timer for ${seconds}s sent to your clock app. Check it started.",
            needsUser = true
        )

    /** Opens the calendar's "new event" screen prefilled; the user saves it. */
    fun addCalendarEvent(title: String, startMillis: Long, endMillis: Long): ActionResult =
        launch(
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis),
            "Event draft opened in your calendar. Press save to keep it.",
            needsUser = true
        )

    fun openAccessibilitySettings(): ActionResult =
        launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Opened Accessibility settings.")

    fun openNotificationAccessSettings(): ActionResult =
        launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Opened Notification access settings.")
}
