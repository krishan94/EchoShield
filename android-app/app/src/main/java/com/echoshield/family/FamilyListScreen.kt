package com.echoshield.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoCyberSpinner
import com.echoshield.core.components.EchoEmptyState
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun FamilyListScreen(
    onOpenDrawer: () -> Unit,
    onAddMember: () -> Unit,
    onViewInvitations: () -> Unit,
    onMemberClick: (String) -> Unit,
    onStartCall: (String) -> Unit,
    viewModel: FamilyViewModel = viewModel(factory = FamilyViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val members = uiState.members
    val pendingCount = uiState.incomingInvitations.size

    Scaffold(
        topBar = {
            EchoTopAppBar(
                title = "Family Circle",
                subtitle = "${members.size} Verified Connection${if (members.size != 1) "s" else ""}",
                onOpenDrawer = onOpenDrawer,
                actions = {
                    IconButton(onClick = { viewModel.loadFamilyData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Family List",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onViewInvitations) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = "Pending Invitations",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            if (pendingCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(AccentBlue)
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddMember,
                containerColor = PrimaryTeal,
                contentColor = Color.Black
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Family Member"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && members.isEmpty()) {
                EchoCyberSpinner(
                    label = "Synchronizing encrypted family circle...",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (members.isEmpty()) {
                EchoEmptyState(
                    title = "Your Family Circle is Empty",
                    description = "Add your loved ones to establish mutual biometric voice verification and protect them from AI deepfake impersonation.",
                    actionButtonText = "Invite Family Member",
                    onActionClick = onAddMember,
                    icon = Icons.Default.Group,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))

                        // Error Banner if present
                        if (uiState.errorMessage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Explanatory Banner
                        EchoCard(
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            padding = 14.dp
                        ) {
                            Text(
                                text = "Encrypted Family Circle",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All outgoing and incoming voice calls with enrolled family members are continuously verified against their consent-registered voice signatures.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(members, key = { it.id }) { member ->
                        EchoCard(
                            padding = 16.dp,
                            onClick = { onMemberClick(member.id) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EchoAvatar(
                                    name = member.name,
                                    size = 52.dp,
                                    isOnline = member.isOnline,
                                    isVoiceEnrolled = member.isVoiceEnrolled
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (member.email.isNotBlank()) "${member.relationship} • ${member.email}" else member.relationship,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Voice Enrollment Status Pill derived from actual data
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (member.isVoiceEnrolled) SuccessGreen else WarningAmber)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (member.isVoiceEnrolled) "Voice Verified" else "Voice Pending",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (member.isVoiceEnrolled) SuccessGreen else WarningAmber
                                        )
                                    }
                                }

                                // Call Action Button
                                IconButton(
                                    onClick = { onStartCall(member.memberId.ifEmpty { member.id }) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryTeal.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call ${member.name}",
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Profile Details Button
                                IconButton(
                                    onClick = { onMemberClick(member.id) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "View Profile",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
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
