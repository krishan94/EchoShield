package com.echoshield.verification

import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.VerificationHistoryRecord
import com.echoshield.domain.model.VerificationOutcome
import com.echoshield.domain.model.VoiceConsentRecord
import com.echoshield.domain.model.VoiceEnrollmentResult
import com.echoshield.domain.model.VoiceProfileInfo
import com.echoshield.domain.model.VoiceVerificationResult
import com.echoshield.domain.model.AntiSpoofOutcome
import com.echoshield.domain.model.AntiSpoofAnalysisResult
import com.echoshield.domain.model.AntiSpoofAudioQuality
import com.echoshield.domain.model.CombinedSecurityAnalysisResult
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.domain.model.SpeakerVerificationSummary
import com.echoshield.domain.model.AntiSpoofSummary
import com.echoshield.domain.model.AcousticScreeningSummary
import com.echoshield.domain.repository.FamilyRepository
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

class TestFamilyRepository : FamilyRepository {
    var membersList = mutableListOf(
        FamilyMember(
            id = "rel_001",
            memberId = "uid_alice",
            name = "Alice",
            email = "alice@echoshield.io",
            relationship = "Daughter",
            isOnline = true,
            isVoiceEnrolled = true,
            voiceQualityScore = 0.94f,
            lastActive = "Active now"
        ),
        FamilyMember(
            id = "rel_002",
            memberId = "uid_bob",
            name = "Bob",
            email = "bob@echoshield.io",
            relationship = "Son",
            isOnline = false,
            isVoiceEnrolled = false,
            voiceQualityScore = 0.0f,
            lastActive = "Yesterday"
        )
    )

    private val _familyMembers = MutableStateFlow<List<FamilyMember>>(membersList)
    override val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers.asStateFlow()

    private val _incoming = MutableStateFlow<List<com.echoshield.domain.model.FamilyInvitation>>(emptyList())
    override val incomingInvitations: StateFlow<List<com.echoshield.domain.model.FamilyInvitation>> = _incoming.asStateFlow()

    private val _outgoing = MutableStateFlow<List<com.echoshield.domain.model.FamilyInvitation>>(emptyList())
    override val outgoingInvitations: StateFlow<List<com.echoshield.domain.model.FamilyInvitation>> = _outgoing.asStateFlow()

    override suspend fun refreshFamilyMembers(): Result<List<FamilyMember>> {
        _familyMembers.value = membersList
        return Result.success(membersList)
    }

    override suspend fun refreshInvitations(): Result<Unit> = Result.success(Unit)

    override suspend fun getMemberById(memberId: String): FamilyMember? {
        return membersList.find { it.id == memberId || it.memberId == memberId }
    }

    override suspend fun searchUser(email: String): Result<com.echoshield.domain.model.UserSearchInfo> {
        return Result.success(com.echoshield.domain.model.UserSearchInfo(true, email, "Found User"))
    }

