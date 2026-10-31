package com.echoshield.family

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoDangerButton
import com.echoshield.core.components.EchoEmptyState
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoSecondaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.WarningAmber

@Composable
fun InvitationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: FamilyViewModel = viewModel(factory = FamilyViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val incoming = uiState.incomingInvitations
    val outgoing = uiState.outgoingInvitations
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Incoming, 1: Outgoing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Circle Invitations",
            subtitle = "${incoming.size} Incoming • ${outgoing.size} Outgoing",
            onNavigateBack = onNavigateBack,
            actions = {
                IconButton(onClick = { viewModel.loadFamilyData() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Invitations",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        // Tab Selector
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = PrimaryTeal,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PrimaryTeal
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Incoming (${incoming.size})",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Outgoing (${outgoing.size})",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        // Error message banner
        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DangerRed.copy(alpha = 0.12f))
                    .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }
            }
        }

        if (selectedTab == 0) {
            // Incoming Invitations View
            if (incoming.isEmpty()) {
                EchoEmptyState(
                    title = "No Pending Incoming Requests",
                    description = "When family members send you an invitation to link circles, it will appear here for your confirmation.",
                    icon = Icons.Default.MarkEmailRead,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Pending Family Approvals",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Accepting an invitation links your accounts for encrypted calling and mutual biometric voice verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(incoming, key = { it.id }) { invite ->
                        EchoCard(padding = 18.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EchoAvatar(
                                    name = invite.senderName,
                                    size = 48.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = invite.senderName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = invite.senderEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Proposed Relationship: ${invite.relationship}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = PrimaryTeal
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                EchoDangerButton(
                                    text = "Decline",
                                    onClick = { viewModel.rejectInvitation(invite.id) },
                                    leadingIcon = Icons.Default.Close,
                                    height = 44.dp,
                                    modifier = Modifier.weight(1f)
                                )
                                EchoPrimaryButton(
                                    text = "Accept",
                                    onClick = { viewModel.acceptInvitation(invite.id) },
                                    leadingIcon = Icons.Default.Check,
                                    height = 44.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Outgoing Invitations View
            if (outgoing.isEmpty()) {
                EchoEmptyState(
                    title = "No Pending Outgoing Invitations",
                    description = "Invitations you dispatch to family members will be tracked here until they confirm.",
                    icon = Icons.Default.Outbox,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Awaiting Recipient Confirmation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "These family members have received an invitation token. You can cancel at any time before they accept.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(outgoing, key = { it.id }) { invite ->
                        EchoCard(padding = 18.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = invite.recipientEmail,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Role: ${invite.relationship}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WarningAmber.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Pending",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = WarningAmber
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            EchoSecondaryButton(
                                text = "Cancel Invitation",
                                onClick = { viewModel.cancelInvitation(invite.id) },
                                leadingIcon = Icons.Default.Close,
                                height = 40.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
