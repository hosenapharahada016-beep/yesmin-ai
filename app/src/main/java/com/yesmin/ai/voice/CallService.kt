package com.yesmin.ai.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yesmin.ai.MainActivity

/**
 * Foreground service that keeps the microphone call alive while the app is in the background.
 * Android requires this (with a visible notification) for background microphone use.
 * It does no audio work itself; the call logic lives in LiveSession.
 */
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Yesmin call", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Yesmin call in progress")
            .setContentText("Microphone is on. Tap to return.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "yesmin_call"
        private const val NOTIF_ID = 4201
        private const val ACTION_STOP = "com.yesmin.ai.STOP_CALL"

        /** Call only after RECORD_AUDIO has been granted. */
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, CallService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, CallService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