    override suspend fun sendInvitation(recipientEmail: String, relationship: String): Result<com.echoshield.domain.model.FamilyInvitation> {
        return Result.success(
            com.echoshield.domain.model.FamilyInvitation(
                id = "inv_1",
                senderId = "me",
                senderName = "Me",
                senderEmail = "me@test.com",
                recipientEmail = recipientEmail,
                relationship = relationship,
                status = "pending",
                createdAt = "now"
            )
        )
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun rejectInvitation(invitationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun cancelInvitation(invitationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFamilyMember(relationshipId: String): Result<Unit> = Result.success(Unit)
}

class TestVoiceRepository : VoiceRepository {
    private val _voiceProfile = MutableStateFlow<VoiceProfileInfo?>(null)
    override val voiceProfile: StateFlow<VoiceProfileInfo?> = _voiceProfile.asStateFlow()

    private val _isConsentGranted = MutableStateFlow(true)
    override val isConsentGranted: StateFlow<Boolean> = _isConsentGranted.asStateFlow()

    var shouldFailVerification: Boolean = false
    var simulatedOutcome: VerificationOutcome = VerificationOutcome.VOICE_MATCHED
    var simulatedSimilarity: Float = 0.88f
    var shouldFailSpoof: Boolean = false
    var simulatedSpoofOutcome: AntiSpoofOutcome = AntiSpoofOutcome.NO_SPOOF_SIGNAL_DETECTED
    var simulatedSpoofScore: Float? = 0.08f
    val historyLog = mutableListOf<VerificationHistoryRecord>()

    override suspend fun recordConsent(granted: Boolean): Result<VoiceConsentRecord> {
        return Result.success(VoiceConsentRecord("uid", "granted", "1.0", "now"))
    }

    override suspend fun enrollVoiceSamples(sampleFiles: List<File>): Result<VoiceEnrollmentResult> {
        return Result.success(VoiceEnrollmentResult("uid", "enrolled", 0.95f, 3, "Success"))
    }

    override suspend fun refreshVoiceProfile(): Result<VoiceProfileInfo> {
        return Result.success(VoiceProfileInfo("uid", "enrolled", true, 0.95f, 3, "ECAPA-TDNN", "v1.1", null, "granted"))
    }

    override suspend fun deleteVoiceProfile(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun verifyVoiceSample(
        memberId: String,
        audioFile: File
    ): Result<VoiceVerificationResult> {
        if (shouldFailVerification) {
            return Result.failure(Exception("Verification service unavailable"))
        }

        val result = VoiceVerificationResult(
            verificationId = "verif_unit_test_99",
            memberId = memberId,
            memberName = if (memberId == "uid_alice") "Alice" else "Bob",
            result = simulatedOutcome,
            similarityScore = if (simulatedOutcome == VerificationOutcome.PROFILE_NOT_ENROLLED) null else simulatedSimilarity,
            matchThreshold = 0.72f,
            nonMatchThreshold = 0.58f,
            decisionStatus = "evaluated",
            audioQualityStatus = "acceptable",
            modelName = "SpeechBrain ECAPA-TDNN",
            modelVersion = "voxceleb-v1.1",
            explanation = "Sample matches enrolled profile.",
            verifiedAt = "2026-11-01T00:00:00Z"
        )

        historyLog.add(
            VerificationHistoryRecord(
                id = result.verificationId,
                userId = "test_user",
                memberId = memberId,
                memberName = result.memberName,
                result = result.result,
                similarityScore = result.similarityScore,
                confidenceBand = "high_match",
                audioQualityStatus = "acceptable",
                durationSeconds = 3.5f,
                snrDb = 22.0f,
                modelName = result.modelName,
                modelVersion = result.modelVersion,
                verifiedAt = result.verifiedAt
            )
        )

        return Result.success(result)
    }

    override suspend fun analyzeSpoofSample(
        audioFile: File,
        requestId: String?
    ): Result<AntiSpoofAnalysisResult> {
        if (shouldFailSpoof) {
            return Result.failure(Exception("Anti-spoof service unavailable"))
        }

        val result = AntiSpoofAnalysisResult(
            analysisId = "spf_unit_test_42",
            requestId = requestId,
            resultCategory = simulatedSpoofOutcome,
            spoofScore = simulatedSpoofScore,
            confidenceScore = 0.94f,
            rawScores = mapOf("fake_probability" to (simulatedSpoofScore ?: 0.0f)),
            scoreInterpretation = "Evaluated by unit test mock",
            modelName = "mo-thecreator/Deepfake-audio-detection (Wav2Vec2)",
            modelVersion = "v1.0-hf",
            audioQuality = AntiSpoofAudioQuality(
                durationSeconds = 3.5f,
                sampleRate = 16000,
                channels = 1,
                rmsEnergy = 0.04f,
                snrDb = 22.0f,
                clippingRatio = 0.0f,
                silenceRatio = 0.05f,
                statusLabel = "EXCELLENT"
            ),
            timestamp = "2026-11-01T00:00:00Z",
            explanation = "Synthetic speech analysis completed.",
            isExperimental = true,
            replayDetectionSupported = false,
            replayLimitationNotice = "Replay detection limitation notice",
            disclaimer = "Experimental disclaimer"
        )
        return Result.success(result)
    }

    override suspend fun getVerificationHistory(): Result<List<VerificationHistoryRecord>> {
        return Result.success(historyLog)
    }

    override suspend fun clearVerificationHistory(): Result<Int> {
        val count = historyLog.size
        historyLog.clear()
        return Result.success(count)
    }

    var shouldFailSecurity: Boolean = false
    var simulatedSecurityOutcome: CombinedSecurityOutcome = CombinedSecurityOutcome.IDENTITY_CONFIRMED

    override suspend fun analyzeSecurity(
        memberId: String,
        audioFile: File,
        requestId: String?
    ): Result<CombinedSecurityAnalysisResult> {
        if (shouldFailSecurity) {
            return Result.failure(Exception("Security analysis service unavailable"))
        }

        val result = CombinedSecurityAnalysisResult(
            analysisId = "sec_unit_test_101",
            requestId = requestId,
            targetMemberId = memberId,
            targetMemberName = if (memberId == "uid_alice") "Alice" else "Bob",
            combinedDecision = simulatedSecurityOutcome,
            decisionHeadline = "Security Analysis Result",
            explanation = "Evaluated by unit test mock decision engine.",
            decisionFactors = listOf("Speaker matched reference", "No synthetic vocoder artifacts detected"),
            speakerVerification = SpeakerVerificationSummary(
                result = "VOICE_MATCHED",
                similarityScore = 0.88f,
                matchThreshold = 0.72f,
                nonMatchThreshold = 0.58f,
                decisionStatus = "high_match",
                explanation = "Matched reference."
            ),
            antiSpoofing = AntiSpoofSummary(
                resultCategory = if (simulatedSecurityOutcome == CombinedSecurityOutcome.SUSPICIOUS_AUDIO) "SPOOF_SUSPECTED" else "NO_SPOOF_SIGNAL_DETECTED",
                spoofScore = if (simulatedSecurityOutcome == CombinedSecurityOutcome.SUSPICIOUS_AUDIO) 0.92f else 0.05f,
                confidenceScore = 0.95f,
                scoreInterpretation = "Test interpretation",
                replayDetectionSupported = false,
                replayLimitationNotice = "Replay not detected"
            ),
            audioQuality = AcousticScreeningSummary(
                durationSeconds = 3.5f,
                sampleRate = 16000,
                snrDb = 22.0f,
                rmsEnergy = 0.04f,
                statusLabel = "EXCELLENT",
                isAcceptable = true
            ),
            limitations = listOf("Replay notice", "Experimental notice"),
            isExperimental = true,
            analyzedAt = "2026-11-01T00:00:00Z",
            disclaimer = "Test disclaimer"
        )
        return Result.success(result)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceVerificationUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeVoiceRepo: TestVoiceRepository
    private lateinit var fakeFamilyRepo: TestFamilyRepository
    private lateinit var viewModel: VoiceVerificationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVoiceRepo = TestVoiceRepository()
        fakeFamilyRepo = TestFamilyRepository()
        viewModel = VoiceVerificationViewModel(
            voiceRepository = fakeVoiceRepo,
            familyRepository = fakeFamilyRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsFamilyMembers() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.members.size)
        assertEquals("Alice", state.selectedMember?.name)
        assertFalse(state.isLoadingMembers)
    }

    @Test
    fun selectMember_switchesTargetMember() = runTest {
        advanceUntilIdle()
        val bob = viewModel.uiState.value.members.first { it.name == "Bob" }
        viewModel.selectMember(bob)
        assertEquals("Bob", viewModel.uiState.value.selectedMember?.name)
        assertNull(viewModel.uiState.value.verificationResult)
    }

    @Test
    fun setRecordedAudio_updatesState() = runTest {
        val dummyFile = File.createTempFile("sample_test_", ".wav")
        viewModel.setRecordedAudio(dummyFile)

        val state = viewModel.uiState.value
        assertTrue(state.hasRecordedSample)
        assertEquals(dummyFile, state.recordedFile)

        dummyFile.delete()
    }

    @Test
    fun verifyVoiceSample_success_returnsVoiceMatched() = runTest {
        advanceUntilIdle()
        val dummyFile = File.createTempFile("sample_test_", ".wav")
        viewModel.setRecordedAudio(dummyFile)

        fakeVoiceRepo.simulatedOutcome = VerificationOutcome.VOICE_MATCHED
        fakeVoiceRepo.simulatedSimilarity = 0.91f

        viewModel.verifyVoiceSample()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isVerifying)
        assertNotNull(state.verificationResult)
        assertEquals(VerificationOutcome.VOICE_MATCHED, state.verificationResult?.result)
        assertEquals(0.91f, state.verificationResult?.similarityScore)
        assertEquals(1, state.historyRecords.size)

        dummyFile.delete()
    }

    @Test
    fun verifyVoiceSample_serviceFailure_setsErrorMessage() = runTest {
        advanceUntilIdle()
        val dummyFile = File.createTempFile("sample_test_", ".wav")
        viewModel.setRecordedAudio(dummyFile)

        fakeVoiceRepo.shouldFailVerification = true
        viewModel.verifyVoiceSample()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isVerifying)
        assertNotNull(state.errorMessage)
        assertNull(state.verificationResult)

        dummyFile.delete()
    }

    @Test
    fun clearRecordedAudio_resetsFileState() = runTest {
        val dummyFile = File.createTempFile("sample_test_", ".wav")
        viewModel.setRecordedAudio(dummyFile)

        viewModel.clearRecordedAudio()
        val state = viewModel.uiState.value
        assertFalse(state.hasRecordedSample)
        assertNull(state.recordedFile)
    }

    @Test
    fun clearVerificationHistory_emptiesLog() = runTest {
        advanceUntilIdle()
        val dummyFile = File.createTempFile("sample_test_", ".wav")
        viewModel.setRecordedAudio(dummyFile)
        viewModel.verifyVoiceSample()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.historyRecords.size)

        viewModel.clearVerificationHistory()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.historyRecords.size)
        dummyFile.delete()
    }

