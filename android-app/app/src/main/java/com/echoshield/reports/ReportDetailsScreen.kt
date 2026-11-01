package com.echoshield.reports

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.calling.CallingViewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoEmptyState
import com.echoshield.core.components.EchoSecondaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun ReportDetailsScreen(
    callId: String,
    onNavigateBack: () -> Unit,
    callingViewModel: CallingViewModel? = null
) {
    val callingUiState = callingViewModel?.uiState?.collectAsState()
    val realReport = callingUiState?.value?.postCallReport
    val callFromHistory = callingUiState?.value?.callHistory?.find { it.callId == callId }

    LaunchedEffect(callId) {
        callingViewModel?.loadCallReport(callId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Forensic Assessment",
            subtitle = "Call Audit Log #$callId",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (realReport != null && realReport.callId == callId) {
                // Real Call Report Display
                val outcomeColor = when (realReport.overallSecurityOutcome) {
                    CombinedSecurityOutcome.IDENTITY_CONFIRMED -> SuccessGreen
                    CombinedSecurityOutcome.SUSPICIOUS_AUDIO -> DangerRed
                    CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED -> DangerRed
                    CombinedSecurityOutcome.INCONCLUSIVE -> WarningAmber
                    CombinedSecurityOutcome.AUDIO_QUALITY_FAILED -> WarningAmber
                    else -> PrimaryTeal
                }

                // Caller Profile Header
                EchoCard(padding = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EchoAvatar(
                            name = realReport.calleeName,
                            size = 54.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = realReport.calleeName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "WebRTC Voice Call Participant",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(outcomeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = realReport.overallSecurityOutcome.name.replace("_", " "),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = outcomeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Risk Level",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = realReport.riskLevel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (realReport.isThreatDetected) DangerRed else SuccessGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val m = realReport.durationSeconds / 60
                            val s = realReport.durationSeconds % 60
                            Text(
                                text = String.format("%02d:%02d", m, s),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Security Verdict Card
                EchoCard(
                    borderColor = outcomeColor.copy(alpha = 0.4f),
                    padding = 18.dp
                ) {
                    Text(
                        text = realReport.securityVerdictHeadline,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = outcomeColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = realReport.securityExplanation,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Analyzed ${realReport.analysesCount} remote audio window(s) during session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Key Findings Card
                if (realReport.keyFindings.isNotEmpty()) {
                    EchoCard(padding = 18.dp) {
                        Text(
                            text = "Key Audit Findings",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        realReport.keyFindings.forEach { finding ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    color = PrimaryTeal,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = finding,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Recommendations Card
                if (realReport.recommendations.isNotEmpty()) {
                    EchoCard(
                        borderColor = if (realReport.isThreatDetected) DangerRed.copy(alpha = 0.5f) else PrimaryTeal.copy(alpha = 0.4f),
                        padding = 18.dp
                    ) {
                        Text(
                            text = "Security Recommendations",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (realReport.isThreatDetected) DangerRed else PrimaryTeal
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        realReport.recommendations.forEach { rec ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "✓ ",
                                    color = if (realReport.isThreatDetected) DangerRed else SuccessGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = rec,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Disclaimer Card
                EchoCard(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    padding = 14.dp
                ) {
                    Text(
                        text = "Forensic Disclaimer",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = realReport.disclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            } else if (callFromHistory != null) {
                // Call Summary from History
                val outcome = callFromHistory.securityOutcome ?: CombinedSecurityOutcome.IDENTITY_CONFIRMED
                val outcomeColor = when (outcome) {
                    CombinedSecurityOutcome.IDENTITY_CONFIRMED -> SuccessGreen
                    CombinedSecurityOutcome.SUSPICIOUS_AUDIO -> DangerRed
                    CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED -> DangerRed
                    CombinedSecurityOutcome.INCONCLUSIVE -> WarningAmber
                    CombinedSecurityOutcome.AUDIO_QUALITY_FAILED -> WarningAmber
                    else -> PrimaryTeal
                }

                EchoCard(padding = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EchoAvatar(
                            name = callFromHistory.otherPartyName,
                            size = 54.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = callFromHistory.otherPartyName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${callFromHistory.direction.replaceFirstChar { it.uppercase() }} Voice Call",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(outcomeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = outcome.name.replace("_", " "),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = outcomeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = callFromHistory.status.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val m = callFromHistory.durationSeconds / 60
                            val s = callFromHistory.durationSeconds % 60
                            Text(
                                text = String.format("%02d:%02d", m, s),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                EchoCard(
                    borderColor = outcomeColor.copy(alpha = 0.4f),
                    padding = 18.dp
                ) {
                    Text(
                        text = "Call Log Recorded",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = outcomeColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This call session was processed by EchoShield WebRTC audio pipeline. Detailed multi-sample acoustic telemetry has been archived.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

            } else {
                // Empty / Not Found State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, bottom = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EchoEmptyState(
                        title = "No Forensic Report Found",
                        description = "The security audit report for Call #$callId is not available yet or could not be loaded.",
                        icon = Icons.Default.Assessment,
                        actionButtonText = "Return to Call Logs",
                        onActionClick = onNavigateBack
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            EchoSecondaryButton(
                text = "Back to Call Logs",
                onClick = onNavigateBack,
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
