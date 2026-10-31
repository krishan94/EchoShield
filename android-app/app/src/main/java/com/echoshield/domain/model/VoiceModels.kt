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

