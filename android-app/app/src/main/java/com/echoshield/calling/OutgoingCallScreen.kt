package com.echoshield.calling

import android.content.Context
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoPulseRadar
import com.echoshield.domain.model.CallStatus
import com.echoshield.family.FamilyViewModel
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal

@Composable
fun OutgoingCallScreen(
    contactId: String,
    onCallConnected: () -> Unit,
    onCancelCall: () -> Unit,
    callingViewModel: CallingViewModel? = null,
    familyViewModel: FamilyViewModel? = null
) {
    val context = LocalContext.current
    val familyUiState = familyViewModel?.uiState?.collectAsState()
    val member = familyUiState?.value?.members?.find { it.id == contactId }
    val memberName = member?.name ?: "Family Contact"
    val relationship = member?.relationship ?: "Family Member"

    val callingUiState = callingViewModel?.uiState?.collectAsState()
    val statusText = callingUiState?.value?.statusMessage ?: "Securing WebRTC channel..."

    LaunchedEffect(contactId) {
        callingViewModel?.startOutgoingCall(
            targetMemberId = contactId,
            targetMemberName = memberName,
            context = context
        )
    }

    LaunchedEffect(callingUiState?.value?.callStatus) {
        if (callingUiState?.value?.callStatus == CallStatus.CONNECTED) {
            onCallConnected()
        }
    }

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
                        text = "WebRTC Secure Stream",
                        fontSize = 12.sp,
                        color = PrimaryTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Radar Pulse around Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                EchoPulseRadar(
                    color = PrimaryTeal,
                    size = 220.dp
                )

                EchoAvatar(
                    name = memberName,
                    size = 110.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = memberName,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = relationship,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryTeal
            )

            Spacer(modifier = Modifier.weight(1f))

            // Cancel Call Button
            IconButton(
                onClick = {
                    callingViewModel?.endCall(reason = "Caller cancelled")
                    onCancelCall()
                },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DangerRed)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Cancel Call",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
