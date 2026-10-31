package com.echoshield.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.theme.DangerRed
import com.echoshield.theme.InconclusiveGray
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

enum class EchoVerdict(
    val title: String,
    val subtitle: String,
    val color: Color,
    val icon: ImageVector
) {
    VOICE_MATCHED(
        "Voice Matched",
        "Acoustic profile matches enrolled family biometric baseline",
        SuccessGreen,
        Icons.Default.CheckCircle
    ),
    IDENTITY_MISMATCH(
        "Identity Not Matched",
        "Natural speech detected, but does not match registered profile",
        WarningAmber,
        Icons.Default.Warning
    ),
    SUSPICIOUS_SPOOF(
        "Suspicious Audio Detected",
        "Acoustic anomaly detected. Possible AI voice cloning or synthetic replay",
        DangerRed,
        Icons.Default.Error
    ),
    INCONCLUSIVE(
        "Inconclusive Assessment",
        "Insufficient speech duration or elevated ambient noise background",
        InconclusiveGray,
        Icons.Default.Help
    )
}

@Composable
fun EchoVerificationBadge(
    verdict: EchoVerdict,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(verdict.color.copy(alpha = 0.14f))
            .border(1.dp, verdict.color.copy(alpha = 0.5f), shape)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = verdict.icon,
                contentDescription = verdict.title,
                tint = verdict.color,
                modifier = Modifier.size(if (compact) 14.dp else 18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = verdict.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 11.sp else 12.sp
                ),
                color = verdict.color
            )
        }
    }
}
