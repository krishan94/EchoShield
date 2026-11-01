package com.echoshield.calling

import com.echoshield.calling.signaling.SignalingClient
import com.echoshield.calling.webrtc.RemoteAudioCaptureSink
import com.echoshield.domain.model.AuthResult
import com.echoshield.domain.model.AuthUser
import com.echoshield.domain.model.CallHistoryRecord
import com.echoshield.domain.model.CallReport
import com.echoshield.domain.model.CallSessionInfo
import com.echoshield.domain.model.CallStatus
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.domain.model.SignalingMessage
import com.echoshield.domain.model.SignalingMessageType
import com.echoshield.domain.repository.AuthRepository
import com.echoshield.domain.repository.CallingRepository
import com.echoshield.domain.repository.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

// Mock AuthRepository
class MockAuthRepository : AuthRepository {
    private val _currentUser = MutableStateFlow<AuthUser?>(
        AuthUser(uid = "uid_caller_111", email = "caller@echoshield.io", displayName = "Sarah Henderson")
    )
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()
    override suspend fun getCurrentUser(): AuthUser? = _currentUser.value
    override fun isUserLoggedIn(): Boolean = true
    override suspend fun signup(name: String, email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(_currentUser.value!!)
    override suspend fun login(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(_currentUser.value!!)
    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> = AuthResult.Success(Unit)
    override suspend fun sendEmailVerification(): AuthResult<Unit> = AuthResult.Success(Unit)
    override suspend fun reloadUser(): AuthResult<AuthUser> = AuthResult.Success(_currentUser.value!!)
    override suspend fun logout(): AuthResult<Unit> {
        _currentUser.value = null
        return AuthResult.Success(Unit)
    }
    override suspend fun getIdToken(forceRefresh: Boolean): AuthResult<String> =
        AuthResult.Success("mock_jwt_token_123")
}

// Mock CallingRepository
class MockCallingRepository : CallingRepository {
    private val _currentCallSession = MutableStateFlow<CallSessionInfo?>(null)
    override val currentCallSession: StateFlow<CallSessionInfo?> = _currentCallSession.asStateFlow()

    private val _liveSecurityOutcome = MutableStateFlow<CombinedSecurityOutcome?>(null)
    override val liveSecurityOutcome: StateFlow<CombinedSecurityOutcome?> = _liveSecurityOutcome.asStateFlow()

    val savedReports = mutableMapOf<String, CallReport>()
    var mockHistory = mutableListOf<CallHistoryRecord>()

    override suspend fun getCallHistory(limit: Int): Result<List<CallHistoryRecord>> {
        return Result.success(mockHistory.take(limit))
    }

    override suspend fun getCallReport(callId: String): Result<CallReport> {
        val report = savedReports[callId]
        return if (report != null) Result.success(report) else Result.failure(Exception("Report not found"))
    }

    override suspend fun saveCallReport(report: CallReport): Result<CallReport> {
        savedReports[report.callId] = report
        return Result.success(report)
    }

    override suspend fun getCallStatus(callId: String): Result<CallSessionInfo> {
        val session = _currentCallSession.value ?: CallSessionInfo(
            callId = callId,
            callerId = "uid_caller_111",
            callerName = "Sarah",
            calleeId = "uid_callee_222",
            calleeName = "David",
            status = CallStatus.CONNECTED,
            createdAt = "2026-11-01T00:00:00Z"
        )
        return Result.success(session)
    }
}

// Minimal Mock VoiceRepository
class MockVoiceRepository : VoiceRepository {
    override val voiceProfile = MutableStateFlow(null)
    override val isConsentGranted = MutableStateFlow(true)
    override suspend fun recordConsent(granted: Boolean) = Result.failure<com.echoshield.domain.model.VoiceConsentRecord>(Exception("Not needed"))
    override suspend fun enrollVoiceSamples(sampleFiles: List<File>) = Result.failure<com.echoshield.domain.model.VoiceEnrollmentResult>(Exception("Not needed"))
    override suspend fun refreshVoiceProfile() = Result.failure<com.echoshield.domain.model.VoiceProfileInfo>(Exception("Not needed"))
    override suspend fun deleteVoiceProfile() = Result.success(Unit)
    override suspend fun verifyVoiceSample(memberId: String, audioFile: File) = Result.failure<com.echoshield.domain.model.VoiceVerificationResult>(Exception("Not needed"))
    override suspend fun analyzeSpoofSample(audioFile: File, requestId: String?) = Result.failure<com.echoshield.domain.model.AntiSpoofAnalysisResult>(Exception("Not needed"))
    override suspend fun analyzeSecurity(memberId: String, audioFile: File, requestId: String?) = Result.failure<com.echoshield.domain.model.CombinedSecurityAnalysisResult>(Exception("Not needed"))
    override suspend fun getVerificationHistory() = Result.success(emptyList<com.echoshield.domain.model.VerificationHistoryRecord>())
    override suspend fun clearVerificationHistory() = Result.success(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CallingUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockAuth: MockAuthRepository
    private lateinit var mockCallingRepo: MockCallingRepository
    private lateinit var mockVoiceRepo: MockVoiceRepository
    private lateinit var viewModel: CallingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAuth = MockAuthRepository()
        mockCallingRepo = MockCallingRepository()
        mockVoiceRepo = MockVoiceRepository()

        viewModel = CallingViewModel(
            callingRepository = mockCallingRepo,
            voiceRepository = mockVoiceRepo,
            authRepository = mockAuth
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_calling_ui_state_initial_defaults() {
        val state = viewModel.uiState.value
        assertEquals(CallStatus.IDLE, state.callStatus)
        assertEquals(0, state.callDurationSeconds)
        assertFalse(state.isMuted)
        assertTrue(state.isSpeakerOn)
        assertNull(state.callId)
        assertNull(state.remoteMemberId)
        assertNull(state.liveSecurityOutcome)
        assertEquals(0, state.analysesCount)
    }

    @Test
    fun test_call_mute_and_speaker_toggles() {
        assertFalse(viewModel.uiState.value.isMuted)
        viewModel.toggleMute()
        assertTrue(viewModel.uiState.value.isMuted)
        viewModel.toggleMute()
        assertFalse(viewModel.uiState.value.isMuted)

        assertTrue(viewModel.uiState.value.isSpeakerOn)
        viewModel.toggleSpeaker()
        assertFalse(viewModel.uiState.value.isSpeakerOn)
        viewModel.toggleSpeaker()
        assertTrue(viewModel.uiState.value.isSpeakerOn)
    }

    @Test
    fun test_remote_audio_sink_buffering_and_wav_export() {
        val sink = RemoteAudioCaptureSink(maxBufferDurationSeconds = 4)
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16

        // Generate 2 seconds of 16kHz 16-bit PCM silence/tone frames
        val framesCount = sampleRate * 2 // 32,000 samples = 64,000 bytes
        val byteBuf = ByteBuffer.allocate(framesCount * 2)
        for (i in 0 until framesCount) {
            byteBuf.putShort((i % 1000).toShort())
        }
        byteBuf.flip()

        sink.onData(
            audioData = byteBuf,
            bitsPerSample = bitsPerSample,
            sampleRate = sampleRate,
            numberOfChannels = channels,
            numberOfFrames = framesCount,
            absoluteCaptureTimestampMs = System.currentTimeMillis()
        )

        // Export to temporary WAV file
        val tempWav = File.createTempFile("test_remote_audio", ".wav")
        tempWav.deleteOnExit()

        val exported = sink.exportToWav(tempWav, minDurationSeconds = 1.5f)
        assertTrue("WAV export should succeed when sufficient audio buffer is present", exported)
        assertTrue(tempWav.exists())
        assertEquals(64044L, tempWav.length()) // 44 bytes header + 64,000 bytes PCM

        // Verify WAV Header
        FileInputStream(tempWav).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            val fmt = String(header, 12, 4)
            val data = String(header, 36, 4)

            assertEquals("RIFF", riff)
            assertEquals("WAVE", wave)
            assertEquals("fmt ", fmt)
            assertEquals("data", data)
        }
    }

    @Test
    fun test_call_history_loading_with_security_outcomes() = runTest(testDispatcher) {
        mockCallingRepo.mockHistory = mutableListOf(
            CallHistoryRecord(
                callId = "call_001",
                callerId = "uid_caller_111",
                callerName = "Sarah",
                calleeId = "uid_callee_222",
                calleeName = "David",
                direction = "outgoing",
                otherPartyName = "David",
                otherPartyId = "uid_callee_222",
                status = CallStatus.ENDED,
                durationSeconds = 85,
                securityOutcome = CombinedSecurityOutcome.IDENTITY_CONFIRMED,
                securityHeadline = "Voice Identity Cryptographically Confirmed",
                hasReport = true,
                createdAt = "2026-11-01T01:00:00Z"
            ),
            CallHistoryRecord(
                callId = "call_002",
                callerId = "uid_attacker_999",
                callerName = "Unknown",
                calleeId = "uid_caller_111",
                calleeName = "Sarah",
                direction = "incoming",
                otherPartyName = "Unknown",
                otherPartyId = "uid_attacker_999",
                status = CallStatus.ENDED,
                durationSeconds = 24,
                securityOutcome = CombinedSecurityOutcome.SUSPICIOUS_AUDIO,
                securityHeadline = "Suspicious Audio Signal Detected",
                hasReport = true,
                createdAt = "2026-11-01T02:00:00Z"
            )
        )

        viewModel.loadCallHistory()
        advanceUntilIdle()

        val history = viewModel.uiState.value.callHistory
        assertEquals(2, history.size)
        assertEquals("call_001", history[0].callId)
        assertEquals("outgoing", history[0].direction)
        assertEquals(CombinedSecurityOutcome.IDENTITY_CONFIRMED, history[0].securityOutcome)

        assertEquals("call_002", history[1].callId)
        assertEquals("incoming", history[1].direction)
        assertEquals(CombinedSecurityOutcome.SUSPICIOUS_AUDIO, history[1].securityOutcome)
    }

    @Test
    fun test_post_call_report_persistence_on_hangup() = runTest(testDispatcher) {
        // Mock active outgoing call state
        val callId = "call_test_report_123"
        val memberId = "uid_callee_222"
        val memberName = "David Henderson"

        // Set up active call state in session coordinator
        viewModel.coordinator._uiState.value = CallingUiState(
            callStatus = CallStatus.CONNECTED,
            callId = callId,
            remoteMemberId = memberId,
            remoteMemberName = memberName,
            callDurationSeconds = 48,
            liveSecurityOutcome = CombinedSecurityOutcome.IDENTITY_CONFIRMED,
            liveSecurityHeadline = "Voice Identity Cryptographically Confirmed",
            liveSecurityExplanation = "High biometric speaker similarity score with zero synthetic anomalies.",
            analysesCount = 2
        )

        // End the call
        viewModel.endCall("User ended call")
        advanceUntilIdle()

        // Verify report was persisted into mockCallingRepo
        val savedReport = mockCallingRepo.savedReports[callId]
        assertNotNull("CallReport should be saved upon call termination", savedReport)
        assertEquals(callId, savedReport?.callId)
        assertEquals(CombinedSecurityOutcome.IDENTITY_CONFIRMED, savedReport?.overallSecurityOutcome)
        assertEquals("LOW", savedReport?.riskLevel)
        assertFalse(savedReport?.isThreatDetected ?: true)
        assertEquals(48, savedReport?.durationSeconds)
        assertTrue(savedReport?.keyFindings?.isNotEmpty() ?: false)
    }

    @Test
    fun test_incoming_call_signaling_event_triggers_ringing_state() = runTest(testDispatcher) {
        val coordinator = viewModel.coordinator
        val mockIncomingMsg = SignalingMessage(
            type = SignalingMessageType.CALL_INITIATE,
            callId = "call_incoming_999",
            senderId = "uid_callee_222",
            senderName = "David Henderson",
            receiverId = "uid_caller_111"
        )

        coordinator.signalingClient.emitEventForTest(mockIncomingMsg)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CallStatus.RINGING, state.callStatus)
        assertEquals("call_incoming_999", state.callId)
        assertEquals("uid_callee_222", state.remoteMemberId)
        assertEquals("David Henderson", state.remoteMemberName)
    }

    @Test
    fun test_call_rejection_signaling_transitions_state() = runTest(testDispatcher) {
        val coordinator = viewModel.coordinator
        coordinator._uiState.value = CallingUiState(
            callStatus = CallStatus.RINGING,
            callId = "call_rej_123",
            remoteMemberId = "uid_callee_222"
        )

        val rejectMsg = SignalingMessage(
            type = SignalingMessageType.CALL_REJECTED,
            callId = "call_rej_123",
            senderId = "uid_callee_222"
        )
        coordinator.signalingClient.emitEventForTest(rejectMsg)
        advanceUntilIdle()

        assertEquals(CallStatus.REJECTED, viewModel.uiState.value.callStatus)
    }
}
