package com.echoshield.calling

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoPulseRadar
import com.echoshield.data.preview.PreviewData
import com.echoshield.theme.CyberShieldGradient
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import kotlinx.coroutines.delay

@Composable
fun OutgoingCallScreen(
    contactId: String,
    onCallConnected: () -> Unit,
    onCancelCall: () -> Unit
) {
    val member = PreviewData.familyMembers.find { it.id == contactId } ?: PreviewData.familyMembers.first()
    var connectionStateText by remember { mutableStateOf("Securing WebRTC channel...") }

    // Simulate connection flow to active call
    LaunchedEffect(Unit) {
        delay(1500)
        connectionStateText = "Ringing family endpoint..."
        delay(2000)
        connectionStateText = "Establishing encrypted audio session..."
        delay(1000)
        onCallConnected()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberShieldGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Security Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PrimaryTeal.copy(alpha = 0.15f))
                    .border(1.dp, PrimaryTeal.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OUTGOING ENCRYPTED CALL",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = PrimaryTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Pulse radar & Avatar
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                EchoPulseRadar(size = 180.dp, color = PrimaryTeal)
                EchoAvatar(
                    name = member.name,
                    size = 110.dp,
                    isVoiceEnrolled = member.isVoiceEnrolled
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = member.name,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = member.relationship,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = connectionStateText,
                style = MaterialTheme.typography.titleSmall,
                color = PrimaryTeal
            )

            Spacer(modifier = Modifier.weight(1f))

            // Cancel Call Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCancelCall,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(DangerRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Cancel Call",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "End Call",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = DangerRed
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
