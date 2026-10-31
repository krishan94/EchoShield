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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoConfirmationDialog
import com.echoshield.core.components.EchoDangerButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.domain.model.FamilyMember
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun FamilyMemberProfileScreen(
    memberId: String,
    onNavigateBack: () -> Unit,
    onStartCall: (String) -> Unit,
    viewModel: FamilyViewModel = viewModel(factory = FamilyViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(memberId) {
        viewModel.loadMemberProfile(memberId)
    }

    val member: FamilyMember? = uiState.members.find { it.id == memberId || it.memberId == memberId }
        ?: uiState.selectedMember

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (member == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            EchoTopAppBar(
                title = "Profile",
                subtitle = "Family Circle",
                onNavigateBack = onNavigateBack
            )
            com.echoshield.core.components.EchoEmptyState(
                title = "Member Not Found",
                description = "The requested family circle profile could not be located or has been severed.",
                actionButtonText = "Return to Family Circle",
                onActionClick = onNavigateBack,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

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

            // Error banner if present
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
                Spacer(modifier = Modifier.height(14.dp))
            }

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
                text = if (member.email.isNotBlank()) "${member.relationship} • ${member.email}" else member.relationship,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Call Action Button
            EchoPrimaryButton(
                text = "Initiate Encrypted Call",
                onClick = { onStartCall(member.memberId.ifEmpty { member.id }) },
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
                            text = if (member.isVoiceEnrolled) {
                                "Pretrained ECAPA-TDNN embedding verified and protected with hardware keystore encryption."
                            } else {
                                "This member has not yet recorded voice samples. Live calls cannot be biometrically verified until they complete enrollment."
                            },
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

            // Relationship Information Card
            EchoCard(padding = 18.dp) {
                Column {
                    Text(
                        text = "Family Circle Role",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = member.relationship,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Remove Connection Button
            EchoDangerButton(
                text = "Remove From Family Circle",
                onClick = { showDeleteDialog = true },
                leadingIcon = Icons.Default.Delete,
                enabled = !uiState.isLoading
            )
        }
    }

    if (showDeleteDialog) {
        EchoConfirmationDialog(
            title = "Remove ${member.name}?",
            message = "Removing this member disconnects encrypted in-app calling and mutual biometric voice verification. Their account and voice profile will NOT be deleted, and you may re-invite them at any time.",
            confirmText = "Remove Member",
            isDestructive = true,
            icon = Icons.Default.Delete,
            onConfirm = {
                showDeleteDialog = false
                viewModel.removeFamilyMember(member.id, onSuccess = onNavigateBack)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
