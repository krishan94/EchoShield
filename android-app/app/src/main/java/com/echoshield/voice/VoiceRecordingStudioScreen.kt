package com.echoshield.voice

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoPulseRadar
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.core.components.EchoWaveformBars
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import kotlinx.coroutines.delay

@Composable
fun VoiceRecordingStudioScreen(
    currentSampleIndex: Int = 1,
    totalSamples: Int = 3,
    onRecordingCompleted: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0.0f) }

    val samplePrompts = listOf(
        "Read aloud: \"The quick brown fox jumps over the lazy dog near the riverbank on a calm afternoon.\"",
        "Read aloud: \"EchoShield provides encrypted biometric defense and anti-spoofing protection for my family circle.\"",
        "Read aloud: \"Always verify high-stakes financial requests and emergency alerts using our mutual voice signature.\""
    )

    val currentPrompt = samplePrompts[(currentSampleIndex - 1).coerceIn(0, samplePrompts.size - 1)]

    // Timer simulation
    LaunchedEffect(isRecording) {
        if (isRecording) {
            secondsElapsed = 0
            while (isRecording && secondsElapsed < 8) {
                delay(1000)
                secondsElapsed++
                progress = secondsElapsed / 8.0f
            }
            if (secondsElapsed >= 8) {
                isRecording = false
                onRecordingCompleted()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Recording Studio",
            subtitle = "Sample $currentSampleIndex of $totalSamples",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress Bar
            LinearProgressIndicator(
                progress = { currentSampleIndex.toFloat() / totalSamples.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PrimaryTeal,
                trackColor = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Sample Prompt Card
            EchoCard(padding = 16.dp) {
                Text(
                    text = "Acoustic Reference Passage",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                    color = PrimaryTeal
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = currentPrompt,
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Waveform & Visualizer Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                EchoWaveformBars(
                    barCount = 28,
                    isAnimating = isRecording,
                    color = if (isRecording) PrimaryTeal else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Audio Quality & Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) SuccessGreen else Color(0xFF94A3B8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) "Signal: Optimal (32dB SNR)" else "Microphone Ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = String.format("00:0%d / 00:08", secondsElapsed),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isRecording) PrimaryTeal else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Large Microphone Control Center
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    EchoPulseRadar(size = 160.dp, color = PrimaryTeal)
                }

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) DangerRed else PrimaryTeal)
                        .clickable {
                            if (isRecording) {
                                isRecording = false
                                onRecordingCompleted()
                            } else {
                                isRecording = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        tint = Color.Black,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isRecording) "Listening... Speak naturally" else "Tap microphone to begin sample",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // Start / Stop manual buttons
            EchoPrimaryButton(
                text = if (isRecording) "Stop Sample & Analyze" else "Start Voice Recording",
                onClick = {
                    if (isRecording) {
                        isRecording = false
                        onRecordingCompleted()
                    } else {
                        isRecording = true
                    }
                },
                leadingIcon = if (isRecording) Icons.Default.Stop else Icons.Default.Mic
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
