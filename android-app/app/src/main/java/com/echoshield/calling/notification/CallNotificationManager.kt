package com.echoshield.calling.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.echoshield.calling.IncomingCallActivity

/**
 * Manages native Android incoming call notifications, system ringtone, and vibration.
 * Adheres strictly to Android 13/14 requirements for fullScreenIntent and CallStyle notifications.
 */
object CallNotificationManager {

    private const val TAG = "CallNotificationMgr"

    const val CHANNEL_ID = "echoshield_incoming_calls"
    const val NOTIFICATION_ID = 4001

    const val ACTION_ACCEPT_CALL = "com.echoshield.calling.ACTION_ACCEPT_CALL"
    const val ACTION_DECLINE_CALL = "com.echoshield.calling.ACTION_DECLINE_CALL"
    const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
    const val EXTRA_CALLER_ID = "EXTRA_CALLER_ID"
    const val EXTRA_CALLER_NAME = "EXTRA_CALLER_NAME"

    @Volatile
    private var activeCallId: String? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    fun showIncomingCallNotification(
        context: Context,
        callId: String,
        callerId: String,
        callerName: String
    ) {
        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Prevent duplicate alert for the same active incoming call
        if (activeCallId == callId) {
            Log.d(TAG, "Notification for callId $callId is already active.")
            return
        }
        activeCallId = callId

        createNotificationChannel(appContext, notificationManager)
        startRingtoneAndVibration(appContext)

        // Decline Intent
        val declineIntent = Intent(appContext, CallActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            appContext,
            101,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Intent
        val acceptIntent = Intent(appContext, CallActionReceiver::class.java).apply {
            action = ACTION_ACCEPT_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            appContext,
            102,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full Screen Activity Intent
        val fullScreenIntent = Intent(appContext, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            appContext,
            103,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declinePendingIntent,
            acceptPendingIntent
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(callerName)
            .setContentText("Incoming EchoShield Encrypted Call")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setStyle(callStyle)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                declinePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_call,
                "Accept",
                acceptPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.i(TAG, "Incoming call notification posted for $callerName ($callId)")
    }

    fun dismissIncomingCallNotification(context: Context) {
        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
        stopRingtoneAndVibration()
        activeCallId = null
        Log.i(TAG, "Incoming call notification and ringer dismissed.")
    }

    private fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority full-screen incoming voice call alerts"
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startRingtoneAndVibration(context: Context) {
        try {
            stopRingtoneAndVibration()

            // Play system call ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, ringtoneUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                play()
            }

            // Start hardware vibration
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator = vib
            val pattern = longArrayOf(0, 800, 800, 800, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error starting ringer/vibrator: ${e.message}")
        }
    }

    private fun stopRingtoneAndVibration() {
        try {
            ringtone?.stop()
            ringtone = null
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringer/vibrator: ${e.message}")
        }
    }
}

