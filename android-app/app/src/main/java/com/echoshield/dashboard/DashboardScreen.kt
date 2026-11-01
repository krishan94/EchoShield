package com.echoshield.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.authentication.AuthViewModel
import com.echoshield.calling.CallingViewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoEmptyState
import com.echoshield.core.components.EchoProtectionStatus
import com.echoshield.core.components.EchoStatusIndicator
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.domain.model.CallHistoryRecord
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.family.FamilyViewModel
import com.echoshield.theme.DangerRed
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber
import com.echoshield.voice.VoiceViewModel
import java.util.Calendar

@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onCallFamily: () -> Unit,
    onAddFamilyMember: () -> Unit,
    onRegisterVoice: () -> Unit,
    onViewReports: () -> Unit,
    onViewCallDetail: (String) -> Unit,
    onViewInvitations: (() -> Unit)? = null,
    authViewModel: AuthViewModel? = null,
    familyViewModel: FamilyViewModel? = null,
    voiceViewModel: VoiceViewModel? = null,
    callingViewModel: CallingViewModel? = null
) {
    // Refresh real data on dashboard load
    LaunchedEffect(Unit) {
        familyViewModel?.loadFamilyData()
        voiceViewModel?.refreshVoiceProfile()
        callingViewModel?.loadCallHistory()
    }

    val authState by authViewModel?.uiState?.collectAsState() ?: androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(null)
    }
    val familyState by familyViewModel?.uiState?.collectAsState() ?: androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(null)
    }
    val voiceState by voiceViewModel?.uiState?.collectAsState() ?: androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(null)
    }
    val callingState by callingViewModel?.uiState?.collectAsState() ?: androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(null)
    }

    val user = authState?.user
    val fullName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Family Guardian"
    val firstName = fullName.split(" ").firstOrNull() ?: fullName
    val familyMembers = familyState?.members ?: emptyList()
    val incomingInvitations = familyState?.incomingInvitations ?: emptyList()
    val isVoiceEnrolled = voiceState?.voiceProfile?.isVoiceEnrolled == true
    val recentCalls = callingState?.callHistory ?: emptyList()

    // Dynamic greeting calculation
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (currentHour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "EchoShield",
            subtitle = "AI Voice Biometrics Defense",
            onOpenDrawer = onOpenDrawer,
            actions = {
                IconButton(onClick = onNavigateToNotifications) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Security Notifications",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        if (incomingInvitations.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Personalized Greeting & Identity Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoAvatar(
                        name = fullName,
                        size = 52.dp,
                        isVoiceEnrolled = isVoiceEnrolled
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$greeting, $firstName",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isVoiceEnrolled) "Biometric Signature Armed" else "Voice Enrollment Pending",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isVoiceEnrolled) MaterialTheme.colorScheme.primary else WarningAmber
                        )
                    }
                    EchoStatusIndicator(
                        status = if (isVoiceEnrolled) EchoProtectionStatus.ACTIVE else EchoProtectionStatus.WARNING
                    )
                }
            }

            // 2. Pending Invitations Alert Card (if any)
            if (incomingInvitations.isNotEmpty()) {
                item {
                    EchoCard(
                        padding = 14.dp,
                        borderColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        onClick = { onViewInvitations?.invoke() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${incomingInvitations.size} Pending Family Invitation(s)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap to review and accept circle requests",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. System Defense Status Card
            item {
                EchoCard(padding = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DEFENSE OVERVIEW",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isVoiceEnrolled) "Encrypted & Armed" else "Action Required",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Family Circle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${familyMembers.size} Member${if (familyMembers.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Your Voice Profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isVoiceEnrolled) "Enrolled & Ready" else "Not Enrolled",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isVoiceEnrolled) SuccessGreen else WarningAmber
                            )
                        }
                    }
                }
            }

            // 4. Quick Actions
            item {
                Column {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionPill(
                            title = "Call Family",
                            icon = Icons.Default.Call,
                            onClick = onCallFamily,
                            modifier = Modifier.weight(1f)
                        )
                        ActionPill(
                            title = "Add Member",
                            icon = Icons.Default.Add,
                            onClick = onAddFamilyMember,
                            modifier = Modifier.weight(1f)
                        )
                        ActionPill(
                            title = "Voice Studio",
                            icon = Icons.Default.Mic,
                            onClick = onRegisterVoice,
                            modifier = Modifier.weight(1f)
                        )
                        ActionPill(
                            title = "Reports",
                            icon = Icons.Default.Description,
                            onClick = onViewReports,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Recent Verified Calls Section (Real backend data)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Verified Calls",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (recentCalls.isNotEmpty()) {
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onViewReports() }
                        )
                    }
                }
            }

            if (callingState?.isLoadingHistory == true && recentCalls.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else if (recentCalls.isEmpty()) {
                item {
                    EchoCard(padding = 24.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No completed calls available",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Encrypted WebRTC calls between your family members will appear here with live biometric screening reports.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(recentCalls.take(4)) { call ->
                    RecentCallCard(
                        call = call,
                        onClick = { onViewCallDetail(call.callId) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun RecentCallCard(
    call: CallHistoryRecord,
    onClick: () -> Unit
) {
    val isIncoming = call.direction.equals("incoming", ignoreCase = true)
    val outcome = call.securityOutcome ?: CombinedSecurityOutcome.IDENTITY_CONFIRMED

    val verdictColor = when (outcome) {
        CombinedSecurityOutcome.IDENTITY_CONFIRMED -> SuccessGreen
        CombinedSecurityOutcome.SUSPICIOUS_AUDIO,
        CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED -> DangerRed
        CombinedSecurityOutcome.AUDIO_QUALITY_FAILED,
        CombinedSecurityOutcome.INCONCLUSIVE -> WarningAmber
        else -> MaterialTheme.colorScheme.primary
    }

    val verdictLabel = when (outcome) {
        CombinedSecurityOutcome.IDENTITY_CONFIRMED -> "Verified"
        CombinedSecurityOutcome.SUSPICIOUS_AUDIO -> "Spoof Alert"
        CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED -> "Mismatch"
        CombinedSecurityOutcome.AUDIO_QUALITY_FAILED -> "Poor Audio"
        CombinedSecurityOutcome.INCONCLUSIVE -> "Inconclusive"
        else -> "Evaluated"
    }

    EchoCard(
        padding = 14.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoAvatar(
                name = call.otherPartyName,
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.otherPartyName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${call.durationSeconds}s • ${call.createdAt.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(verdictColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = verdictLabel,
                    fontSize = 11.sp,
                    color = verdictColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val primaryColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = primaryColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
