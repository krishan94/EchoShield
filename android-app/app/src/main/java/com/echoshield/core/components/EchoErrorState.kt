package com.echoshield.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiConnectedNoInternet4
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoshield.theme.DangerRed
import com.echoshield.theme.WarningAmber

enum class EchoErrorType(
    val defaultTitle: String,
    val defaultMessage: String,
    val icon: ImageVector
) {
    NETWORK(
        "Network Disconnected",
        "Unable to reach EchoShield security servers. Please verify your Wi-Fi or cellular data connection.",
        Icons.Default.SignalWifiConnectedNoInternet4
    ),
    BACKEND_UNAVAILABLE(
        "Security Engine Unavailable",
        "The voice verification backend service is currently unreachable or undergoing maintenance.",
        Icons.Default.CloudOff
    ),
    PERMISSION_DENIED(
        "Microphone Access Required",
        "EchoShield requires microphone permission to perform live in-call acoustic verification and anti-spoof analysis.",
        Icons.Default.Lock
    ),
    GENERIC(
        "Unexpected Anomaly",
        "An unexpected error occurred while executing the operation. Please try again.",
        Icons.Default.Warning
    )
}

@Composable
fun EchoErrorState(
    errorType: EchoErrorType = EchoErrorType.GENERIC,
    customTitle: String? = null,
    customMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val title = customTitle ?: errorType.defaultTitle
    val message = customMessage ?: errorType.defaultMessage
    val iconColor = if (errorType == EchoErrorType.NETWORK || errorType == EchoErrorType.GENERIC) DangerRed else WarningAmber

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = errorType.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (onRetry != null) {
            EchoPrimaryButton(
                text = "Retry Connection",
                onClick = onRetry,
                leadingIcon = Icons.Default.Refresh,
                height = 46.dp
            )
        } else if (onOpenSettings != null) {
            EchoPrimaryButton(
                text = "Open App Settings",
                onClick = onOpenSettings,
                height = 46.dp
            )
        }
    }
}

