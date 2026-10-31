package com.echoshield.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoSecondaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.core.components.EchoVerdict
import com.echoshield.core.components.EchoVerificationBadge
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.DangerRed
import com.echoshield.theme.InconclusiveGray
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun VerificationResultsScreen(
    callId: String,
    onNavigateBack: () -> Unit,
    onViewFullForensicReport: (String) -> Unit
) {
    var selectedVerdict by remember { mutableStateOf(EchoVerdict.VOICE_MATCHED) }

    // Dynamic metrics based on selected verdict
    val (similarityScore, spoofScore) = when (selectedVerdict) {
        EchoVerdict.VOICE_MATCHED -> Pair(0.94f, 0.03f)
        EchoVerdict.IDENTITY_MISMATCH -> Pair(0.56f, 0.12f)
        EchoVerdict.SUSPICIOUS_SPOOF -> Pair(0.42f, 0.89f)
        EchoVerdict.INCONCLUSIVE -> Pair(0.20f, 0.18f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Verification Verdict",
            subtitle = "Call $callId • Forensic Results",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive State Switcher for UX Inspection & Testing
            EchoCard(padding = 12.dp) {
                Text(
                    text = "Interactive Verdict Switcher",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                    color = PrimaryTeal
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VerdictSelectorChip("Matched", EchoVerdict.VOICE_MATCHED, selectedVerdict) { selectedVerdict = it }
                    VerdictSelectorChip("Mismatch", EchoVerdict.IDENTITY_MISMATCH, selectedVerdict) { selectedVerdict = it }
                    VerdictSelectorChip("Spoof", EchoVerdict.SUSPICIOUS_SPOOF, selectedVerdict) { selectedVerdict = it }
                    VerdictSelectorChip("Inconclusive", EchoVerdict.INCONCLUSIVE, selectedVerdict) { selectedVerdict = it }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large Hero Verdict Badge
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(selectedVerdict.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = selectedVerdict.icon,
                    contentDescription = null,
                    tint = selectedVerdict.color,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = selectedVerdict.title,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = selectedVerdict.color
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = selectedVerdict.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Separate Speaker Similarity Score Card
            EchoCard(padding = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Speaker Similarity (ECAPA-TDNN)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${(similarityScore * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (similarityScore >= 0.75f) SuccessGreen else WarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { similarityScore },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (similarityScore >= 0.75f) SuccessGreen else WarningAmber,
                    trackColor = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Threshold: $\\ge 75\\%$ for positive family match. Measured against enrolled 192-dim biometric baseline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Separate Anti-Spoofing Assessment Card
            EchoCard(padding = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (spoofScore > 0.5f) DangerRed else SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Spoof / Synthetic Risk Assessment",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${(spoofScore * 100).toInt()}% Risk",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (spoofScore > 0.5f) DangerRed else SuccessGreen
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { spoofScore },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (spoofScore > 0.5f) DangerRed else SuccessGreen,
                    trackColor = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (spoofScore > 0.5f) {
                        "High anomaly score. Acoustic indicators strongly suggest generative Text-to-Speech (TTS) or Voice Conversion (VC)."
                    } else {
                        "Normal acoustic distribution. Spectral phase coherence indicates bonafide human vocal tract production."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            EchoPrimaryButton(
                text = "View Detailed Forensic Report",
                onClick = { onViewFullForensicReport(callId) },
                leadingIcon = Icons.Default.Description
            )

            Spacer(modifier = Modifier.height(14.dp))

            EchoSecondaryButton(
                text = "Return to Call Log",
                onClick = onNavigateBack
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VerdictSelectorChip(
    label: String,
    verdict: EchoVerdict,
    current: EchoVerdict,
    onSelect: (EchoVerdict) -> Unit
) {
    val isSelected = current == verdict
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) verdict.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (isSelected) verdict.color else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onSelect(verdict) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) verdict.color else MaterialTheme.colorScheme.onSurface
        )
    }
}
