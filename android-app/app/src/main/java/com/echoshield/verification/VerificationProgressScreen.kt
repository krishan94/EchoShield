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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoPulseRadar
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import kotlinx.coroutines.delay

@Composable
fun VerificationProgressScreen(
    callId: String,
    onNavigateBack: () -> Unit,
    onViewFinalResults: (String) -> Unit
) {
    var audioProgress by remember { mutableFloatStateOf(0.4f) }
    var speakerProgress by remember { mutableFloatStateOf(0.3f) }
    var spoofProgress by remember { mutableFloatStateOf(0.2f) }

    LaunchedEffect(Unit) {
        while (audioProgress < 1.0f || speakerProgress < 1.0f || spoofProgress < 1.0f) {
            delay(400)
            audioProgress = (audioProgress + 0.15f).coerceAtMost(1.0f)
            speakerProgress = (speakerProgress + 0.12f).coerceAtMost(1.0f)
            spoofProgress = (spoofProgress + 0.10f).coerceAtMost(1.0f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Live Verification HUD",
            subtitle = "Call $callId • Continuous Pipeline",
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

            // Radar scanning hub
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                EchoPulseRadar(size = 130.dp, color = PrimaryTeal)
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Processing Live Audio Stream",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Analyzing remote audio buffer in 3-second non-overlapping chunks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(26.dp))

            // 1. Audio Quality Assessment
            PipelineStepCard(
                stepNumber = "STAGE 1",
                title = "Acoustic Quality & VAD",
                description = "Signal-to-Noise Ratio (31dB), Voice Activity Detection (Active)",
                progress = audioProgress,
                statusText = if (audioProgress >= 1f) "Optimal Audio Stream" else "Calibrating Room Noise...",
                icon = Icons.Default.GraphicEq
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Speaker Recognition Analysis
            PipelineStepCard(
                stepNumber = "STAGE 2",
                title = "SpeechBrain ECAPA-TDNN Analysis",
                description = "Extracting 192-dim vector & computing cosine distance against baseline",
                progress = speakerProgress,
                statusText = if (speakerProgress >= 1f) "Vector Match: 0.94 (High Confidence)" else "Extracting Latent Embedding...",
                icon = Icons.Default.Lock
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Anti-Spoofing & Deepfake Detection
            PipelineStepCard(
                stepNumber = "STAGE 3",
                title = "AASIST Neural Anti-Spoofing",
                description = "Scanning high-frequency spectral phase & neural vocoder signatures",
                progress = spoofProgress,
                statusText = if (spoofProgress >= 1f) "Bonafide Probability: 97% (No Spoof)" else "Evaluating Spectral Phase...",
                icon = Icons.Default.Assessment
            )

            Spacer(modifier = Modifier.height(30.dp))

            EchoPrimaryButton(
                text = "Inspect Full Forensic Verdict",
                onClick = { onViewFinalResults(callId) },
                enabled = spoofProgress >= 0.8f
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PipelineStepCard(
    stepNumber: String,
    title: String,
    description: String,
    progress: Float,
    statusText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val isComplete = progress >= 1.0f
    EchoCard(padding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isComplete) SuccessGreen else PrimaryTeal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryTeal
                )
            }
            if (isComplete) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Complete",
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = PrimaryTeal,
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isComplete) SuccessGreen else PrimaryTeal,
            trackColor = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isComplete) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

