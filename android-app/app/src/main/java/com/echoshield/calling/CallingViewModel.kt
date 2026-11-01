package com.echoshield.calling

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echoshield.calling.signaling.SignalingClient
import com.echoshield.core.di.AppContainer
import com.echoshield.domain.model.CallHistoryRecord
import com.echoshield.domain.model.CallReport
import com.echoshield.domain.model.CallStatus
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.domain.repository.AuthRepository
import com.echoshield.domain.repository.CallingRepository
import com.echoshield.domain.repository.VoiceRepository
import kotlinx.coroutines.flow.StateFlow

data class CallingUiState(
    val callStatus: CallStatus = CallStatus.IDLE,
    val callId: String? = null,
    val remoteMemberId: String? = null,
    val remoteMemberName: String? = null,
    val callDurationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isAnalyzingAudio: Boolean = false,
    val liveSecurityOutcome: CombinedSecurityOutcome? = null,
    val liveSecurityHeadline: String? = null,
    val liveSecurityExplanation: String? = null,
    val liveSecurityRisk: String? = null,
    val analysesCount: Int = 0,
    val postCallReport: CallReport? = null,
    val callHistory: List<CallHistoryRecord> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class CallingViewModel(
    val coordinator: CallSessionCoordinator
) : ViewModel() {

    // Overload constructor for backwards compatibility with tests
    constructor(
        callingRepository: CallingRepository,
        voiceRepository: VoiceRepository,
        authRepository: AuthRepository,
        signalingClient: SignalingClient = SignalingClient()
    ) : this(
        CallSessionCoordinator(
            callingRepository = callingRepository,
            voiceRepository = voiceRepository,
            authRepository = authRepository,
            signalingClient = signalingClient
        )
    )

    val uiState: StateFlow<CallingUiState> = coordinator.uiState
    val signalingClient: SignalingClient get() = coordinator.signalingClient

    fun connectSignaling() {
        coordinator.connectSignaling()
    }

    fun startOutgoingCall(targetMemberId: String, targetMemberName: String, context: Context) {
        coordinator.startOutgoingCall(targetMemberId, targetMemberName, context)
    }

    fun acceptIncomingCall(context: Context) {
        coordinator.acceptIncomingCall(context)
    }

    fun rejectIncomingCall() {
        coordinator.rejectIncomingCall()
    }

    fun endCall(reason: String = "User hang up") {
        coordinator.endCall(reason)
    }

    fun toggleMute() {
        coordinator.toggleMute()
    }

    fun toggleSpeaker() {
        coordinator.toggleSpeaker()
    }

    fun loadCallHistory() {
        coordinator.loadCallHistory()
    }

    fun loadCallReport(callId: String) {
        coordinator.loadCallReport(callId)
    }

    fun resetCallState() {
        coordinator.resetCallState()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CallingViewModel(
                    coordinator = AppContainer.callSessionCoordinator
                )
            }
        }
    }
}

