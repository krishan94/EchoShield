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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoTextField
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.core.components.EchoVerificationBadge
import com.echoshield.data.preview.PreviewData
import com.echoshield.domain.model.CallType
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal

@Composable
fun CallHistoryScreen(
    onOpenDrawer: () -> Unit,
    onViewReportDetails: (String) -> Unit
) {
    val allCalls = PreviewData.callRecords
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val tabTitles = listOf("All", "Incoming", "Outgoing", "Missed")

    val filteredCalls = allCalls.filter { call ->
        val matchesTab = when (selectedTabIndex) {
            1 -> call.callType == CallType.INCOMING
            2 -> call.callType == CallType.OUTGOING
            3 -> call.callType == CallType.MISSED
            else -> true
        }
        val matchesSearch = call.contactName.contains(searchQuery, ignoreCase = true) ||
                call.contactRelationship.contains(searchQuery, ignoreCase = true)
        matchesTab && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Call Verification History",
            subtitle = "Audit Trail & Threat Log",
            onOpenDrawer = onOpenDrawer
        )

        // Search Bar
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            EchoTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search Call Logs",
                placeholder = "Search by name or relationship...",
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredCalls) { call ->
                EchoCard(
                    padding = 16.dp,
                    onClick = { onViewReportDetails(call.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Direction Icon
                        val directionIcon = when (call.callType) {
                            CallType.INCOMING -> Icons.Default.CallReceived
                            CallType.OUTGOING -> Icons.Default.CallMade
                            CallType.MISSED -> Icons.Default.CallMissed
                        }
                        val directionColor = when (call.callType) {
                            CallType.INCOMING -> PrimaryTeal
                            CallType.OUTGOING -> MaterialTheme.colorScheme.secondary
                            CallType.MISSED -> DangerRed
                        }

                        EchoAvatar(
                            name = call.contactName,
                            size = 46.dp,
                            isVoiceEnrolled = call.speakerSimilarity > 0.7f
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
                                    text = call.contactName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${call.contactRelationship} • ${call.timestamp} (${call.durationText})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        EchoVerificationBadge(
                            verdict = call.verdict,
                            compact = true
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

