package com.echoshield.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoshield.core.components.EchoGhostButton
import com.echoshield.core.components.EchoOutlinedButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.WarningAmber

@Composable
fun MicrophonePermissionScreen(
    onPermissionGranted: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var isDeniedPermanently by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Hardware Permission",
            subtitle = "Microphone Access Required",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDeniedPermanently) DangerRed.copy(alpha = 0.15f) else PrimaryTeal.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDeniedPermanently) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isDeniedPermanently) DangerRed else PrimaryTeal,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isDeniedPermanently) "Microphone Access Blocked" else "Grant Microphone Access",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isDeniedPermanently) {
                    "Microphone permission has been permanently denied in Android system settings. Please grant microphone access to capture voice enrollment samples and inspect live call acoustics."
                } else {
                    "EchoShield requires access to your microphone to capture high-fidelity 16kHz audio during voice enrollment and in-app call anti-spoofing verification. No background recording takes place."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (!isDeniedPermanently) {
                EchoPrimaryButton(
                    text = "Grant Microphone Permission",
                    onClick = onPermissionGranted,
                    leadingIcon = Icons.Default.Security
                )

                Spacer(modifier = Modifier.height(14.dp))

                EchoOutlinedButton(
                    text = "Simulate Permission Denied",
                    onClick = { isDeniedPermanently = true },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                EchoPrimaryButton(
                    text = "Open System Settings",
                    onClick = onPermissionGranted
                )

                Spacer(modifier = Modifier.height(14.dp))

                EchoOutlinedButton(
                    text = "Reset Permission State",
                    onClick = { isDeniedPermanently = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            EchoGhostButton(
                text = "Cancel & Return",
                onClick = onNavigateBack,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

