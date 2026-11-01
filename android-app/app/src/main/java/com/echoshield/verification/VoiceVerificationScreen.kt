package com.echoshield.verification

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoOutlinedButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoPulseRadar
import com.echoshield.core.components.EchoSecondaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.VerificationOutcome
import com.echoshield.domain.model.VoiceVerificationResult
import com.echoshield.domain.model.AntiSpoofOutcome
import com.echoshield.domain.model.AntiSpoofAnalysisResult
import com.echoshield.domain.model.CombinedSecurityAnalysisResult
import com.echoshield.domain.model.CombinedSecurityOutcome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonAdd
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.DangerRed
import com.echoshield.theme.InconclusiveGray
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber
import com.echoshield.voice.audio.AudioPlaybackManager
import com.echoshield.voice.audio.AudioRecordManager
import java.io.File

@Composable
fun VoiceVerificationScreen(
    preselectedMemberId: String? = null,
    onNavigateBack: () -> Unit,
    onViewHistory: () -> Unit,
    viewModel: VoiceVerificationViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Audio Engine managers
    val recordManager = remember { AudioRecordManager(context) }
    val playbackManager = remember { AudioPlaybackManager() }

    val isRecording by recordManager.isRecording.collectAsState()
    val elapsedSeconds by recordManager.elapsedSeconds.collectAsState()
    val waveformBars by recordManager.waveformBars.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val playbackProgress by playbackManager.playbackProgress.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(preselectedMemberId) {
        if (preselectedMemberId != null) {
            viewModel.loadFamilyMembers(preselectedMemberId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recordManager.cancelRecording()
            playbackManager.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Voice Verification",
            subtitle = "AI Speaker Recognition",
            onNavigateBack = onNavigateBack,
            actions = {
                IconButton(onClick = onViewHistory) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Verification History",
                        tint = PrimaryTeal
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

            // 1. Error Banner
            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed.copy(alpha = 0.12f))
                        .border(1.dp, DangerRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = DangerRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 2. Family Member Selector
            Text(
                text = "Select Family Member to Verify",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.isLoadingMembers) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PrimaryTeal, strokeWidth = 2.dp)
            } else if (uiState.members.isEmpty()) {
                EchoCard(padding = 16.dp) {
                    Text(
                        text = "No family members found in your circle. Add a family member first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.members) { member ->
                        val isSelected = uiState.selectedMember?.id == member.id || uiState.selectedMember?.memberId == member.memberId
                        FamilyMemberSelectionCard(
                            member = member,
                            isSelected = isSelected,
                            onClick = { viewModel.selectMember(member) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Selected Member Enrollment Status Pill
            uiState.selectedMember?.let { member ->
                EnrollmentStatusPill(member = member)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Main State Content Area: Result vs Verifying vs Studio
            when {
                uiState.isVerifying || uiState.isAnalyzingSpoof || uiState.isAnalyzingSecurity -> {
                    // AI Inference Loading Radar
                    VerificationLoadingCard()
                }

                uiState.combinedSecurityResult != null -> {
                    // Phase 7: Combined Security Decision Engine Result
                    CombinedSecurityResultCard(
                        result = uiState.combinedSecurityResult!!,
                        selectedMember = uiState.selectedMember,
                        onVerifyAnother = { viewModel.resetVerification() },
                        onRetry = { viewModel.analyzeSecurity() }
                    )
                }

                uiState.verificationResult != null || uiState.antiSpoofResult != null -> {
                    // Dual Result View (Independent Presentation)
                    CombinedResultsView(
                        verificationResult = uiState.verificationResult,
                        antiSpoofResult = uiState.antiSpoofResult,
                        selectedMember = uiState.selectedMember,
                        onVerifyAnother = { viewModel.resetVerification() }
                    )
                }

                else -> {
                    // Studio Audio Recording & Review Area
                    RecordingStudioCard(
                        hasPermission = hasPermission,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        isRecording = isRecording,
                        elapsedSeconds = elapsedSeconds,
                        waveformBars = waveformBars,
                        hasRecordedSample = uiState.hasRecordedSample,
                        isPlaying = isPlaying,
                        playbackProgress = playbackProgress,
                        isMemberEnrolled = uiState.selectedMember?.isVoiceEnrolled ?: false,
                        onStartRecording = {
                            val verificationDir = File(context.filesDir, "pending_verification").apply { mkdirs() }
                            val audioFile = File(verificationDir, "verify_${System.currentTimeMillis()}.wav")
                            recordManager.startRecording(audioFile, scope)
                        },
                        onStopRecording = {
                            scope.launch {
                                val recordedFile = recordManager.stopRecording()
                                if (recordedFile != null) {
                                    viewModel.setRecordedAudio(recordedFile)
                                }
                            }
                        },
                        onCancelRecording = {
                            recordManager.cancelRecording()
                            viewModel.clearRecordedAudio()
                        },
                        onPlayReview = {
                            uiState.recordedFile?.let { playbackManager.playAudio(it, scope) }
                        },
                        onStopReview = {
                            playbackManager.stop()
                        },
                        onResetSample = {
                            playbackManager.stop()
                            viewModel.clearRecordedAudio()
                        },
                        onVerifySample = {
                            playbackManager.stop()
                            viewModel.verifyVoiceSample()
                        },
                        onAnalyzeSpoof = {
                            playbackManager.stop()
                            viewModel.analyzeSpoofSample()
                        },
                        onVerifyBoth = {
                            playbackManager.stop()
                            viewModel.verifyVoiceWithAntiSpoof()
                        },
                        onAnalyzeSecurity = {
                            playbackManager.stop()
                            viewModel.analyzeSecurity()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy & Architecture Disclaimer Card
            EchoCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Privacy & Biometric Architecture",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Voice verification compares a newly extracted 192-dim ECAPA-TDNN vector against the authorized family member's AES-256-GCM encrypted baseline. Audio is wiped immediately after inference. Raw recordings are never stored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyMemberSelectionCard(
    member: FamilyMember,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) PrimaryTeal else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (isSelected) PrimaryTeal.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .width(135.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EchoAvatar(name = member.name, size = 44.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = member.relationship,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (member.isVoiceEnrolled) SuccessGreen.copy(alpha = 0.15f)
                        else InconclusiveGray.copy(alpha = 0.2f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (member.isVoiceEnrolled) "Enrolled" else "Not Enrolled",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (member.isVoiceEnrolled) SuccessGreen else InconclusiveGray
                )
            }
        }
    }
}

@Composable
private fun EnrollmentStatusPill(member: FamilyMember) {
    val isEnrolled = member.isVoiceEnrolled
    val pillBg = if (isEnrolled) SuccessGreen.copy(alpha = 0.12f) else WarningAmber.copy(alpha = 0.12f)
    val pillBorder = if (isEnrolled) SuccessGreen.copy(alpha = 0.3f) else WarningAmber.copy(alpha = 0.3f)
    val pillColor = if (isEnrolled) SuccessGreen else WarningAmber

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pillBg)
            .border(1.dp, pillBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isEnrolled) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = pillColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isEnrolled) "${member.name} is Biometrically Enrolled" else "${member.name} is NOT Enrolled",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = pillColor
                )
                Text(
                    text = if (isEnrolled)
                        "Voice profile active (${(member.voiceQualityScore * 100).toInt()}% baseline quality). Ready for verification."
                    else
                        "Voice matching requires an enrolled profile. ${member.name} must complete 3-sample voice enrollment first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VerificationLoadingCard() {
    EchoCard(padding = 24.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                EchoPulseRadar(size = 110.dp, color = PrimaryTeal)
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    color = PrimaryTeal,
                    strokeWidth = 3.dp
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Analyzing Voice Identity",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PrimaryTeal
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Extracting 192-dim ECAPA-TDNN embedding vector and computing cosine similarity against encrypted baseline...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecordingStudioCard(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    isRecording: Boolean,
    elapsedSeconds: Int,
    waveformBars: List<Float>,
    hasRecordedSample: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    isMemberEnrolled: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayReview: () -> Unit,
    onStopReview: () -> Unit,
    onResetSample: () -> Unit,
    onVerifySample: () -> Unit,
    onAnalyzeSpoof: () -> Unit,
    onVerifyBoth: () -> Unit,
    onAnalyzeSecurity: () -> Unit
) {
    EchoCard(padding = 20.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasPermission) {
                // Request Permission UI
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Microphone Access Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "EchoShield needs microphone permission to capture audio for genuine ECAPA-TDNN speaker recognition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                EchoPrimaryButton(
                    text = "Grant Microphone Permission",
                    onClick = onRequestPermission,
                    leadingIcon = Icons.Default.Mic
                )
            } else if (!hasRecordedSample) {
                // Active Recording Studio
                Text(
                    text = if (isRecording) "Recording Voice Sample..." else "Speech Recording Studio",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isRecording) PrimaryTeal else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isRecording)
                        "Speak naturally: 'Hello, this is my voice confirmation for EchoShield.'"
                    else
                        "Press the record button and speak a natural conversational sentence (minimum 2.5s).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Waveform Visualizer (Real amplitude-driven)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    waveformBars.forEach { amp ->
                        val barHeight = (amp * 50).dp.coerceIn(4.dp, 52.dp)
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isRecording) PrimaryTeal else PrimaryTeal.copy(alpha = 0.3f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real Timer
                Text(
                    text = formatTimer(elapsedSeconds),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = if (isRecording) PrimaryTeal else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EchoSecondaryButton(
                            text = "Cancel",
                            onClick = onCancelRecording,
                            modifier = Modifier.weight(1f)
                        )
                        EchoPrimaryButton(
                            text = "Stop Recording",
                            onClick = onStopRecording,
                            leadingIcon = Icons.Default.Stop,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    EchoPrimaryButton(
                        text = "Start Recording",
                        onClick = onStartRecording,
                        leadingIcon = Icons.Default.Mic,
                        enabled = isMemberEnrolled
                    )
                    if (!isMemberEnrolled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select an enrolled family member to enable verification.",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
            } else {
                // Audio Review & Verification Submission Area
                Text(
                    text = "Review Recorded Sample",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Listen to your recording to ensure speech clarity and quiet background before verifying.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Playback progress bar
                LinearProgressIndicator(
                    progress = { playbackProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PrimaryTeal,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EchoSecondaryButton(
                        text = if (isPlaying) "Stop" else "Listen",
                        onClick = { if (isPlaying) onStopReview() else onPlayReview() },
                        leadingIcon = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        modifier = Modifier.weight(1f)
                    )
                    EchoOutlinedButton(
                        text = "Record Again",
                        onClick = onResetSample,
                        leadingIcon = Icons.Default.Refresh,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Action: Combined Security Decision Engine
                EchoPrimaryButton(
                    text = "Combined Security Analysis",
                    onClick = onAnalyzeSecurity,
                    leadingIcon = Icons.Default.Security,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EchoSecondaryButton(
                        text = "Deepfake Only",
                        onClick = onAnalyzeSpoof,
                        leadingIcon = Icons.Default.GraphicEq,
                        modifier = Modifier.weight(1f)
                    )
                    EchoOutlinedButton(
                        text = "Identity Only",
                        onClick = onVerifySample,
                        leadingIcon = Icons.Default.Person,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationResultCard(
    result: VoiceVerificationResult,
    selectedMember: FamilyMember?,
    onVerifyAnother: (() -> Unit)? = null
) {
    val (badgeColor, badgeIcon, titleText, subtitleText) = when (result.result) {
        VerificationOutcome.VOICE_MATCHED -> Tuple4(
            SuccessGreen,
            Icons.Default.VerifiedUser,
            "Voice Matched",
            "Acoustic signature matches enrolled biometric profile"
        )
        VerificationOutcome.NO_MATCH -> Tuple4(
            DangerRed,
            Icons.Default.Warning,
            "No Voice Match",
            "Acoustic features diverge from enrolled baseline"
        )
        VerificationOutcome.INCONCLUSIVE -> Tuple4(
            WarningAmber,
            Icons.Default.HelpOutline,
            "Inconclusive Result",
            "Biometric evidence falls in the uncertain operating region"
        )
        VerificationOutcome.AUDIO_QUALITY_FAILED -> Tuple4(
            WarningAmber,
            Icons.Default.GraphicEq,
            "Audio Quality Failed",
            "Acoustic validation criteria not satisfied"
        )
        VerificationOutcome.PROFILE_NOT_ENROLLED -> Tuple4(
            InconclusiveGray,
            Icons.Default.Person,
            "Profile Not Enrolled",
            "Selected member has not registered a voice signature"
        )
    }

    EchoCard(padding = 22.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Verdict Icon Hub
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = badgeColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Similarity Score Breakdown Card (if available)
            if (result.similarityScore != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cosine Similarity (ECAPA-TDNN):",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = String.format("%.4f", result.similarityScore),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress representation (clamped 0f..1f)
                        val progress = result.similarityScore.coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = badgeColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Non-match boundary: < ${result.nonMatchThreshold}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Match threshold: ≥ ${result.matchThreshold}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryTeal
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Responsible Explanation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Text(
                    text = result.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Forensic Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Evaluated Member:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = result.memberName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Acoustic Model:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${result.modelName} (${result.modelVersion})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (onVerifyAnother != null) {
                EchoPrimaryButton(
                    text = "Verify Another Sample",
                    onClick = onVerifyAnother,
                    leadingIcon = Icons.Default.Refresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)



@Composable
private fun CombinedSecurityResultCard(
    result: CombinedSecurityAnalysisResult,
    selectedMember: FamilyMember?,
    onVerifyAnother: () -> Unit,
    onRetry: () -> Unit
) {
    val (badgeColor, badgeIcon, titleText, subtitleText) = when (result.combinedDecision) {
        CombinedSecurityOutcome.IDENTITY_CONFIRMED -> Tuple4(
            SuccessGreen,
            Icons.Default.VerifiedUser,
            "Voice Identity Confirmed",
            "Speaker Matched & No Spoofing Signal"
        )
        CombinedSecurityOutcome.SUSPICIOUS_AUDIO -> Tuple4(
            DangerRed,
            Icons.Default.Warning,
            "Suspicious Audio Detected",
            "Potential AI Voice Clone / Deepfake"
        )
        CombinedSecurityOutcome.IDENTITY_NOT_CONFIRMED -> Tuple4(
            InconclusiveGray,
            Icons.Default.Close,
            "Identity Not Confirmed",
            "Speaker Voice Does Not Match Enrolled Profile"
        )
        CombinedSecurityOutcome.INCONCLUSIVE -> Tuple4(
            WarningAmber,
            Icons.Default.HelpOutline,
            "Security Evaluation Inconclusive",
            "Insufficient Evidence to Authorize Voice"
        )
        CombinedSecurityOutcome.ANALYSIS_UNAVAILABLE -> Tuple4(
            DangerRed,
            Icons.Default.ErrorOutline,
            "Security Analysis Unavailable",
            "AI Model / Verification Service Offline"
        )
        CombinedSecurityOutcome.AUDIO_QUALITY_FAILED -> Tuple4(
            WarningAmber,
            Icons.Default.GraphicEq,
            "Audio Quality Insufficient",
            "Acoustic Criteria Not Met"
        )
        CombinedSecurityOutcome.PROFILE_NOT_ENROLLED -> Tuple4(
            AccentBlue,
            Icons.Default.PersonAdd,
            "Voice Profile Not Enrolled",
            "Target Member Must Complete Enrollment"
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Primary Verdict Card ---
        EchoCard(padding = 20.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(2.dp, badgeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = badgeColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Detailed synthesized explanation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    Text(
                        text = result.explanation,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Contributing Factors
                if (result.decisionFactors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Decision Factors:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        result.decisionFactors.forEach { factor ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = badgeColor
                                )
                                Text(
                                    text = factor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. Dual Inspection Breakdown Cards ---
        EchoCard(padding = 18.dp) {
            Text(
                text = "Dual-Engine Forensic Breakdown",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Sub-Panel A: Speaker Identity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phase 5: Speaker Recognition",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                    Text(
                        text = result.speakerVerification?.result ?: "NOT_EVALUATED",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val sim = result.speakerVerification?.similarityScore
                    Text(
                        text = if (sim != null) "Similarity: ${"%.2f".format(sim)} (Threshold: ${result.speakerVerification?.matchThreshold ?: 0.72f})" else "No score generated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-Panel B: AI Anti-Spoofing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phase 6: AI Deepfake Detection",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentBlue
                    )
                    Text(
                        text = result.antiSpoofing?.resultCategory ?: "NOT_EVALUATED",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val spoofScore = result.antiSpoofing?.spoofScore
                    Text(
                        text = if (spoofScore != null) "Spoof Probability: ${(spoofScore * 100).toInt()}% • ${result.antiSpoofing?.scoreInterpretation ?: ""}" else "No score generated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. Acoustic Screening Card ---
        EchoCard(padding = 16.dp) {
            Text(
                text = "Acoustic Screening Metrics",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Duration: ${"%.1f".format(result.audioQuality.durationSeconds)}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "SNR: ${result.audioQuality.snrDb.toInt()} dB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Quality: ${result.audioQuality.statusLabel}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (result.audioQuality.isAcceptable) SuccessGreen else DangerRed
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. Forensic Limitations & Disclaimers ---
        EchoCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Forensic Safeguards & Disclaimers",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = WarningAmber
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    result.limitations.forEach { limitation ->
                        Text(
                            text = "• $limitation",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.disclaimer,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 5. Action Buttons ---
        EchoPrimaryButton(
            text = "Verify Another Sample",
            onClick = onVerifyAnother,
            leadingIcon = Icons.Default.Refresh,
            modifier = Modifier.fillMaxWidth()
        )

        if (result.combinedDecision in listOf(
            CombinedSecurityOutcome.INCONCLUSIVE,
            CombinedSecurityOutcome.AUDIO_QUALITY_FAILED,
            CombinedSecurityOutcome.ANALYSIS_UNAVAILABLE
        )) {
            Spacer(modifier = Modifier.height(10.dp))
            EchoOutlinedButton(
                text = "Retry Analysis",
                onClick = onRetry,
                leadingIcon = Icons.Default.Refresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CombinedResultsView(
    verificationResult: VoiceVerificationResult?,
    antiSpoofResult: AntiSpoofAnalysisResult?,
    selectedMember: FamilyMember?,
    onVerifyAnother: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Independence Safeguard Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AccentBlue.copy(alpha = 0.12f))
                .border(1.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Independent AI Security Assessments",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentBlue
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Speaker similarity (identity verification) and anti-spoofing (deepfake detection) answer separate questions. High speaker similarity does NOT prove the audio is genuine. Synthetic voice clones can mimic acoustic identity while exhibiting vocoder artifacts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: Phase 5 Speaker Identity Verification
        if (verificationResult != null) {
            VerificationResultCard(
                result = verificationResult,
                selectedMember = selectedMember,
                onVerifyAnother = if (antiSpoofResult == null) onVerifyAnother else null
            )
        }

        if (verificationResult != null && antiSpoofResult != null) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Card 2: Phase 6 AI Anti-Spoofing & Deepfake Detection
        if (antiSpoofResult != null) {
            AntiSpoofResultCard(
                result = antiSpoofResult,
                onVerifyAnother = onVerifyAnother
            )
        }
    }
}

@Composable
private fun AntiSpoofResultCard(
    result: AntiSpoofAnalysisResult,
    onVerifyAnother: (() -> Unit)? = null
) {
    val (badgeColor, badgeIcon, titleText, subtitleText) = when (result.resultCategory) {
        AntiSpoofOutcome.SPOOF_SUSPECTED -> Tuple4(
            DangerRed,
            Icons.Default.Warning,
            "Spoof Suspected",
            "Acoustic indicators suggest AI speech synthesis or voice conversion"
        )
        AntiSpoofOutcome.NO_SPOOF_SIGNAL_DETECTED -> Tuple4(
            SuccessGreen,
            Icons.Default.CheckCircle,
            "No Spoof Signal Detected",
            "Absence of synthetic vocoder cues under current criteria (does NOT prove authentic)"
        )
        AntiSpoofOutcome.INCONCLUSIVE -> Tuple4(
            WarningAmber,
            Icons.Default.HelpOutline,
            "Inconclusive Result",
            result.scoreInterpretation
        )
        AntiSpoofOutcome.AUDIO_QUALITY_FAILED -> Tuple4(
            WarningAmber,
            Icons.Default.GraphicEq,
            "Audio Quality Failed",
            result.audioQuality.errorMessage ?: "Audio did not satisfy acoustic requirements"
        )
        AntiSpoofOutcome.MODEL_UNAVAILABLE -> Tuple4(
            InconclusiveGray,
            Icons.Default.Info,
            "Detector Unavailable",
            "Anti-spoofing engine is currently offline or unreachable"
        )
    }

    EchoCard(padding = 20.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anti-Spoofing & Deepfake Detection",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor
                    )
                }
                if (result.isExperimental) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(WarningAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "EXPERIMENTAL",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = WarningAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Spoof Probability Metric
            if (result.spoofScore != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Synthetic Risk Probability:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(result.spoofScore * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val progress = result.spoofScore.coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = badgeColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Clean floor: < 35%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Suspicion ceiling: ≥ 65%",
                                style = MaterialTheme.typography.labelSmall,
                                color = DangerRed
                            )
                        }
                    }
                }
            }

            // Explanation
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Text(
                    text = result.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Replay Limitation Warning Box
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(InconclusiveGray.copy(alpha = 0.12f))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.replayLimitationNotice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Metadata footer
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Acoustic Detector:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = result.modelName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sample Duration / SNR:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${result.audioQuality.durationSeconds}s • ${result.audioQuality.snrDb} dB SNR",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (onVerifyAnother != null) {
                Spacer(modifier = Modifier.height(16.dp))
                EchoPrimaryButton(
                    text = "Verify Another Sample",
                    onClick = onVerifyAnother,
                    leadingIcon = Icons.Default.Refresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
