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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoGhostButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.voice.audio.AudioRecordManager

@Composable
fun VoiceRecordingStudioScreen(
    currentSampleIndex: Int = 1,
    totalSamples: Int = 3,
    onRecordingCompleted: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory)
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    val recordManager = remember { AudioRecordManager(context) }
    val isRecording by recordManager.isRecording.collectAsState()
    val elapsedSeconds by recordManager.elapsedSeconds.collectAsState()
    val waveformBars by recordManager.waveformBars.collectAsState()
    val currentAmp by recordManager.currentAmplitude.collectAsState()

    val promptIndex = (uiState.currentSampleIndex - 1).coerceIn(0, viewModel.samplePrompts.size - 1)
    val currentPrompt = viewModel.samplePrompts[promptIndex]

    DisposableEffect(Unit) {
        onDispose {
            recordManager.cancelRecording()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Recording Studio",
            subtitle = "Sample ${uiState.currentSampleIndex} of $totalSamples",
            onNavigateBack = {
                recordManager.cancelRecording()
                onNavigateBack()
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { (uiState.currentSampleIndex - 1 + (elapsedSeconds / 7f).coerceAtMost(1f)) / totalSamples },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PrimaryTeal,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Speech Prompt Card
            EchoCard(padding = 22.dp) {
                Text(
                    text = "Acoustic Sample #${uiState.currentSampleIndex}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryTeal
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentPrompt,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Speak in a natural conversational tone in a quiet environment. Minimum duration: 2.5 seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Live Microphone Waveform Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        1.dp,
                        if (isRecording) PrimaryTeal.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        waveformBars.forEach { amplitude ->
                            val heightFraction = amplitude.coerceIn(0.08f, 1.0f)
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(90.dp * heightFraction)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (amplitude > 0.85f) DangerRed else PrimaryTeal
                                    )
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Microphone Ready • 16,000 Hz Mono PCM",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Timer & Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) DangerRed else SuccessGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "Recording: 00:0${elapsedSeconds}s" else "Ready to Record",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isRecording) DangerRed else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            if (!isRecording) {
                EchoPrimaryButton(
                    text = "Start Recording Sample",
                    onClick = {
                        val outputFile = java.io.File(
                            context.cacheDir,
                            "voice_sample_${uiState.currentSampleIndex}_${System.currentTimeMillis()}.wav"
                        )
                        recordManager.startRecording(outputFile, scope)
                    },
                    leadingIcon = Icons.Default.Mic,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                EchoPrimaryButton(
                    text = "Stop & Evaluate Sample (${elapsedSeconds}s)",
                    onClick = {
                        val wavFile = recordManager.stopRecording()
                        if (wavFile != null && wavFile.exists()) {
                            val quality = com.echoshield.voice.audio.AudioQualityValidator.validateWavFile(wavFile)
                            val prompt = viewModel.samplePrompts[promptIndex]
                            val sample = com.echoshield.domain.model.RecordedSample(
                                sampleIndex = uiState.currentSampleIndex,
                                file = wavFile,
                                prompt = prompt,
                                durationSeconds = quality.durationSeconds,
                                snrDb = quality.snrDb,
                                rmsEnergy = quality.rmsEnergy,
                                isValid = quality.isValid
                            )
                            viewModel.setSampleForReview(sample, quality.errorMessage)
                            onRecordingCompleted()
                        }
                    },
                    leadingIcon = Icons.Default.Stop,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            EchoGhostButton(
                text = "Cancel Recording",
                onClick = {
                    recordManager.cancelRecording()
                    onNavigateBack()
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
