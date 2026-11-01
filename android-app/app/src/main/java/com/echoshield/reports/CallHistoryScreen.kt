package com.echoshield.reports

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.echoshield.calling.CallingViewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoEmptyState
import com.echoshield.core.components.EchoTextField
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.domain.model.CallStatus
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun CallHistoryScreen(
    onOpenDrawer: () -> Unit,
    onViewReportDetails: (String) -> Unit,
    onViewVerificationAudit: (() -> Unit)? = null,
    callingViewModel: CallingViewModel? = null
) {
    val callingUiState = callingViewModel?.uiState?.collectAsState()
    val realCalls = callingUiState?.value?.callHistory ?: emptyList()
    val isLoading = callingUiState?.value?.isLoadingHistory == true

    LaunchedEffect(Unit) {
        callingViewModel?.loadCallHistory()
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val tabTitles = listOf("All", "Incoming", "Outgoing", "Missed")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Call Verification History",
            subtitle = "Audit Trail & WebRTC Call Logs",
            onOpenDrawer = onOpenDrawer,
            actions = {
                if (onViewVerificationAudit != null) {
                    IconButton(onClick = onViewVerificationAudit) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Voice Verification Audit Log",
                            tint = PrimaryTeal
                        )
                    }
                }
            }
        )

        // Search Bar
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            EchoTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search Call Logs",
                placeholder = "Search by name or status...",
                leadingIcon = Icons.Default.Search
            )
        }

        // Tabs: All, Incoming, Outgoing, Missed
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = PrimaryTeal,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = PrimaryTeal
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selectedTabIndex == index) PrimaryTeal else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        if (isLoading && realCalls.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (realCalls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EchoEmptyState(
                    icon = Icons.Default.Call,
                    title = "No Completed Calls",
                    description = "You have not made or received any encrypted voice calls yet. Encrypted call history and security audit reports will appear here."
                )
            }
        } else {
            val filteredRealCalls = realCalls.filter { call ->
                val matchesTab = when (selectedTabIndex) {
                    1 -> call.direction.equals("incoming", ignoreCase = true)
                    2 -> call.direction.equals("outgoing", ignoreCase = true)
                    3 -> call.status == CallStatus.MISSED || call.status == CallStatus.REJECTED
                    else -> true
                }
                val matchesSearch = call.otherPartyName.contains(searchQuery, ignoreCase = true) ||
                        call.callerName.contains(searchQuery, ignoreCase = true) ||
                        call.calleeName.contains(searchQuery, ignoreCase = true)
                matchesTab && matchesSearch
            }

            if (filteredRealCalls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EchoEmptyState(
                        icon = Icons.Default.Search,
                        title = "No Matching Calls",
                        description = "No call logs match the selected filter or search term."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredRealCalls) { call ->
                        EchoCard(
                            padding = 16.dp,
                            onClick = { onViewReportDetails(call.callId) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isIncoming = call.direction.equals("incoming", ignoreCase = true)
                                val isMissed = call.status == CallStatus.MISSED || call.status == CallStatus.REJECTED

                                val directionIcon = when {
                                    isMissed -> Icons.Default.CallMissed
                                    isIncoming -> Icons.Default.CallReceived
                                    else -> Icons.Default.CallMade
                                }

                                val directionColor = when {
                                    isMissed -> DangerRed
                                    isIncoming -> PrimaryTeal
                                    else -> MaterialTheme.colorScheme.secondary
                                }

                                EchoAvatar(
                                    name = call.otherPartyName,
                                    size = 46.dp,
                                    isVoiceEnrolled = true
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = directionIcon,
                                            contentDescription = null,
                                            tint = directionColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = call.otherPartyName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val mins = call.durationSeconds / 60
                                    val secs = call.durationSeconds % 60
                                    val durationText = if (call.durationSeconds > 0) String.format("%02d:%02d", mins, secs) else call.status.name
                                    Text(
                                        text = "${call.direction.replaceFirstChar { it.uppercase() }} • $durationText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Render Phase 7 Security Verdict
                                val outcome = call.securityOutcome ?: CombinedSecurityOutcome.IDENTITY_CONFIRMED
                                val badgeColor = when (outcome) {
                                    CombinedSecurityOutcome.IDENTITY_CONFIRMED -> SuccessGreen
                                    CombinedSecurityOutcome.SUSPICIOUS_AUDIO -> DangerRed
                                    CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED -> DangerRed
                                    CombinedSecurityOutcome.INCONCLUSIVE -> WarningAmber
                                    CombinedSecurityOutcome.AUDIO_QUALITY_FAILED -> WarningAmber
                                    else -> PrimaryTeal
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = outcome.name.replace("_", " "),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
