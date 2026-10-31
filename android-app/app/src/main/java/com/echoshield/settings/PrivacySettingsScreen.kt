package com.echoshield.settings

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
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
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoConfirmationDialog
import com.echoshield.core.components.EchoDangerButton
import com.echoshield.core.components.EchoOutlinedButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal

@Composable
fun PrivacySettingsScreen(
    onNavigateBack: () -> Unit
) {
    var showDeleteVoiceDialog by remember { mutableStateOf(false) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Biometric Privacy Rights",
            subtitle = "Data Sovereignty & Purge Controls",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Privacy Pledge Card
            EchoCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(PrimaryTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Zero-Surveillance Architecture",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Compliant with GDPR & Biometric Data Acts",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryTeal
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "EchoShield never records cellular phone calls and does not store raw audio files. Only 192-dimensional numerical vectors are stored with hardware-level AES-256-GCM encryption. You may purge any or all data at any moment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Voice Profile Management
            Text(
                text = "Biometric Voice Data",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            EchoCard(padding = 16.dp) {
                Text(
                    text = "Current Voice Profile",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Active • Enrolled Oct 28, 2026 • 192-dim ECAPA Vector",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                EchoDangerButton(
                    text = "Cryptographically Purge Voice Profile",
                    onClick = { showDeleteVoiceDialog = true },
                    leadingIcon = Icons.Default.Delete,
                    height = 44.dp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: History Purge
            Text(
                text = "Call Audit Log History",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            EchoCard(padding = 16.dp) {
                Text(
                    text = "Verification Audit Records",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "48 forensic records logged • Zero audio retained",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                EchoDangerButton(
                    text = "Wipe All Forensic Logs",
                    onClick = { showDeleteHistoryDialog = true },
                    leadingIcon = Icons.Default.Delete,
                    height = 44.dp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Account Deletion
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DangerRed
            )
            Spacer(modifier = Modifier.height(8.dp))

            EchoCard(
                borderColor = DangerRed.copy(alpha = 0.5f),
                padding = 16.dp
            ) {
                Text(
                    text = "Permanent Account Deletion",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = DangerRed
                )
                Text(
                    text = "Immediately purges family circle membership, user profile, cryptographic keys, and all registered biometric vectors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                EchoDangerButton(
                    text = "Delete Entire EchoShield Account",
                    onClick = { showDeleteAccountDialog = true },
                    leadingIcon = Icons.Default.DeleteForever,
                    height = 44.dp
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showDeleteVoiceDialog) {
        EchoConfirmationDialog(
            title = "Purge Biometric Voice Signature?",
            message = "This permanently wipes your mathematical voice embedding from your device Keystore and encrypted cloud storage. Your family will no longer be able to verify your incoming calls.",
            confirmText = "Purge Signature",
            isDestructive = true,
            icon = Icons.Default.Delete,
            onConfirm = { showDeleteVoiceDialog = false },
            onDismiss = { showDeleteVoiceDialog = false }
        )
    }

    if (showDeleteHistoryDialog) {
        EchoConfirmationDialog(
            title = "Wipe All Audit Logs?",
            message = "All past call verification scores, spoof alerts, and acoustic analysis summaries will be permanently deleted.",
            confirmText = "Delete History",
            isDestructive = true,
            icon = Icons.Default.Delete,
            onConfirm = { showDeleteHistoryDialog = false },
            onDismiss = { showDeleteHistoryDialog = false }
        )
    }

    if (showDeleteAccountDialog) {
        EchoConfirmationDialog(
            title = "Permanently Delete Account?",
            message = "This action is irreversible. All family memberships, voice certificates, and verification histories will be destroyed.",
            confirmText = "Delete Everything",
            isDestructive = true,
            icon = Icons.Default.DeleteForever,
            onConfirm = {
                showDeleteAccountDialog = false
                onNavigateBack()
            },
            onDismiss = { showDeleteAccountDialog = false }
        )
    }
}
