package com.echoshield.calling

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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoEmptyState
import com.echoshield.core.components.EchoTextField
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.family.FamilyViewModel
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun CallingContactsScreen(
    onOpenDrawer: () -> Unit,
    onStartOutgoingCall: (String) -> Unit,
    onSimulateIncomingCall: ((String) -> Unit)? = null,
    onAddFamilyMember: (() -> Unit)? = null,
    familyViewModel: FamilyViewModel? = null
) {
    val familyUiState = familyViewModel?.uiState?.collectAsState()
    val isLoading = familyUiState?.value?.isLoading == true
    val members = familyUiState?.value?.members ?: emptyList()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (members.isEmpty()) {
            familyViewModel?.loadFamilyData()
        }
    }

    val filteredMembers = members.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.relationship.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Encrypted Calls",
            subtitle = "Protected WebRTC Audio Channels",
            onOpenDrawer = onOpenDrawer,
            actions = {
                if (onSimulateIncomingCall != null && members.isNotEmpty()) {
                    IconButton(onClick = { onSimulateIncomingCall(members.first().id) }) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = "Simulate Incoming Call",
                            tint = PrimaryTeal
                        )
                    }
                }
            }
        )

        if (isLoading && members.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (members.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EchoEmptyState(
                    title = "No Family Members Added",
                    description = "Add family members to your secure network to start encrypted calls and live voice verification.",
                    icon = Icons.Default.PersonAdd,
                    actionButtonText = onAddFamilyMember?.let { "Add Family Member" },
                    onActionClick = onAddFamilyMember
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    EchoTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "Find Family Contact",
                        placeholder = "Search by name or relationship...",
                        leadingIcon = Icons.Default.Search
                    )
                }

                item {
                    EchoCard(
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        padding = 14.dp
                    ) {
                        Text(
                            text = "Encrypted Peer-to-Peer Calling",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calls are transmitted over encrypted WebRTC audio streams. Cellular carrier calls are never tapped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (filteredMembers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EchoEmptyState(
                                title = "No Matching Contacts",
                                description = "No family members match \"$searchQuery\".",
                                icon = Icons.Default.Search
                            )
                        }
                    }
                } else {
                    items(filteredMembers) { member ->
                        EchoCard(
                            padding = 16.dp,
                            onClick = { onStartOutgoingCall(member.id) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EchoAvatar(
                                    name = member.name,
                                    isOnline = member.isOnline,
                                    size = 52.dp
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = member.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (member.isVoiceEnrolled) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = "Voice Enrolled",
                                                tint = PrimaryTeal,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = member.relationship,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (member.isVoiceEnrolled) SuccessGreen else WarningAmber)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (member.isVoiceEnrolled) "Voice Identity Shield Active" else "Voice Not Enrolled",
                                            fontSize = 11.sp,
                                            color = if (member.isVoiceEnrolled) SuccessGreen else WarningAmber,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Call Action Button
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryTeal.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call ${member.name}",
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
