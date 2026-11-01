package com.echoshield.calling.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.echoshield.MainActivity
import com.echoshield.core.di.AppContainer

/**
 * BroadcastReceiver handling background notification action button clicks
 * (Decline / Accept) for incoming WebRTC voice calls.
 */
class CallActionReceiver : BroadcastReceiver() {

    private val tag = "CallActionReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID)
        Log.i(tag, "Received call action: $action for call: $callId")

        when (action) {
            CallNotificationManager.ACTION_DECLINE_CALL -> {
                CallNotificationManager.dismissIncomingCallNotification(context)
                AppContainer.callSessionCoordinator.rejectIncomingCall()
            }

            CallNotificationManager.ACTION_ACCEPT_CALL -> {
                CallNotificationManager.dismissIncomingCallNotification(context)
                AppContainer.callSessionCoordinator.acceptIncomingCall(context)

                // Launch main activity into active call screen
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("EXTRA_NAVIGATE_TO", "active_call")
                    putExtra("EXTRA_CALL_ID", callId)
                }
                context.startActivity(mainIntent)
            }
        }
    }
}

