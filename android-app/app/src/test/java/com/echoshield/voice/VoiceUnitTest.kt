package com.echoshield.voice

import com.echoshield.domain.model.RecordedSample
import com.echoshield.domain.model.VoiceConsentRecord
import com.echoshield.domain.model.VoiceEnrollmentResult
import com.echoshield.domain.model.VoiceProfileInfo
import com.echoshield.domain.repository.VoiceRepository
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

    override suspend fun getVerificationHistory(): Result<List<com.echoshield.domain.model.VerificationHistoryRecord>> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        return Result.success(emptyList())
    }

    override suspend fun clearVerificationHistory(): Result<Int> {
        if (shouldFailNetwork) return Result.failure(Exception("Network unreachable"))
        return Result.success(0)
    }
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
        val dummyFile1 = File.createTempFile("sample_1", ".wav").apply { deleteOnExit() }
        val dummyFile2 = File.createTempFile("sample_2", ".wav").apply { deleteOnExit() }

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
        val dummy1 = File.createTempFile("sample_1", ".wav").apply { deleteOnExit() }
        val dummy2 = File.createTempFile("sample_2", ".wav").apply { deleteOnExit() }
        val dummy3 = File.createTempFile("sample_3", ".wav").apply { deleteOnExit() }

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
        val dummy1 = File.createTempFile("sample_1", ".wav").apply { deleteOnExit() }
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
}

