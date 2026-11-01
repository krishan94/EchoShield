package com.echoshield.calling

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoWaveformBars
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun ActiveCallScreen(
    callId: String,
    onEndCall: () -> Unit,
    onViewVerificationProgress: (String) -> Unit,
    callingViewModel: CallingViewModel? = null
) {
    val callingUiState = callingViewModel?.uiState?.collectAsState()
    val memberName = callingUiState?.value?.remoteMemberName?.ifBlank { "Family Member" } ?: "Family Member"

    val isMuted = callingUiState?.value?.isMuted ?: false
    val isSpeakerOn = callingUiState?.value?.isSpeakerOn ?: true
    val secondsElapsed = callingUiState?.value?.callDurationSeconds ?: 0

    val securityOutcome = callingUiState?.value?.liveSecurityOutcome
    val headline = callingUiState?.value?.liveSecurityHeadline ?: "Phase 7 Security Shield Active"
    val explanation = callingUiState?.value?.liveSecurityExplanation ?: "Screening remote caller audio stream..."
    val evalCount = callingUiState?.value?.analysesCount ?: 0

    val minutes = secondsElapsed / 60
    val seconds = secondsElapsed % 60
    val formattedTimer = String.format("%02d:%02d", minutes, seconds)

    val badgeColor = when (securityOutcome) {
        CombinedSecurityOutcome.IDENTITY_CONFIRMED -> SuccessGreen
        CombinedSecurityOutcome.SUSPICIOUS_AUDIO -> DangerRed
        CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED -> DangerRed
        CombinedSecurityOutcome.INCONCLUSIVE -> WarningAmber
        CombinedSecurityOutcome.AUDIO_QUALITY_FAILED -> WarningAmber
        CombinedSecurityOutcome.PROFILE_NOT_ENROLLED -> PrimaryTeal
        else -> SuccessGreen
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Real-Time Combined Security Live HUD Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .clickable { onViewVerificationProgress(callId) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = headline,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor,
                                maxLines = 1
                            )
                            Text(
                                text = if (evalCount > 0) "$explanation ($evalCount evals)" else explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Inspect Verification",
                        tint = badgeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Caller Info & Avatar
            EchoAvatar(
                name = memberName,
                size = 110.dp,
                isVoiceEnrolled = true
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = memberName,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "WebRTC Encrypted Peer-to-Peer Stream",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Timer Display
            Text(
                text = formattedTimer,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = PrimaryTeal
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Call Audio Waveform
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                EchoWaveformBars(barCount = 24, isAnimating = true, color = badgeColor)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection Quality Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "WebRTC Audio: Active (Loss: 0.0%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // In-Call Action Controls (Mute, End Call, Speaker)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { callingViewModel?.toggleMute() },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMuted) DangerRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) DangerRed else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isMuted) "Muted" else "Mute",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // End Call Button (Center Big Red)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            callingViewModel?.endCall(reason = "User ended call")
                            onEndCall()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(DangerRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "End Call",
                        style = MaterialTheme.typography.labelSmall,
                        color = DangerRed
                    )
                }

                // Speaker Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { callingViewModel?.toggleSpeaker() },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSpeakerOn) PrimaryTeal.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            contentDescription = if (isSpeakerOn) "Speaker Off" else "Speaker On",
                            tint = if (isSpeakerOn) PrimaryTeal else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isSpeakerOn) "Speaker" else "Earpiece",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
