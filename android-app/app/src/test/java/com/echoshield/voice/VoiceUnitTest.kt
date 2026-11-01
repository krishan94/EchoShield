package com.echoshield.voice

import com.echoshield.domain.model.RecordedSample
import com.echoshield.domain.model.VoiceConsentRecord
import com.echoshield.domain.model.VoiceEnrollmentResult
import com.echoshield.domain.model.VoiceProfileInfo
import com.echoshield.domain.repository.VoiceRepository
import com.echoshield.data.remote.VoiceRemoteDataSource
import com.echoshield.data.repository.VoiceRepositoryImpl
import com.echoshield.voice.audio.AudioQualityValidator
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
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fake in-memory implementation of [VoiceRepository] for hermetic unit testing.
 */
class FakeVoiceRepository : VoiceRepository {

    private val _voiceProfile = MutableStateFlow<VoiceProfileInfo?>(null)
    override val voiceProfile: StateFlow<VoiceProfileInfo?> = _voiceProfile.asStateFlow()

    private val _isConsentGranted = MutableStateFlow(false)
    override val isConsentGranted: StateFlow<Boolean> = _isConsentGranted.asStateFlow()

    var shouldFailNetwork: Boolean = false

    override suspend fun recordConsent(granted: Boolean): Result<VoiceConsentRecord> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        _isConsentGranted.value = granted
        val record = VoiceConsentRecord(
            userId = "user_test_123",
            consentStatus = if (granted) "granted" else "revoked",
            consentVersion = "1.0",
            consentTimestamp = "2026-10-31T23:55:00Z"
        )
        return Result.success(record)
    }

    override suspend fun enrollVoiceSamples(sampleFiles: List<File>): Result<VoiceEnrollmentResult> {
        if (shouldFailNetwork) return Result.failure(Exception("Server connection failed"))
        if (sampleFiles.size < 3) return Result.failure(IllegalArgumentException("3 samples required"))

        val result = VoiceEnrollmentResult(
            userId = "user_test_123",
            enrollmentStatus = "enrolled",
            qualityScore = 0.96f,
            sampleCount = sampleFiles.size,
            message = "Voice profile enrolled successfully."
        )

        _voiceProfile.value = VoiceProfileInfo(
            userId = "user_test_123",
            enrollmentStatus = "enrolled",
            isVoiceEnrolled = true,
            voiceQualityScore = 0.96f,
            sampleCount = sampleFiles.size,
            embeddingModel = "SpeechBrain ECAPA-TDNN",
            modelVersion = "voxceleb-v1.1",
            enrolledAt = "2026-10-31T23:55:00Z",
            consentStatus = "granted"
        )

        return Result.success(result)
    }

    override suspend fun refreshVoiceProfile(): Result<VoiceProfileInfo> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        val current = _voiceProfile.value ?: VoiceProfileInfo(
            userId = "user_test_123",
            enrollmentStatus = "not_enrolled",
            isVoiceEnrolled = false,
            voiceQualityScore = 0f,
            sampleCount = 0,
            embeddingModel = "SpeechBrain ECAPA-TDNN",
            modelVersion = "voxceleb-v1.1",
            enrolledAt = null,
            consentStatus = if (_isConsentGranted.value) "granted" else "pending"
        )
        _voiceProfile.value = current
        return Result.success(current)
    }

    override suspend fun deleteVoiceProfile(): Result<Unit> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        _voiceProfile.value = null
        _isConsentGranted.value = false
        return Result.success(Unit)
    }

    override suspend fun verifyVoiceSample(
        memberId: String,
        audioFile: File
    ): Result<com.echoshield.domain.model.VoiceVerificationResult> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        val result = com.echoshield.domain.model.VoiceVerificationResult(
            verificationId = "verif_test_123",
            memberId = memberId,
            memberName = "Test Member",
            result = com.echoshield.domain.model.VerificationOutcome.VOICE_MATCHED,
            similarityScore = 0.88f,
            matchThreshold = 0.72f,
            nonMatchThreshold = 0.58f,
            decisionStatus = "high_match",
            audioQualityStatus = "acceptable",
            modelName = "SpeechBrain ECAPA-TDNN",
            modelVersion = "voxceleb-v1.1",
            explanation = "Sample matches enrolled profile.",
            verifiedAt = "2026-11-01T00:00:00Z"
        )
        return Result.success(result)
    }

    override suspend fun analyzeSpoofSample(
        audioFile: File,
        requestId: String?
    ): Result<com.echoshield.domain.model.AntiSpoofAnalysisResult> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        val result = com.echoshield.domain.model.AntiSpoofAnalysisResult(
            analysisId = "spf_fake_test",
            requestId = requestId,
            resultCategory = com.echoshield.domain.model.AntiSpoofOutcome.NO_SPOOF_SIGNAL_DETECTED,
            spoofScore = 0.05f,
            confidenceScore = 0.95f,
            rawScores = mapOf("fake_probability" to 0.05f, "real_probability" to 0.95f),
            scoreInterpretation = "No spoof signatures detected",
            modelName = "mo-thecreator/Deepfake-audio-detection (Wav2Vec2)",
            modelVersion = "v1.0-hf",
            audioQuality = com.echoshield.domain.model.AntiSpoofAudioQuality(
                durationSeconds = 3.5f,
                sampleRate = 16000,
                channels = 1,
                rmsEnergy = 0.04f,
                snrDb = 20.0f,
                clippingRatio = 0.0f,
                silenceRatio = 0.1f,
                statusLabel = "EXCELLENT"
            ),
            timestamp = "2026-11-01T00:00:00Z",
            explanation = "Sample does not exhibit synthetic speech artifacts.",
            isExperimental = true,
            replayDetectionSupported = false,
            replayLimitationNotice = "Replay notice",
            disclaimer = "Experimental disclaimer"
        )
        return Result.success(result)
    }

    override suspend fun getVerificationHistory(): Result<List<com.echoshield.domain.model.VerificationHistoryRecord>> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        return Result.success(emptyList())
    }

    override suspend fun analyzeSecurity(
        memberId: String,
        audioFile: File,
        requestId: String?
    ): Result<com.echoshield.domain.model.CombinedSecurityAnalysisResult> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        val result = com.echoshield.domain.model.CombinedSecurityAnalysisResult(
            analysisId = "sec_test_001",
            requestId = requestId,
            targetMemberId = memberId,
            targetMemberName = "Test Member",
            combinedDecision = com.echoshield.domain.model.CombinedSecurityOutcome.IDENTITY_CONFIRMED,
            decisionHeadline = "Confirmed",
            explanation = "Matched baseline",
            decisionFactors = listOf("Speaker matched", "Clean audio"),
            speakerVerification = com.echoshield.domain.model.SpeakerVerificationSummary(
                result = "VOICE_MATCHED",
                similarityScore = 0.88f,
                matchThreshold = 0.72f,
                nonMatchThreshold = 0.58f,
                decisionStatus = "high_match",
                explanation = "Matched."
            ),
            antiSpoofing = com.echoshield.domain.model.AntiSpoofSummary(
                resultCategory = "NO_SPOOF_SIGNAL_DETECTED",
                spoofScore = 0.05f,
                confidenceScore = 0.95f,
                scoreInterpretation = "Clean",
                replayDetectionSupported = false,
                replayLimitationNotice = "Notice"
            ),
            audioQuality = com.echoshield.domain.model.AcousticScreeningSummary(
                durationSeconds = 3.5f,
                sampleRate = 16000,
                snrDb = 20.0f,
                rmsEnergy = 0.04f,
                statusLabel = "EXCELLENT",
                isAcceptable = true
            ),
            limitations = listOf("Limitation"),
            isExperimental = true,
            analyzedAt = "2026-11-01T00:00:00Z",
            disclaimer = "Disclaimer"
        )
        return Result.success(result)
    }

    override suspend fun clearVerificationHistory(): Result<Int> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        return Result.success(0)
    }
}

