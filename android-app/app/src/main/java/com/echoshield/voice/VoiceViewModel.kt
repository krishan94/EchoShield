package com.echoshield.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echoshield.core.di.AppContainer
import com.echoshield.domain.model.RecordedSample
import com.echoshield.domain.model.VoiceProfileInfo
import com.echoshield.domain.repository.VoiceRepository
import com.echoshield.voice.audio.AudioPlaybackManager
import com.echoshield.voice.audio.AudioQualityValidator
import com.echoshield.voice.audio.AudioRecordManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VoiceUiState(
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val currentSampleIndex: Int = 1,
    val totalSamples: Int = 3,
    val recordedSamples: Map<Int, RecordedSample> = emptyMap(),
    val currentReviewSample: RecordedSample? = null,
    val voiceProfile: VoiceProfileInfo? = null,
    val isConsentGranted: Boolean = false,
    val isMicrophonePermissionGranted: Boolean = false,
    val isPermissionPermanentlyDenied: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * ViewModel managing the guided 12-step voice enrollment state machine.
 */
class VoiceViewModel(
    private val voiceRepository: VoiceRepository,
    val audioRecordManager: AudioRecordManager? = null,
    val playbackManager: AudioPlaybackManager = AudioPlaybackManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    val samplePrompts = listOf(
        "Read aloud: \"The quick brown fox jumps over the lazy dog near the riverbank on a calm afternoon.\"",
        "Read aloud: \"EchoShield provides encrypted biometric defense and anti-spoofing protection for my family circle.\"",
        "Read aloud: \"Always verify high-stakes financial requests and emergency alerts using our mutual voice signature.\""
    )

    init {
        viewModelScope.launch {
            voiceRepository.voiceProfile.collect { profile ->
                _uiState.update {
                    it.copy(
                        voiceProfile = profile,
                        isConsentGranted = (profile?.consentStatus == "granted")
                    )
                }
            }
        }
        viewModelScope.launch {
            voiceRepository.isConsentGranted.collect { granted ->
                _uiState.update { it.copy(isConsentGranted = granted) }
            }
        }
        refreshVoiceProfile()
    }

    fun refreshVoiceProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            voiceRepository.refreshVoiceProfile()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun recordConsent(granted: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = voiceRepository.recordConsent(granted)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isConsentGranted = granted) }
                if (granted) onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "Failed to record biometric consent.")
                }
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update {
            it.copy(
                isMicrophonePermissionGranted = granted,
                isPermissionPermanentlyDenied = false
            )
        }
    }

    fun setPermissionPermanentlyDenied(denied: Boolean) {
        _uiState.update {
            it.copy(
                isMicrophonePermissionGranted = false,
                isPermissionPermanentlyDenied = denied
            )
        }
    }

    fun startRecording(context: Context) {
        val sampleIndex = _uiState.value.currentSampleIndex
        val enrollmentDir = File(context.filesDir, "pending_enrollment").apply { mkdirs() }
        val sampleFile = File(enrollmentDir, "voice_sample_${sampleIndex}_${System.currentTimeMillis()}.wav")

        if (audioRecordManager != null) {
            audioRecordManager.startRecording(sampleFile, viewModelScope)
        }
    }

    fun stopRecording(onSampleCaptured: (RecordedSample) -> Unit) {
        viewModelScope.launch {
            val file = audioRecordManager?.stopRecording()
            if (file == null || !file.exists() || file.length() <= 44) {
                _uiState.update { it.copy(errorMessage = "Recording failed or produced empty audio. Please try again.") }
                return@launch
            }

            // Validate audio quality immediately on client
            val qualityReport = AudioQualityValidator.validateWavFile(file)
            val sampleIndex = _uiState.value.currentSampleIndex
            val prompt = samplePrompts[(sampleIndex - 1).coerceIn(0, samplePrompts.size - 1)]

            val sample = RecordedSample(
                sampleIndex = sampleIndex,
                file = file,
                prompt = prompt,
                durationSeconds = qualityReport.durationSeconds,
                snrDb = qualityReport.snrDb,
                rmsEnergy = qualityReport.rmsEnergy,
                isValid = qualityReport.isValid
            )

            _uiState.update {
                it.copy(
                    currentReviewSample = sample,
                    errorMessage = qualityReport.errorMessage
                )
            }
            onSampleCaptured(sample)
        }
    }

    fun setSampleForReview(sample: RecordedSample, error: String?) {
        _uiState.update {
            it.copy(
                currentReviewSample = sample,
                errorMessage = error
            )
        }
    }

    fun cancelRecording() {
        audioRecordManager?.cancelRecording()
    }

    fun acceptCurrentSample(onProceedToNext: () -> Unit, onAllSamplesCompleted: () -> Unit) {
        val sample = _uiState.value.currentReviewSample
        if (sample == null || !sample.isValid) {
            _uiState.update { it.copy(errorMessage = "Cannot accept an invalid audio sample. Please re-record.") }
            return
        }

        // Validate that the accepted sample's audio file exists and is intact
        if (!sample.file.exists() || !sample.file.canRead() || sample.file.length() <= 44) {
            _uiState.update {
                it.copy(
                    errorMessage = "Sample #${sample.sampleIndex} audio file is missing or corrupted. Please re-record Sample #${sample.sampleIndex}."
                )
            }
            return
        }

        val updatedMap = _uiState.value.recordedSamples.toMutableMap()
        updatedMap[sample.sampleIndex] = sample

        if (sample.sampleIndex < _uiState.value.totalSamples) {
            _uiState.update {
                it.copy(
                    recordedSamples = updatedMap,
                    currentSampleIndex = sample.sampleIndex + 1,
                    currentReviewSample = null,
                    errorMessage = null
                )
            }
            onProceedToNext()
        } else {
            // All samples recorded. Perform thorough pre-flight validation on ALL required samples
            for (idx in 1.._uiState.value.totalSamples) {
                val s = updatedMap[idx]
                if (s == null) {
                    _uiState.update {
                        it.copy(
                            errorMessage = "Sample #$idx has not been recorded yet.",
                            currentSampleIndex = idx,
                            currentReviewSample = null
                        )
                    }
                    return
                }
                if (!s.file.exists() || !s.file.canRead() || s.file.length() <= 44) {
                    _uiState.update {
                        it.copy(
                            errorMessage = "Sample #$idx audio file (${s.file.name}) is missing or corrupted. Please re-record Sample #$idx.",
                            currentSampleIndex = idx,
                            currentReviewSample = null
                        )
                    }
                    return
                }
            }

            // All 3 samples confirmed present and valid on disk! Submit for AI embedding extraction
            _uiState.update {
                it.copy(
                    recordedSamples = updatedMap,
                    isUploading = true,
                    errorMessage = null
                )
            }
            uploadEnrollmentSamples(updatedMap.values.sortedBy { it.sampleIndex }, onAllSamplesCompleted)
        }
    }

    fun rerecordCurrentSample() {
        val sample = _uiState.value.currentReviewSample
        try {
            if (sample?.file?.exists() == true) {
                sample.file.delete()
            }
        } catch (_: Exception) {}
        _uiState.update { it.copy(currentReviewSample = null, errorMessage = null) }
    }

    fun discardCurrentSample(onDiscarded: () -> Unit) {
        val sample = _uiState.value.currentReviewSample
        try {
            if (sample?.file?.exists() == true) {
                sample.file.delete()
            }
        } catch (_: Exception) {}
        _uiState.update { it.copy(currentReviewSample = null, errorMessage = null) }
        onDiscarded()
    }

    fun cancelEnrollment(context: Context? = null) {
        // Delete recorded samples from active map
        _uiState.value.recordedSamples.values.forEach { sample ->
            try {
                if (sample.file.exists()) {
                    sample.file.delete()
                }
            } catch (_: Exception) {}
        }
        _uiState.value.currentReviewSample?.let { sample ->
            try {
                if (sample.file.exists()) {
                    sample.file.delete()
                }
            } catch (_: Exception) {}
        }

        // Clean up pending_enrollment directory if context is provided
        context?.let { ctx ->
            try {
                val dir = File(ctx.filesDir, "pending_enrollment")
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.delete() }
                }
            } catch (_: Exception) {}
        }

        _uiState.update {
            it.copy(
                currentSampleIndex = 1,
                recordedSamples = emptyMap(),
                currentReviewSample = null,
                errorMessage = null,
                isUploading = false
            )
        }
    }

    private fun uploadEnrollmentSamples(samples: List<RecordedSample>, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Secondary pre-flight guard before calling repository
            for ((idx, sample) in samples.withIndex()) {
                val file = sample.file
                if (!file.exists() || !file.canRead() || file.length() <= 44) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            errorMessage = "Sample #${idx + 1} audio file (${file.name}) is invalid or missing. Please re-record.",
                            currentSampleIndex = idx + 1,
                            currentReviewSample = null
                        )
                    }
                    return@launch
                }
            }

            val files = samples.map { it.file }
            val result = voiceRepository.enrollVoiceSamples(files)
            result.onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        currentSampleIndex = 1,
                        recordedSamples = emptyMap(),
                        currentReviewSample = null,
                        errorMessage = null,
                        successMessage = res.message
                    )
                }
                onComplete()
            }.onFailure { err ->
                // Retain recorded samples on network/server error so the user can retry without re-recording
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = err.message ?: "Failed to upload and extract voice embeddings. Please try again."
                    )
                }
            }
        }
    }

    fun deleteVoiceProfile(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = voiceRepository.deleteVoiceProfile()
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        voiceProfile = null,
                        isConsentGranted = false,
                        successMessage = "Voice profile and biometric embeddings deleted."
                    )
                }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to delete voice profile."
                    )
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecordManager?.cancelRecording()
        playbackManager.stop()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VoiceViewModel(
                    voiceRepository = AppContainer.voiceRepository
                )
            }
        }
    }
}
