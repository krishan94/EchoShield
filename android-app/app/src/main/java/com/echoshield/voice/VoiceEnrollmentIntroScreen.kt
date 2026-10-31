package com.echoshield.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoConsentDialog
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal

@Composable
fun VoiceEnrollmentIntroScreen(
    onNavigateBack: () -> Unit,
    onProceedToPermission: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConsentDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Voice Studio Enrollment",
            subtitle = "Biometric Signature Setup",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
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

            // Header Intro Card
            EchoCard(padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(PrimaryTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Acoustic Fingerprint Setup",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "3 Guided Speech Samples",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Enrollment Protocol Disclosures",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            EnrollmentInfoCard(
                icon = Icons.Default.GraphicEq,
                title = "Why Enrollment is Mandatory",
                description = "Without a registered biometric baseline, incoming callers cannot be authenticated against your real acoustic identity, rendering deepfake detection inconclusive."
            )

            Spacer(modifier = Modifier.height(12.dp))

            EnrollmentInfoCard(
                icon = Icons.Default.Lock,
                title = "Mathematical Embedding Extraction",
                description = "SpeechBrain ECAPA-TDNN transforms your speech into a 192-dimensional vector embedding. Raw recordings are purged immediately from disk and are never retained permanently."
            )

            Spacer(modifier = Modifier.height(12.dp))

            EnrollmentInfoCard(
                icon = Icons.Default.PrivacyTip,
                title = "Informed Biometric Consent",
                description = "Your consent is cryptographically recorded. Voice vectors are encrypted at rest with AES-256-GCM and can be wiped instantly via Settings > Privacy > Voice Data."
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Action Button
            EchoPrimaryButton(
                text = if (uiState.isConsentGranted) "Consent Verified • Begin Studio" else "Review Consent & Begin Studio",
                onClick = {
                    if (uiState.isConsentGranted) {
                        onProceedToPermission()
                    } else {
                        showConsentDialog = true
                    }
                },
                isLoading = uiState.isLoading,
                trailingIcon = Icons.Default.ArrowForward
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showConsentDialog) {
        EchoConsentDialog(
            onConsentAgreed = {
                showConsentDialog = false
                viewModel.recordConsent(true) {
                    onProceedToPermission()
                }
            },
            onDismiss = { showConsentDialog = false }
        )
    }
}

@Composable
private fun EnrollmentInfoCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    EchoCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
