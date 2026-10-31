package com.echoshield.voice

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoConfirmationDialog
import com.echoshield.core.components.EchoDangerButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoSecondaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun VoiceProfileStatusScreen(
    onNavigateToDashboard: () -> Unit,
    onReEnrollVoice: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.voiceProfile
    val isEnrolled = profile?.isVoiceEnrolled ?: false
    val qualityScore = profile?.voiceQualityScore ?: 0.0f

    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Biometric Profile Status",
            subtitle = if (isEnrolled) "Active Family Voice Certificate" else "Voice Enrollment Pending",
            onNavigateBack = onNavigateToDashboard,
            actions = {
                IconButton(onClick = { viewModel.refreshVoiceProfile() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Error banner if any
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

            // Status Shield Badge
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(if (isEnrolled) SuccessGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isEnrolled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isEnrolled) SuccessGreen else WarningAmber,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (isEnrolled) "Voice Certificate Activated" else "No Active Voice Certificate",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isEnrolled) {
                    "Protected with AES-256-GCM Mathematical Encryption"
                } else {
                    "Complete voice studio enrollment to establish biometric verification."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnrolled) PrimaryTeal else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Quality & Metric Card
            EchoCard(padding = 20.dp) {
                Text(
                    text = "Acoustic Embedding Reliability",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEnrolled) {
                        "${(qualityScore * 100).toInt()}% Optimal Verification Baseline"
                    } else {
                        "0% — Voice Baseline Not Recorded"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEnrolled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { if (isEnrolled) qualityScore else 0.0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isEnrolled) SuccessGreen else WarningAmber,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Embedding Model:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = profile?.embeddingModel ?: "SpeechBrain ECAPA-TDNN",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Vector Dimension:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "192-dimensional tensor",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Biometric Consent:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (uiState.isConsentGranted) "Informed Consent Granted (v1.0)" else "Consent Pending",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (uiState.isConsentGranted) SuccessGreen else WarningAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            if (isEnrolled) {
                EchoSecondaryButton(
                    text = "Re-Enroll Voice Samples",
                    onClick = onReEnrollVoice,
                    leadingIcon = Icons.Default.Refresh,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                EchoDangerButton(
                    text = "Delete Biometric Profile & Data",
                    onClick = { showDeleteDialog = true },
                    leadingIcon = Icons.Default.Delete,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                EchoPrimaryButton(
                    text = "Begin Voice Studio Enrollment",
                    onClick = onReEnrollVoice,
                    leadingIcon = Icons.Default.Mic,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteDialog) {
        EchoConfirmationDialog(
            title = "Delete Voice Profile?",
            message = "This permanently purges your encrypted biometric embeddings and revokes informed consent. In-app callers will no longer be verified against your voice baseline until you re-enroll.",
            confirmText = "Delete Biometric Data",
            isDestructive = true,
            icon = Icons.Default.Delete,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteVoiceProfile {
                    onNavigateToDashboard()
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