/**
 * Fake implementation of [VoiceRemoteDataSource] for testing repository file cleanup and error handling.
 */
class FakeVoiceRemoteDataSource : VoiceRemoteDataSource {
    var shouldFail: Boolean = false
    var enrollCallCount: Int = 0

    override suspend fun recordConsent(granted: Boolean): VoiceConsentRecord {
        return VoiceConsentRecord("u1", if (granted) "granted" else "revoked", "1.0", "now")
    }

    override suspend fun enrollVoice(samples: List<File>): VoiceEnrollmentResult {
        enrollCallCount++
        if (shouldFail) {
            throw java.io.IOException("Simulated 503 Backend Service Unavailable")
        }
        return VoiceEnrollmentResult("u1", "enrolled", 0.95f, samples.size, "Enrollment successful")
    }

    override suspend fun getVoiceProfile(): VoiceProfileInfo {
        return VoiceProfileInfo("u1", "enrolled", true, 0.95f, 3, "SpeechBrain ECAPA-TDNN", "voxceleb-v1.1", "now", "granted")
    }

    override suspend fun deleteVoiceProfile() {}

    override suspend fun verifyVoice(memberId: String, audioFile: File): com.echoshield.domain.model.VoiceVerificationResult {
        throw NotImplementedError()
    }