    @Test
    fun verificationOutcome_enumParsing() {
        assertEquals(VerificationOutcome.VOICE_MATCHED, VerificationOutcome.fromString("VOICE_MATCHED"))
        assertEquals(VerificationOutcome.NO_MATCH, VerificationOutcome.fromString("NO_MATCH"))
        assertEquals(VerificationOutcome.INCONCLUSIVE, VerificationOutcome.fromString("INCONCLUSIVE"))
        assertEquals(VerificationOutcome.AUDIO_QUALITY_FAILED, VerificationOutcome.fromString("AUDIO_QUALITY_FAILED"))
        assertEquals(VerificationOutcome.PROFILE_NOT_ENROLLED, VerificationOutcome.fromString("PROFILE_NOT_ENROLLED"))
        assertEquals(VerificationOutcome.INCONCLUSIVE, VerificationOutcome.fromString("UNKNOWN_VALUE"))
    }

    @Test
    fun antiSpoofOutcome_enumParsing() {
        assertEquals(AntiSpoofOutcome.SPOOF_SUSPECTED, AntiSpoofOutcome.fromString("SPOOF_SUSPECTED"))
        assertEquals(AntiSpoofOutcome.NO_SPOOF_SIGNAL_DETECTED, AntiSpoofOutcome.fromString("NO_SPOOF_SIGNAL_DETECTED"))
        assertEquals(AntiSpoofOutcome.INCONCLUSIVE, AntiSpoofOutcome.fromString("INCONCLUSIVE"))
        assertEquals(AntiSpoofOutcome.AUDIO_QUALITY_FAILED, AntiSpoofOutcome.fromString("AUDIO_QUALITY_FAILED"))
        assertEquals(AntiSpoofOutcome.MODEL_UNAVAILABLE, AntiSpoofOutcome.fromString("MODEL_UNAVAILABLE"))
        assertEquals(AntiSpoofOutcome.INCONCLUSIVE, AntiSpoofOutcome.fromString("UNKNOWN_SPOOF_CAT"))
    }

