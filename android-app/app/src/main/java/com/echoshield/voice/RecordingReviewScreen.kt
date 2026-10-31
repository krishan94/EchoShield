package com.echoshield.voice

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoDangerButton
import com.echoshield.core.components.EchoOutlinedButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.core.components.EchoWaveformBars
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber

@Composable
fun RecordingReviewScreen(
    sampleNumber: Int = 1,
    onAcceptSample: () -> Unit,
    onRecordAgain: () -> Unit,
    onDeleteSample: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val playbackManager = viewModel.playbackManager
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val playbackProgress by playbackManager.playbackProgress.collectAsState()

    val sample = uiState.currentReviewSample

    DisposableEffect(Unit) {
        onDispose {
            playbackManager.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Review Sample ${sample?.sampleIndex ?: sampleNumber}",
            subtitle = "Biometric Quality Evaluation",
            onNavigateBack = {
                playbackManager.stop()
                onNavigateBack()
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

            // Audio Player Card
            EchoCard(padding = 20.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause Circle Button
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(PrimaryTeal)
                            .clickable {
                                if (sample?.file != null && sample.file.exists()) {
                                    if (isPlaying) {
                                        playbackManager.pause()
                                    } else {
                                        playbackManager.playAudio(sample.file, scope)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sample #${sample?.sampleIndex ?: sampleNumber} (16kHz Mono)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isPlaying) "Playing back raw capture..." else "Duration: ${"%.1f".format(sample?.durationSeconds ?: 0f)} seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryTeal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback progress bar
                LinearProgressIndicator(
                    progress = { playbackProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = PrimaryTeal,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EchoWaveformBars(
                        barCount = 32,
                        isAnimating = isPlaying,
                        color = PrimaryTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Acoustic Quality Assessment Card
            EchoCard(padding = 18.dp) {
                val isValid = sample?.isValid ?: false
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isValid) Icons.Default.VerifiedUser else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isValid) SuccessGreen else WarningAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isValid) "Acoustic Sample Validated" else "Quality Warning",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Signal-to-Noise Ratio (SNR)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${sample?.snrDb?.toInt() ?: 0} dB (${if ((sample?.snrDb ?: 0f) > 20f) "Clean" else "Moderate"})",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if ((sample?.snrDb ?: 0f) > 20f) SuccessGreen else WarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ECAPA-TDNN Compatibility",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isValid) "16kHz PCM Verified" else "Check Failed",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isValid) PrimaryTeal else DangerRed
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sample Progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${sample?.sampleIndex ?: sampleNumber} of ${uiState.totalSamples} Completed",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            val isFinalSample = (sample?.sampleIndex ?: sampleNumber) >= uiState.totalSamples
            val buttonText = when {
                uiState.isUploading -> "Extracting & Encrypting Embeddings..."
                isFinalSample -> "Submit 3 Samples & Finalize Voice Profile"
                else -> "Accept Sample & Record Next (${(sample?.sampleIndex ?: sampleNumber) + 1}/3)"
            }

            EchoPrimaryButton(
                text = buttonText,
                onClick = {
                    playbackManager.stop()
                    viewModel.acceptCurrentSample(
                        onProceedToNext = onAcceptSample,
                        onAllSamplesCompleted = onAcceptSample
                    )
                },
                enabled = sample?.isValid == true && !uiState.isUploading,
                isLoading = uiState.isUploading,
                leadingIcon = Icons.Default.Check,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            EchoOutlinedButton(
                text = "Re-record This Sample",
                onClick = {
                    playbackManager.stop()
                    viewModel.rerecordCurrentSample()
                    onRecordAgain()
                },
                leadingIcon = Icons.Default.Replay,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            EchoDangerButton(
                text = "Discard & Delete Sample",
                onClick = {
                    playbackManager.stop()
                    viewModel.discardCurrentSample {
                        onDeleteSample()
                    }
                },
                leadingIcon = Icons.Default.Delete,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
