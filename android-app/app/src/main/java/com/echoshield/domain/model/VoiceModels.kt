package com.echoshield.domain.model

import java.io.File

data class VoiceConsentRecord(
    val userId: String,
    val consentStatus: String,
    val consentVersion: String,
    val consentTimestamp: String
)

data class VoiceProfileInfo(
    val userId: String,
    val enrollmentStatus: String,
    val isVoiceEnrolled: Boolean,
    val voiceQualityScore: Float,
    val sampleCount: Int,
    val embeddingModel: String,
    val modelVersion: String,
    val enrolledAt: String?,
    val consentStatus: String
)

data class RecordedSample(
    val sampleIndex: Int,
    val file: File,
    val prompt: String,
    val durationSeconds: Float,
    val snrDb: Float,
    val rmsEnergy: Float,
    val isValid: Boolean
)

data class VoiceEnrollmentResult(
    val userId: String,
    val enrollmentStatus: String,
    val qualityScore: Float,
    val sampleCount: Int,
    val message: String
)

enum class VerificationOutcome {
    VOICE_MATCHED,
    NO_MATCH,
    INCONCLUSIVE,
    AUDIO_QUALITY_FAILED,
    PROFILE_NOT_ENROLLED;

    companion object {
        fun fromString(value: String): VerificationOutcome = when (value.uppercase()) {
            "VOICE_MATCHED" -> VOICE_MATCHED
            "NO_MATCH" -> NO_MATCH
            "INCONCLUSIVE" -> INCONCLUSIVE
            "AUDIO_QUALITY_FAILED" -> AUDIO_QUALITY_FAILED
            "PROFILE_NOT_ENROLLED" -> PROFILE_NOT_ENROLLED
            else -> INCONCLUSIVE
        }
    }
}

data class VoiceVerificationResult(
    val verificationId: String,
    val memberId: String,
    val memberName: String,
    val result: VerificationOutcome,
    val similarityScore: Float?,
    val matchThreshold: Float = 0.72f,
    val nonMatchThreshold: Float = 0.58f,
    val decisionStatus: String,
    val audioQualityStatus: String,
    val modelName: String = "SpeechBrain ECAPA-TDNN",
    val modelVersion: String = "voxceleb-v1.1",
    val explanation: String,
    val verifiedAt: String
)

data class VerificationHistoryRecord(
    val id: String,
    val userId: String,
    val memberId: String,
    val memberName: String,
    val result: VerificationOutcome,
    val similarityScore: Float?,
    val confidenceBand: String,
    val audioQualityStatus: String,
    val durationSeconds: Float,
    val snrDb: Float,
    val modelName: String,
    val modelVersion: String,
    val verifiedAt: String
)

enum class AntiSpoofOutcome {
    SPOOF_SUSPECTED,
    NO_SPOOF_SIGNAL_DETECTED,
    INCONCLUSIVE,
    AUDIO_QUALITY_FAILED,
    MODEL_UNAVAILABLE;

    companion object {
        fun fromString(value: String): AntiSpoofOutcome = when (value.uppercase()) {
            "SPOOF_SUSPECTED" -> SPOOF_SUSPECTED
            "NO_SPOOF_SIGNAL_DETECTED" -> NO_SPOOF_SIGNAL_DETECTED
            "INCONCLUSIVE" -> INCONCLUSIVE
            "AUDIO_QUALITY_FAILED" -> AUDIO_QUALITY_FAILED
            "MODEL_UNAVAILABLE" -> MODEL_UNAVAILABLE
            else -> INCONCLUSIVE
        }
    }
}

data class AntiSpoofAudioQuality(
    val durationSeconds: Float,
    val sampleRate: Int,
    val channels: Int,
    val rmsEnergy: Float,
    val snrDb: Float,
    val clippingRatio: Float,
    val silenceRatio: Float,
    val statusLabel: String,
    val errorMessage: String? = null
)

data class AntiSpoofAnalysisResult(
    val analysisId: String,
    val requestId: String? = null,
    val resultCategory: AntiSpoofOutcome,
    val spoofScore: Float?,
    val confidenceScore: Float?,
    val rawScores: Map<String, Float> = emptyMap(),
    val scoreInterpretation: String,
    val modelName: String = "mo-thecreator/Deepfake-audio-detection (Wav2Vec2)",
    val modelVersion: String = "v1.0-hf",
    val audioQuality: AntiSpoofAudioQuality,
    val timestamp: String,
    val explanation: String,
    val isExperimental: Boolean = true,
    val replayDetectionSupported: Boolean = false,
    val replayLimitationNotice: String,
    val disclaimer: String
)


// ============================================================================
// PHASE 7: COMBINED SECURITY DECISION ENGINE MODELS
// ============================================================================

enum class CombinedSecurityOutcome {
    IDENTITY_CONFIRMED,
    SUSPICIOUS_AUDIO,
    IDENTITY_NOT_CONFIRMED,
    INCONCLUSIVE,
    ANALYSIS_UNAVAILABLE,
    AUDIO_QUALITY_FAILED,
    PROFILE_NOT_ENROLLED;

    companion object {
        fun fromString(value: String): CombinedSecurityOutcome = when (value.uppercase()) {
            "IDENTITY_CONFIRMED" -> IDENTITY_CONFIRMED
            "SUSPICIOUS_AUDIO" -> SUSPICIOUS_AUDIO
            "IDENTITY_NOT_CONFIRMED" -> IDENTITY_NOT_CONFIRMED
            "INCONCLUSIVE" -> INCONCLUSIVE
            "ANALYSIS_UNAVAILABLE" -> ANALYSIS_UNAVAILABLE
            "AUDIO_QUALITY_FAILED" -> AUDIO_QUALITY_FAILED
            "PROFILE_NOT_ENROLLED" -> PROFILE_NOT_ENROLLED
            else -> INCONCLUSIVE
        }
    }
}

data class SpeakerVerificationSummary(
    val result: String,
    val similarityScore: Float?,
    val matchThreshold: Float = 0.72f,
    val nonMatchThreshold: Float = 0.58f,
    val decisionStatus: String,
    val explanation: String,
    val modelName: String = "SpeechBrain ECAPA-TDNN",
    val modelVersion: String = "voxceleb-v1.1"
)

data class AntiSpoofSummary(
    val resultCategory: String,
    val spoofScore: Float?,
    val confidenceScore: Float?,
    val scoreInterpretation: String,
    val modelName: String = "mo-thecreator/Deepfake-audio-detection (Wav2Vec2)",
    val modelVersion: String = "v1.0-hf",
    val replayDetectionSupported: Boolean = false,
    val replayLimitationNotice: String
)

data class AcousticScreeningSummary(
    val durationSeconds: Float,
    val sampleRate: Int = 16000,
    val snrDb: Float,
    val rmsEnergy: Float,
    val statusLabel: String,
    val isAcceptable: Boolean,
    val rejectionReason: String? = null
)

data class CombinedSecurityAnalysisResult(
    val analysisId: String,
    val requestId: String? = null,
    val targetMemberId: String,
    val targetMemberName: String,
    val combinedDecision: CombinedSecurityOutcome,
    val decisionHeadline: String,
    val explanation: String,
    val decisionFactors: List<String> = emptyList(),
    val speakerVerification: SpeakerVerificationSummary? = null,
    val antiSpoofing: AntiSpoofSummary? = null,
    val audioQuality: AcousticScreeningSummary,
    val limitations: List<String> = emptyList(),
    val isExperimental: Boolean = true,
    val analyzedAt: String,
    val disclaimer: String
)
