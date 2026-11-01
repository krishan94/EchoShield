package com.echoshield.notifications

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.calling.CallingViewModel
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoEmptyState
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.domain.model.NotificationItem
import com.echoshield.domain.model.NotificationType
import com.echoshield.family.FamilyViewModel
import com.echoshield.theme.DangerRed
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNotificationClick: (String) -> Unit,
    familyViewModel: FamilyViewModel? = null,
    callingViewModel: CallingViewModel? = null
) {
    LaunchedEffect(Unit) {
        familyViewModel?.loadFamilyData()
        callingViewModel?.loadCallHistory()
    }

    val familyState by familyViewModel?.uiState?.collectAsState() ?: remember {
        androidx.compose.runtime.mutableStateOf(null)
    }
    val callingState by callingViewModel?.uiState?.collectAsState() ?: remember {
        androidx.compose.runtime.mutableStateOf(null)
    }

    val incomingInvitations = familyState?.incomingInvitations ?: emptyList()
    val callHistory = callingState?.callHistory ?: emptyList()

    // Aggregate real notifications from actual backend models
    val notificationList = remember(incomingInvitations, callHistory) {
        val list = mutableListOf<NotificationItem>()

        incomingInvitations.forEach { invite ->
            list.add(
                NotificationItem(
                    id = invite.id,
                    title = "Family Circle Invitation",
                    message = "${invite.senderEmail} invited you to join the family circle.",
                    timestamp = invite.createdAt.take(10),
                    type = NotificationType.FAMILY_INVITE,
                    isRead = false
                )
            )
        }

        callHistory.forEach { call ->
            val isThreat = call.securityOutcome == CombinedSecurityOutcome.SUSPICIOUS_AUDIO ||
                    call.securityOutcome == CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED
            if (isThreat) {
                list.add(
                    NotificationItem(
                        id = call.callId,
                        title = "Security Alert: ${call.securityHeadline ?: "Suspicious Audio"}",
                        message = "Voice biometric screening during call with ${call.otherPartyName} flagged potential synthetic anomalies.",
                        timestamp = call.createdAt.take(10),
                        type = NotificationType.SECURITY_ALERT,
                        isRead = false
                    )
                )
            } else if (call.status.name.contains("MISSED", ignoreCase = true)) {
                list.add(
                    NotificationItem(
                        id = call.callId,
                        title = "Missed Encrypted Call",
                        message = "Missed voice call from ${call.otherPartyName}.",
                        timestamp = call.createdAt.take(10),
                        type = NotificationType.MISSED_CALL,
                        isRead = true
                    )
                )
            }
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Security Notifications",
            subtitle = if (notificationList.isEmpty()) "All caught up" else "${notificationList.count { !it.isRead }} Unread Alerts",
            onNavigateBack = onNavigateBack
        )

        if (notificationList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                EchoEmptyState(
                    title = "No notifications yet",
                    description = "Family circle requests, call alerts, and live acoustic threat detections will appear here.",
                    icon = Icons.Default.Notifications
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notificationList) { item ->
                    val (icon, iconColor) = when (item.type) {
                        NotificationType.SECURITY_ALERT -> Pair(Icons.Default.Warning, DangerRed)
                        NotificationType.FAMILY_INVITE -> Pair(Icons.Default.GroupAdd, MaterialTheme.colorScheme.primary)
                        NotificationType.MISSED_CALL -> Pair(Icons.Default.CallMissed, DangerRed)
                        NotificationType.SYSTEM_UPDATE -> Pair(Icons.Default.CheckCircle, SuccessGreen)
                    }

                    EchoCard(
                        padding = 16.dp,
                        backgroundColor = if (!item.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
                        onClick = { onNotificationClick(item.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(iconColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = item.timestamp,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
