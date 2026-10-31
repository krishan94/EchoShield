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
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoTextField
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.data.preview.PreviewData
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun CallingContactsScreen(
    onOpenDrawer: () -> Unit,
    onStartOutgoingCall: (String) -> Unit,
    onSimulateIncomingCall: (String) -> Unit
) {
    val members = PreviewData.familyMembers
    var searchQuery by remember { mutableStateOf("") }

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
                // Button to simulate receiving an incoming call (for testing & UX demonstration)
                IconButton(onClick = { onSimulateIncomingCall(members.first().id) }) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = "Simulate Incoming Call",
                        tint = PrimaryTeal
                    )
                }
            }
        )

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
                        text = "Calls are transmitted over encrypted WebRTC audio streams. Cellular SIM calls are never tapped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                            size = 50.dp,
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
                                text = "${member.relationship} • ${if (member.isOnline) "Online" else member.lastActive}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Status text
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (member.isVoiceEnrolled) SuccessGreen else WarningAmber)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (member.isVoiceEnrolled) "Voice Biometrics Armed" else "Voice Unregistered",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (member.isVoiceEnrolled) SuccessGreen else WarningAmber
                                )
                            }
                        }

                        // Call Action Button
                        IconButton(
                            onClick = { onStartOutgoingCall(member.id) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryTeal)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call ${member.name}",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
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