    @Test
    fun analyzeSpoofSample_successUpdatesUiState() = runTest {
        advanceUntilIdle()
        val dummyFile = File.createTempFile("spoof_test_", ".wav")
        viewModel.setRecordedAudio(dummyFile)

        viewModel.analyzeSpoofSample()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAnalyzingSpoof)
        assertNotNull(viewModel.uiState.value.antiSpoofResult)
        assertEquals(AntiSpoofOutcome.NO_SPOOF_SIGNAL_DETECTED, viewModel.uiState.value.antiSpoofResult?.resultCategory)
        assertEquals(0.08f, viewModel.uiState.value.antiSpoofResult?.spoofScore)
        assertFalse(viewModel.uiState.value.antiSpoofResult?.replayDetectionSupported ?: true)
        dummyFile.delete()
    }

    @Test
    fun analyzeSpoofSample_suspectedSpoofOutcome() = runTest {
        advanceUntilIdle()
        fakeVoiceRepo.simulatedSpoofOutcome = AntiSpoofOutcome.SPOOF_SUSPECTED
        fakeVoiceRepo.simulatedSpoofScore = 0.94f

        val dummyFile = File.createTempFile("spoof_suspected_", ".wav")
        viewModel.setRecordedAudio(dummyFile)

        viewModel.analyzeSpoofSample()
        advanceUntilIdle()

        assertEquals(AntiSpoofOutcome.SPOOF_SUSPECTED, viewModel.uiState.value.antiSpoofResult?.resultCategory)
        assertEquals(0.94f, viewModel.uiState.value.antiSpoofResult?.spoofScore)
        dummyFile.delete()
    }

    @Test
    fun verifyVoiceWithAntiSpoof_runsBothIndependently() = runTest {
        advanceUntilIdle()
        fakeVoiceRepo.simulatedOutcome = VerificationOutcome.VOICE_MATCHED
        fakeVoiceRepo.simulatedSimilarity = 0.91f
        fakeVoiceRepo.simulatedSpoofOutcome = AntiSpoofOutcome.SPOOF_SUSPECTED
        fakeVoiceRepo.simulatedSpoofScore = 0.88f

        val dummyFile = File.createTempFile("dual_test_", ".wav")
        viewModel.setRecordedAudio(dummyFile)

        viewModel.verifyVoiceWithAntiSpoof()
        advanceUntilIdle()

        // Verify that speaker similarity is high AND spoof is suspected WITHOUT conflating them
        assertNotNull(viewModel.uiState.value.verificationResult)
        assertEquals(VerificationOutcome.VOICE_MATCHED, viewModel.uiState.value.verificationResult?.result)
        assertEquals(0.91f, viewModel.uiState.value.verificationResult?.similarityScore)

        assertNotNull(viewModel.uiState.value.antiSpoofResult)
        assertEquals(AntiSpoofOutcome.SPOOF_SUSPECTED, viewModel.uiState.value.antiSpoofResult?.resultCategory)
        assertEquals(0.88f, viewModel.uiState.value.antiSpoofResult?.spoofScore)
        dummyFile.delete()
    }

    @Test
    fun resetVerification_clearsBothVerificationAndSpoofResults() = runTest {
        advanceUntilIdle()
        val dummyFile = File.createTempFile("reset_test_", ".wav")
        viewModel.setRecordedAudio(dummyFile)

        viewModel.verifyVoiceWithAntiSpoof()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.verificationResult)
        assertNotNull(viewModel.uiState.value.antiSpoofResult)

        viewModel.resetVerification()

        assertNull(viewModel.uiState.value.verificationResult)
        assertNull(viewModel.uiState.value.antiSpoofResult)
        assertFalse(viewModel.uiState.value.hasRecordedSample)
    }

    @Test
    fun testAnalyzeSecurity_Success_IdentityConfirmed() = runTest(testDispatcher) {
        val dummyAudio = File.createTempFile("test_audio", ".wav").apply { deleteOnExit() }
        viewModel.setRecordedAudio(dummyAudio)

        fakeVoiceRepo.simulatedSecurityOutcome = CombinedSecurityOutcome.IDENTITY_CONFIRMED

        viewModel.analyzeSecurity()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzingSecurity)
        assertNotNull(state.combinedSecurityResult)
        assertEquals(CombinedSecurityOutcome.IDENTITY_CONFIRMED, state.combinedSecurityResult!!.combinedDecision)
        assertEquals("Alice", state.combinedSecurityResult!!.targetMemberName)
        assertNull(state.errorMessage)
    }

    @Test
    fun testAnalyzeSecurity_SuspiciousAudioOverride() = runTest(testDispatcher) {
        val dummyAudio = File.createTempFile("test_audio_spoof", ".wav").apply { deleteOnExit() }
        viewModel.setRecordedAudio(dummyAudio)

        fakeVoiceRepo.simulatedSecurityOutcome = CombinedSecurityOutcome.SUSPICIOUS_AUDIO

        viewModel.analyzeSecurity()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzingSecurity)
        assertNotNull(state.combinedSecurityResult)
        assertEquals(CombinedSecurityOutcome.SUSPICIOUS_AUDIO, state.combinedSecurityResult!!.combinedDecision)
        assertEquals("SPOOF_SUSPECTED", state.combinedSecurityResult!!.antiSpoofing?.resultCategory)
    }

    @Test
    fun testAnalyzeSecurity_NetworkFailure_SetsError() = runTest(testDispatcher) {
        val dummyAudio = File.createTempFile("test_audio_fail", ".wav").apply { deleteOnExit() }
        viewModel.setRecordedAudio(dummyAudio)

        fakeVoiceRepo.shouldFailSecurity = true

        viewModel.analyzeSecurity()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzingSecurity)
        assertNull(state.combinedSecurityResult)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("unavailable") || state.errorMessage!!.contains("failed"))
    }

    @Test
    fun testAnalyzeSecurity_NoAudioRecorded_SetsError() = runTest(testDispatcher) {
        viewModel.clearRecordedAudio()
        viewModel.analyzeSecurity()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("No valid audio"))
    }

    @Test
    fun testResetVerification_ClearsCombinedSecurityResult() = runTest(testDispatcher) {
        val dummyAudio = File.createTempFile("test_reset_sec", ".wav").apply { deleteOnExit() }
        viewModel.setRecordedAudio(dummyAudio)
        viewModel.analyzeSecurity()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.combinedSecurityResult)

        viewModel.resetVerification()

        val state = viewModel.uiState.value
        assertNull(state.combinedSecurityResult)
        assertNull(state.verificationResult)
        assertNull(state.antiSpoofResult)
        assertNull(state.recordedFile)
        assertFalse(state.hasRecordedSample)
    }
}
