package com.echoshield.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoshield.core.di.AppContainer
import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.VerificationHistoryRecord
import com.echoshield.domain.model.VoiceVerificationResult
import com.echoshield.domain.repository.FamilyRepository
import com.echoshield.domain.repository.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VoiceVerificationUiState(
    val isLoadingMembers: Boolean = false,
    val members: List<FamilyMember> = emptyList(),
    val selectedMember: FamilyMember? = null,
    val isRecording: Boolean = false,
    val hasRecordedSample: Boolean = false,
    val recordedFile: File? = null,
    val isVerifying: Boolean = false,
    val verificationResult: VoiceVerificationResult? = null,
    val historyRecords: List<VerificationHistoryRecord> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val errorMessage: String? = null
)

class VoiceVerificationViewModel(
    private val voiceRepository: VoiceRepository = AppContainer.voiceRepository,
    private val familyRepository: FamilyRepository = AppContainer.familyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceVerificationUiState())
    val uiState: StateFlow<VoiceVerificationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            familyRepository.familyMembers.collect { list ->
                _uiState.update { current ->
                    current.copy(
                        members = list,
                        selectedMember = current.selectedMember ?: list.firstOrNull()
                    )
                }
            }
        }
        loadFamilyMembers()
        loadVerificationHistory()
    }

    fun loadFamilyMembers(preselectMemberId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMembers = true) }
            val result = familyRepository.refreshFamilyMembers()
            result.onSuccess { membersList: List<FamilyMember> ->
                val preselected = if (preselectMemberId != null) {
                    membersList.find { it.id == preselectMemberId || it.memberId == preselectMemberId }
                } else null

                _uiState.update {
                    it.copy(
                        isLoadingMembers = false,
                        members = membersList,
                        selectedMember = preselected ?: it.selectedMember ?: membersList.firstOrNull()
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingMembers = false,
                        errorMessage = err.message ?: "Failed to load family circle members."
                    )
                }
            }
        }
    }

    fun selectMember(member: FamilyMember) {
        _uiState.update {
            it.copy(
                selectedMember = member,
                verificationResult = null,
                errorMessage = null
            )
        }
    }

    fun setRecordedAudio(file: File) {
        _uiState.update {
            it.copy(
                hasRecordedSample = true,
                recordedFile = file,
                errorMessage = null
            )
        }
    }

    fun clearRecordedAudio() {
        val currentFile = _uiState.value.recordedFile
        try {
            if (currentFile != null && currentFile.exists()) {
                currentFile.delete()
            }
        } catch (_: Exception) {}

        _uiState.update {
            it.copy(
                hasRecordedSample = false,
                recordedFile = null,
                errorMessage = null
            )
        }
    }

    fun resetVerification() {
        clearRecordedAudio()
        _uiState.update {
            it.copy(
                verificationResult = null,
                errorMessage = null
            )
        }
    }

    fun verifyVoiceSample(file: File? = null) {
        val member = _uiState.value.selectedMember
        if (member == null) {
            _uiState.update { it.copy(errorMessage = "Please select a family member to verify against.") }
            return
        }

        val audioToVerify = file ?: _uiState.value.recordedFile
        if (audioToVerify == null || !audioToVerify.exists()) {
            _uiState.update { it.copy(errorMessage = "No valid audio sample available for verification.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
            // Use member.memberId (the remote user UID) or member.id
            val targetId = if (member.memberId.isNotBlank()) member.memberId else member.id
            val result = voiceRepository.verifyVoiceSample(targetId, audioToVerify)

            result.onSuccess { outcome ->
                _uiState.update {
                    it.copy(
                        isVerifying = false,
                        verificationResult = outcome
                    )
                }
                loadVerificationHistory()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isVerifying = false,
                        errorMessage = err.message ?: "Voice verification request failed."
                    )
                }
            }
        }
    }

    fun loadVerificationHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true) }
            val result = voiceRepository.getVerificationHistory()
            result.onSuccess { records ->
                _uiState.update {
                    it.copy(
                        isLoadingHistory = false,
                        historyRecords = records
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingHistory = false) }
            }
        }
    }

    fun clearVerificationHistory() {
        viewModelScope.launch {
            val result = voiceRepository.clearVerificationHistory()
            result.onSuccess {
                _uiState.update { it.copy(historyRecords = emptyList()) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
