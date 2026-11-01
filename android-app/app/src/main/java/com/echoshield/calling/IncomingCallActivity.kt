package com.echoshield.calling

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.MainActivity
import com.echoshield.calling.notification.CallNotificationManager
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoPulseRadar
import com.echoshield.core.di.AppContainer
import com.echoshield.core.theme.AppThemeMode
import com.echoshield.domain.model.CallStatus
import com.echoshield.theme.CyberShieldGradient
import com.echoshield.theme.DangerRed
import com.echoshield.theme.EchoShieldTheme
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen

/**
 * Dedicated Full-Screen Activity for incoming WebRTC voice calls.
 * Displays over keyguard / lock-screen and turns screen on when called.
 * Styled in EchoShield Midnight Teal aesthetic with WhatsApp-style large actions.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenAndScreenOn()

        val callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID) ?: ""
        val callerId = intent.getStringExtra(CallNotificationManager.EXTRA_CALLER_ID) ?: ""
        val callerName = intent.getStringExtra(CallNotificationManager.EXTRA_CALLER_NAME) ?: "Family Member"
        val themePrefs = AppContainer.getThemePreferences(applicationContext)

        setContent {
            val themeMode by themePrefs.themeMode.collectAsState(initial = AppThemeMode.DARK)
            EchoShieldTheme(themeMode = themeMode) {
                val callState by AppContainer.callSessionCoordinator.uiState.collectAsState()

                // Automatically finish if caller hangs up or call state is no longer RINGING
                LaunchedEffect(callState.callStatus) {
                    if (callState.callStatus != CallStatus.RINGING && callState.callStatus != CallStatus.IDLE) {
                        finish()
                    }
                }

                FullScreenIncomingCallContent(
                    callerName = callState.remoteMemberName ?: callerName,
                    onAccept = {
                        CallNotificationManager.dismissIncomingCallNotification(this)
                        AppContainer.callSessionCoordinator.acceptIncomingCall(this)

                        // Navigate to Active Call Screen in MainActivity
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("EXTRA_NAVIGATE_TO", "active_call")
                            putExtra("EXTRA_CALL_ID", callId)
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onDecline = {
                        CallNotificationManager.dismissIncomingCallNotification(this)
                        AppContainer.callSessionCoordinator.rejectIncomingCall()
                        finish()
                    }
                )
            }
        }
    }

    private fun configureLockScreenAndScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
fun FullScreenIncomingCallContent(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Sub-header with security badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PrimaryTeal.copy(alpha = 0.15f))
                    .border(1.dp, PrimaryTeal.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Encrypted Family Calling",
                        fontSize = 12.sp,
                        color = PrimaryTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Radar Pulse around Avatar (WhatsApp / EchoShield style)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                EchoPulseRadar(
                    color = SuccessGreen,
                    size = 220.dp
                )

                EchoAvatar(
                    name = callerName,
                    size = 110.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = callerName,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "EchoShield Secure Voice Call",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Defense Status Indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Real-Time AI Verification Ready",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Accept / Decline Call Controls (Large WhatsApp-style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline Call Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(DangerRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline Call",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Decline",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Accept Call Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Accept Call",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Accept",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

