package com.echoshield.verification

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoConfirmationDialog
import com.echoshield.core.components.EchoEmptyState
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.domain.model.VerificationHistoryRecord
import com.echoshield.domain.model.VerificationOutcome
import com.echoshield.theme.DangerRed
import com.echoshield.theme.InconclusiveGray
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun VerificationHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: VoiceVerificationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadVerificationHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Verification History",
            subtitle = "Forensic Voice Audit Trail",
            onNavigateBack = onNavigateBack,
            actions = {
                if (uiState.historyRecords.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear History",
                            tint = DangerRed
                        )
                    }
                }
            }
        )

        if (uiState.isLoadingHistory) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryTeal, strokeWidth = 3.dp)
            }
        } else if (uiState.historyRecords.isEmpty()) {
            EchoEmptyState(
                title = "No Verification Records",
                description = "You have not performed any voice verification checks yet. Recorded samples and scores will be logged here.",
                actionButtonText = "Back",
                onActionClick = onNavigateBack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Total Verifications: ${uiState.historyRecords.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(uiState.historyRecords) { record ->
                    VerificationHistoryItemCard(record = record)
                }

                item {
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }
    }

    if (showClearDialog) {
        EchoConfirmationDialog(
            title = "Clear Verification History?",
            message = "This permanently purges all historical verification audit logs. Underlying enrolled voice signatures and accounts are unaffected.",
            confirmText = "Purge Audit Log",
            isDestructive = true,
            icon = Icons.Default.Delete,
            onConfirm = {
                showClearDialog = false
                viewModel.clearVerificationHistory()
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun VerificationHistoryItemCard(record: VerificationHistoryRecord) {
    val (badgeColor, badgeIcon, label) = when (record.result) {
        VerificationOutcome.VOICE_MATCHED -> Triple(SuccessGreen, Icons.Default.CheckCircle, "MATCHED")
        VerificationOutcome.NO_MATCH -> Triple(DangerRed, Icons.Default.Warning, "NO MATCH")
        VerificationOutcome.INCONCLUSIVE -> Triple(WarningAmber, Icons.Default.HelpOutline, "INCONCLUSIVE")
        VerificationOutcome.AUDIO_QUALITY_FAILED -> Triple(WarningAmber, Icons.Default.GraphicEq, "QUALITY REJECTED")
        VerificationOutcome.PROFILE_NOT_ENROLLED -> Triple(InconclusiveGray, Icons.Default.Person, "UNENROLLED")
    }

    EchoCard(padding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoAvatar(name = record.memberName, size = 44.dp)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.memberName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${record.modelName} • ${record.verifiedAt.take(19).replace('T', ' ')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.similarityScore != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Similarity: ${String.format("%.4f", record.similarityScore)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = badgeColor
                    )
                }
            }
        }
    }
}