    override suspend fun analyzeSpoof(audioFile: File, requestId: String?): com.echoshield.domain.model.AntiSpoofAnalysisResult {
        throw NotImplementedError()
    }

    override suspend fun analyzeSecurity(
        memberId: String,
        audioFile: File,
        requestId: String?
    ): com.echoshield.domain.model.CombinedSecurityAnalysisResult {
        throw NotImplementedError()
    }

    override suspend fun getVerificationHistory(): List<com.echoshield.domain.model.VerificationHistoryRecord> = emptyList()

    override suspend fun clearVerificationHistory(): Int = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeVoiceRepository
    private lateinit var viewModel: VoiceViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeVoiceRepository()
        viewModel = VoiceViewModel(voiceRepository = fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_NotEnrolledAndConsentPending() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isConsentGranted)
        assertEquals(1, state.currentSampleIndex)
        assertEquals(3, state.totalSamples)
        assertTrue(state.recordedSamples.isEmpty())
        assertNull(state.currentReviewSample)
    }

    @Test
    fun testRecordConsent_GrantedSuccessfully() = runTest(testDispatcher) {
        var callbackCalled = false
        viewModel.recordConsent(granted = true) {
            callbackCalled = true
        }
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertTrue(viewModel.uiState.value.isConsentGranted)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testRecordConsent_NetworkFailurePropagatesError() = runTest(testDispatcher) {
        fakeRepo.shouldFailNetwork = true
        var callbackCalled = false
        viewModel.recordConsent(granted = true) {
            callbackCalled = true
        }
        advanceUntilIdle()

        assertFalse(callbackCalled)
        assertFalse(viewModel.uiState.value.isConsentGranted)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testPermissionStateTransitions() = runTest(testDispatcher) {
        viewModel.setPermissionGranted(true)
        assertTrue(viewModel.uiState.value.isMicrophonePermissionGranted)
        assertFalse(viewModel.uiState.value.isPermissionPermanentlyDenied)

        viewModel.setPermissionPermanentlyDenied(true)
        assertFalse(viewModel.uiState.value.isMicrophonePermissionGranted)
        assertTrue(viewModel.uiState.value.isPermissionPermanentlyDenied)
    }

    @Test
    fun testSampleProgression_Sample1ToSample2ToSample3() = runTest(testDispatcher) {
        val dummyFile1 = File.createTempFile("sample_1", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        val dummyFile2 = File.createTempFile("sample_2", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }

        val sample1 = RecordedSample(1, dummyFile1, "prompt 1", 3.5f, 28f, 0.05f, isValid = true)
        viewModel.setSampleForReview(sample1, null)
        assertEquals(sample1, viewModel.uiState.value.currentReviewSample)

        var nextCalled = false
        viewModel.acceptCurrentSample(onProceedToNext = { nextCalled = true }, onAllSamplesCompleted = {})
        advanceUntilIdle()

        assertTrue(nextCalled)
        assertEquals(2, viewModel.uiState.value.currentSampleIndex)
        assertEquals(1, viewModel.uiState.value.recordedSamples.size)
        assertNull(viewModel.uiState.value.currentReviewSample)

        // Accept Sample 2
        val sample2 = RecordedSample(2, dummyFile2, "prompt 2", 3.2f, 30f, 0.06f, isValid = true)
        viewModel.setSampleForReview(sample2, null)

        var nextCalled2 = false
        viewModel.acceptCurrentSample(onProceedToNext = { nextCalled2 = true }, onAllSamplesCompleted = {})
        advanceUntilIdle()

        assertTrue(nextCalled2)
        assertEquals(3, viewModel.uiState.value.currentSampleIndex)
        assertEquals(2, viewModel.uiState.value.recordedSamples.size)
    }

    @Test
    fun testInvalidSampleRejectedFromAcceptance() = runTest(testDispatcher) {
        val dummyFile = File.createTempFile("sample_invalid", ".wav").apply { deleteOnExit() }
        val invalidSample = RecordedSample(1, dummyFile, "prompt", 1.0f, 0f, 0f, isValid = false)

        viewModel.setSampleForReview(invalidSample, "Sample too short.")
        var nextCalled = false
        viewModel.acceptCurrentSample(onProceedToNext = { nextCalled = true }, onAllSamplesCompleted = {})
        advanceUntilIdle()

        assertFalse(nextCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testFinalSampleSubmission_TriggersEnrollment() = runTest(testDispatcher) {
        val dummy1 = File.createTempFile("sample_1", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        val dummy2 = File.createTempFile("sample_2", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        val dummy3 = File.createTempFile("sample_3", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }

        // Sample 1
        viewModel.setSampleForReview(RecordedSample(1, dummy1, "p1", 3.5f, 25f, 0.04f, true), null)
        viewModel.acceptCurrentSample({}, {})
        advanceUntilIdle()

        // Sample 2
        viewModel.setSampleForReview(RecordedSample(2, dummy2, "p2", 3.5f, 25f, 0.04f, true), null)
        viewModel.acceptCurrentSample({}, {})
        advanceUntilIdle()

        // Sample 3 (Final)
        viewModel.setSampleForReview(RecordedSample(3, dummy3, "p3", 3.5f, 25f, 0.04f, true), null)
        var allDoneCalled = false
        viewModel.acceptCurrentSample(
            onProceedToNext = {},
            onAllSamplesCompleted = { allDoneCalled = true }
        )
        advanceUntilIdle()

        assertTrue(allDoneCalled)
        val profile = viewModel.uiState.value.voiceProfile
        assertNotNull(profile)
        assertTrue(profile!!.isVoiceEnrolled)
        assertEquals(0.96f, profile.voiceQualityScore)
    }

    @Test
    fun testDeleteVoiceProfile_ResetsState() = runTest(testDispatcher) {
        // Enroll first
        val dummy1 = File.createTempFile("sample_1", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        fakeRepo.enrollVoiceSamples(listOf(dummy1, dummy1, dummy1))
        viewModel.refreshVoiceProfile()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.voiceProfile?.isVoiceEnrolled == true)

        var deleteSuccess = false
        viewModel.deleteVoiceProfile {
            deleteSuccess = true
        }
        advanceUntilIdle()

        assertTrue(deleteSuccess)
        assertNull(viewModel.uiState.value.voiceProfile)
        assertFalse(viewModel.uiState.value.isConsentGranted)
    }

    @Test
    fun testAudioQualityValidator_ValidatesHeaderAndRejectsEmpty() {
        val emptyFile = File.createTempFile("empty", ".wav").apply { deleteOnExit() }
        val report = AudioQualityValidator.validateWavFile(emptyFile)
        assertFalse(report.isValid)
        assertTrue(report.errorMessage!!.contains("empty") || report.errorMessage!!.contains("missing"))
    }

    @Test
    fun testAudioQualityValidator_RejectsTooShortWav() {
        val shortWavFile = File.createTempFile("short", ".wav").apply { deleteOnExit() }
        writeSyntheticWav(shortWavFile, durationSeconds = 1.0f)

        val report = AudioQualityValidator.validateWavFile(shortWavFile)
        assertFalse(report.isValid)
        assertTrue(report.errorMessage!!.contains("too short"))
    }

    @Test
    fun testAudioQualityValidator_AcceptsValidWav() {
        val validWavFile = File.createTempFile("valid", ".wav").apply { deleteOnExit() }
        writeSyntheticWav(validWavFile, durationSeconds = 3.5f)

        val report = AudioQualityValidator.validateWavFile(validWavFile)
        assertTrue(report.isValid)
        assertNull(report.errorMessage)
        assertEquals(3.5f, report.durationSeconds, 0.1f)
    }

    private fun writeSyntheticWav(file: File, durationSeconds: Float) {
        val sampleRate = 16000
        val numSamples = (sampleRate * durationSeconds).toInt()
        val totalAudioLen = numSamples * 2
        val totalDataLen = totalAudioLen + 36

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // Mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(totalAudioLen)

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val pcm = ByteArray(totalAudioLen)
            val pcmBuf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numSamples) {
                // Sine wave 440 Hz
                val sampleVal = (0.2 * Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 32767.0).toInt().toShort()
                pcmBuf.putShort(sampleVal)
            }
            fos.write(pcm)
        }
    }

    @Test
    fun testPreFlightValidation_DetectsMissingSample1BeforeUpload() = runTest(testDispatcher) {
        val dummy1 = File.createTempFile("sample_1", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        val dummy2 = File.createTempFile("sample_2", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        val dummy3 = File.createTempFile("sample_3", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }

        // Accept Sample 1 & 2
        viewModel.setSampleForReview(RecordedSample(1, dummy1, "p1", 3.5f, 25f, 0.04f, true), null)
        viewModel.acceptCurrentSample({}, {})
        advanceUntilIdle()

        viewModel.setSampleForReview(RecordedSample(2, dummy2, "p2", 3.5f, 25f, 0.04f, true), null)
        viewModel.acceptCurrentSample({}, {})
        advanceUntilIdle()

        // Now simulate accidental deletion of Sample 1 (the exact ENOENT bug)
        dummy1.delete()
        assertFalse("Sample 1 file must be deleted to simulate ENOENT bug", dummy1.exists())

        // Try to accept final Sample 3
        viewModel.setSampleForReview(RecordedSample(3, dummy3, "p3", 3.5f, 25f, 0.04f, true), null)
        var allDoneCalled = false
        viewModel.acceptCurrentSample(
            onProceedToNext = {},
            onAllSamplesCompleted = { allDoneCalled = true }
        )
        advanceUntilIdle()

        // Submission MUST be prevented and user redirected to re-record missing Sample 1
        assertFalse("Submission should not succeed if Sample 1 was deleted", allDoneCalled)
        assertFalse(viewModel.uiState.value.isUploading)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Sample #1"))
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("missing or corrupted"))
        assertEquals("Should redirect user to re-record Sample 1", 1, viewModel.uiState.value.currentSampleIndex)
    }

    @Test
    fun testPreFlightValidation_DetectsCorrupted0ByteSample() = runTest(testDispatcher) {
        val dummy1 = File.createTempFile("sample_1", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        // Empty 0-byte file
        val dummy2 = File.createTempFile("sample_2", ".wav").apply { deleteOnExit() }
        val dummy3 = File.createTempFile("sample_3", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }

        viewModel.setSampleForReview(RecordedSample(1, dummy1, "p1", 3.5f, 25f, 0.04f, true), null)
        viewModel.acceptCurrentSample({}, {})
        advanceUntilIdle()

        // Try to accept 0-byte sample 2
        viewModel.setSampleForReview(RecordedSample(2, dummy2, "p2", 3.5f, 25f, 0.04f, true), null)
        var nextCalled = false
        viewModel.acceptCurrentSample(onProceedToNext = { nextCalled = true }, onAllSamplesCompleted = {})
        advanceUntilIdle()

        assertFalse("0-byte sample must not be accepted", nextCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Sample #2"))
    }

    @Test
    fun testUploadFailure_PreservesRecordedSamplesForRetry() = runTest(testDispatcher) {
        val dummy1 = File.createTempFile("sample_1", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        val dummy2 = File.createTempFile("sample_2", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }
        val dummy3 = File.createTempFile("sample_3", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
            deleteOnExit() 
        }

        fakeRepo.shouldFailNetwork = true

        viewModel.setSampleForReview(RecordedSample(1, dummy1, "p1", 3.5f, 25f, 0.04f, true), null)
        viewModel.acceptCurrentSample({}, {})
        viewModel.setSampleForReview(RecordedSample(2, dummy2, "p2", 3.5f, 25f, 0.04f, true), null)
        viewModel.acceptCurrentSample({}, {})
        viewModel.setSampleForReview(RecordedSample(3, dummy3, "p3", 3.5f, 25f, 0.04f, true), null)

        var allDoneCalled = false
        viewModel.acceptCurrentSample(
            onProceedToNext = {},
            onAllSamplesCompleted = { allDoneCalled = true }
        )
        advanceUntilIdle()

        assertFalse(allDoneCalled)
        assertFalse(viewModel.uiState.value.isUploading)
        assertNotNull(viewModel.uiState.value.errorMessage)
        // Samples must still be preserved in state so user doesn't have to re-record
        assertEquals(3, viewModel.uiState.value.recordedSamples.size)
        assertTrue(dummy1.exists())
        assertTrue(dummy2.exists())
        assertTrue(dummy3.exists())
    }

    @Test
    fun testCancelEnrollment_CleansUpAllRecordedFiles() = runTest(testDispatcher) {
        val dummy1 = File.createTempFile("sample_1", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
        }
        val dummy2 = File.createTempFile("sample_2", ".wav").apply { 
            writeSyntheticWav(this, 3.5f)
        }

        viewModel.setSampleForReview(RecordedSample(1, dummy1, "p1", 3.5f, 25f, 0.04f, true), null)
        viewModel.acceptCurrentSample({}, {})
        advanceUntilIdle()

        viewModel.setSampleForReview(RecordedSample(2, dummy2, "p2", 3.5f, 25f, 0.04f, true), null)
        advanceUntilIdle()

        assertTrue(dummy1.exists())
        assertTrue(dummy2.exists())

        viewModel.cancelEnrollment()
        advanceUntilIdle()

        assertFalse("Sample 1 file should be deleted on cancel", dummy1.exists())
        assertFalse("Sample 2 file should be deleted on cancel", dummy2.exists())
        assertEquals(1, viewModel.uiState.value.currentSampleIndex)
        assertTrue(viewModel.uiState.value.recordedSamples.isEmpty())
        assertNull(viewModel.uiState.value.currentReviewSample)
    }

    @Test
    fun testVoiceRepository_DeletesFilesOnlyOnSuccessfulEnrollment() = runTest(testDispatcher) {
        val fakeRemote = FakeVoiceRemoteDataSource()
        val repo = VoiceRepositoryImpl(remoteDataSource = fakeRemote)
        val f1 = File.createTempFile("repo_test_1", ".wav").apply { writeSyntheticWav(this, 3.5f) }
        val f2 = File.createTempFile("repo_test_2", ".wav").apply { writeSyntheticWav(this, 3.5f) }
        val f3 = File.createTempFile("repo_test_3", ".wav").apply { writeSyntheticWav(this, 3.5f) }

        assertTrue(f1.exists())
        assertTrue(f2.exists())
        assertTrue(f3.exists())

        val result = repo.enrollVoiceSamples(listOf(f1, f2, f3))
        assertTrue(result.isSuccess)
        assertEquals(1, fakeRemote.enrollCallCount)

        // Files must be deleted after successful upload
        assertFalse("File 1 must be deleted after successful enrollment", f1.exists())
        assertFalse("File 2 must be deleted after successful enrollment", f2.exists())
        assertFalse("File 3 must be deleted after successful enrollment", f3.exists())
    }

    @Test
    fun testVoiceRepository_PreservesFilesOnRemoteFailure() = runTest(testDispatcher) {
        val fakeRemote = FakeVoiceRemoteDataSource().apply { shouldFail = true }
        val repo = VoiceRepositoryImpl(remoteDataSource = fakeRemote)
        val f1 = File.createTempFile("repo_fail_1", ".wav").apply { writeSyntheticWav(this, 3.5f) }
        val f2 = File.createTempFile("repo_fail_2", ".wav").apply { writeSyntheticWav(this, 3.5f) }
        val f3 = File.createTempFile("repo_fail_3", ".wav").apply { writeSyntheticWav(this, 3.5f) }

        val result = repo.enrollVoiceSamples(listOf(f1, f2, f3))
        assertTrue(result.isFailure)

        // Files MUST NOT be deleted so retry can succeed
        assertTrue("File 1 must be kept on failure for retry", f1.exists())
        assertTrue("File 2 must be kept on failure for retry", f2.exists())
        assertTrue("File 3 must be kept on failure for retry", f3.exists())

        f1.delete()
        f2.delete()
        f3.delete()
    }

    @Test
    fun testVoiceRepository_RejectsMissingOrEmptyFileBeforeNetwork() = runTest(testDispatcher) {
        val fakeRemote = FakeVoiceRemoteDataSource()
        val repo = VoiceRepositoryImpl(remoteDataSource = fakeRemote)
        val f1 = File.createTempFile("repo_val_1", ".wav").apply { writeSyntheticWav(this, 3.5f) }
        val f2 = File.createTempFile("repo_val_2", ".wav").apply { writeSyntheticWav(this, 3.5f) }
        val nonExistent = File("missing_sample_sample_3_non_existent.wav")

        val result = repo.enrollVoiceSamples(listOf(f1, f2, nonExistent))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.FileNotFoundException)
        assertEquals("Network call must never be made when file is missing", 0, fakeRemote.enrollCallCount)

        f1.delete()
        f2.delete()
    }
}
