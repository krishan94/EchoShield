package com.echoshield.family

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoConfirmationDialog
import com.echoshield.core.components.EchoDangerButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoSecondaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.data.preview.PreviewData
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun FamilyMemberProfileScreen(
    memberId: String,
    onNavigateBack: () -> Unit,
    onStartCall: (String) -> Unit
) {
    val member = PreviewData.familyMembers.find { it.id == memberId } ?: PreviewData.familyMembers.first()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var currentRelationship by remember { mutableStateOf(member.relationship) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = member.name,
            subtitle = "Family Circle Profile",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Avatar & Name Card
            EchoAvatar(
                name = member.name,
                size = 90.dp,
                isOnline = member.isOnline,
                isVoiceEnrolled = member.isVoiceEnrolled
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = member.name,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "$currentRelationship • ${member.lastActive}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Call Action Button
            EchoPrimaryButton(
                text = "Initiate Encrypted Call",
                onClick = { onStartCall(member.id) },
                leadingIcon = Icons.Default.Call
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Enrollment & Voice Status Card
            EchoCard(padding = 18.dp) {
                Text(
                    text = "Biometric Voice Profile Status",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (member.isVoiceEnrolled) SuccessGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (member.isVoiceEnrolled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (member.isVoiceEnrolled) SuccessGreen else WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (member.isVoiceEnrolled) "Voice Signature Active" else "Enrollment Pending",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (member.isVoiceEnrolled) "192-dim ECAPA-TDNN Embedding stored with AES-256-GCM encryption." else "This member has not yet recorded voice samples. Calls cannot be biometrically verified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (member.isVoiceEnrolled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Acoustic Sample Quality:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(member.voiceQualityScore * 100).toInt()}% Optimal",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Relationship Editing Card
            EchoCard(padding = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Family Circle Role",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = currentRelationship,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    EchoSecondaryButton(
                        text = "Edit",
                        onClick = {
                            currentRelationship = if (currentRelationship == "Spouse") "Co-Guardian" else "Spouse"
                        },
                        leadingIcon = Icons.Default.Edit,
                        modifier = Modifier.width(100.dp),
                        height = 40.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Remove Connection Button
            EchoDangerButton(
                text = "Remove From Family Circle",
                onClick = { showDeleteDialog = true },
                leadingIcon = Icons.Default.Delete
            )
        }
    }

    if (showDeleteDialog) {
        EchoConfirmationDialog(
            title = "Remove ${member.name}?",
            message = "Removing this member disconnects encrypted in-app calling and mutual biometric voice verification. This action can be reversed via a new family invitation.",
            confirmText = "Remove Member",
            isDestructive = true,
            icon = Icons.Default.Delete,
            onConfirm = {
                showDeleteDialog = false
                onNavigateBack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
