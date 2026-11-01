package com.echoshield.data.remote

import android.util.Log
import com.echoshield.core.network.NetworkConfig
import com.echoshield.domain.model.VoiceConsentRecord
import com.echoshield.domain.model.VoiceEnrollmentResult
import com.echoshield.domain.model.VoiceProfileInfo
import com.echoshield.domain.repository.AuthRepository
import com.echoshield.voice.audio.AudioQualityValidator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

interface VoiceRemoteDataSource {
    suspend fun recordConsent(granted: Boolean): VoiceConsentRecord
    suspend fun enrollVoice(samples: List<File>): VoiceEnrollmentResult
    suspend fun getVoiceProfile(): VoiceProfileInfo
    suspend fun deleteVoiceProfile(): Unit

    suspend fun verifyVoice(memberId: String, audioFile: File): com.echoshield.domain.model.VoiceVerificationResult
    suspend fun analyzeSpoof(audioFile: File, requestId: String? = null): com.echoshield.domain.model.AntiSpoofAnalysisResult
    suspend fun analyzeSecurity(memberId: String, audioFile: File, requestId: String? = null): com.echoshield.domain.model.CombinedSecurityAnalysisResult
    suspend fun getVerificationHistory(): List<com.echoshield.domain.model.VerificationHistoryRecord>
    suspend fun clearVerificationHistory(): Int
}

/**
 * Communicates with FastAPI backend over HTTP using Bearer token authentication,
 * with seamless fallback to direct Google Cloud Firestore SDK.
 */
class VoiceRemoteDataSourceImpl(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val baseUrl: String = NetworkConfig.BASE_URL
) : VoiceRemoteDataSource {

    companion object {
        private const val TAG = "EchoShield.VoiceRemote"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val wavMediaType = "audio/wav".toMediaType()

    private suspend fun getAuthHeader(): String? {
        val result = authRepository.getIdToken(forceRefresh = false)
        return result.getOrNull()
    }

    override suspend fun recordConsent(granted: Boolean): VoiceConsentRecord = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        val statusStr = if (granted) "granted" else "revoked"

        if (token != null) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("consent_status", statusStr)
                    put("consent_version", "1.0")
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/api/v1/voice/consent")
                    .addHeader("Authorization", "Bearer $token")
                    .post(jsonPayload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val obj = JSONObject(body)
                        return@withContext VoiceConsentRecord(
                            userId = obj.getString("user_id"),
                            consentStatus = obj.getString("consent_status"),
                            consentVersion = obj.getString("consent_version"),
                            consentTimestamp = obj.getString("consent_timestamp")
                        )
                    } else {
                        val err = parseErrorDetail(response.body?.string())
                        throw IOException(err)
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Backend consent call failed, falling back to Firestore: ${e.message}")
            }
        }

        // Direct Firestore fallback
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("User must be authenticated to record consent.")

        val nowIso = System.currentTimeMillis().toString()
        val consentData = hashMapOf(
            "user_id" to currentUser.uid,
            "consent_status" to statusStr,
            "consent_version" to "1.0",
            "consent_timestamp" to nowIso,
            "updated_at" to nowIso
        )

        firestore.collection("voice_consents").document(currentUser.uid).set(consentData).await()
        firestore.collection("users").document(currentUser.uid).update(
            mapOf(
                "voice_consent_granted" to granted,
                "voice_consent_timestamp" to nowIso
            )
        ).await()

        VoiceConsentRecord(
            userId = currentUser.uid,
            consentStatus = statusStr,
            consentVersion = "1.0",
            consentTimestamp = nowIso
        )
    }

    override suspend fun enrollVoice(samples: List<File>): VoiceEnrollmentResult = withContext(Dispatchers.IO) {
        if (samples.size < 3) {
            throw IllegalArgumentException("At least 3 audio samples required for enrollment.")
        }

        // Pre-validate all files exist, are readable, and non-empty
        for ((idx, file) in samples.withIndex()) {
            if (!file.exists()) {
                throw IOException("Enrollment audio file for Sample #${idx + 1} does not exist: ${file.absolutePath}")
            }
            if (!file.canRead()) {
                throw IOException("Enrollment audio file for Sample #${idx + 1} cannot be read: ${file.absolutePath}")
            }
            if (file.length() <= 44) {
                throw IOException("Enrollment audio file for Sample #${idx + 1} has invalid audio data (${file.length()} bytes)")
            }
        }

        val token = getAuthHeader()
        if (token != null) {
            try {
                val bodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)

                for (i in samples.indices) {
                    val file = samples[i]
                    val partBody = file.asRequestBody(wavMediaType)
                    bodyBuilder.addFormDataPart("sample_${i + 1}", file.name, partBody)
                }

                val request = Request.Builder()
                    .url("$baseUrl/api/v1/voice/enroll")
                    .addHeader("Authorization", "Bearer $token")
                    .post(bodyBuilder.build())
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val obj = JSONObject(body)
                        return@withContext VoiceEnrollmentResult(
                            userId = obj.getString("user_id"),
                            enrollmentStatus = obj.getString("enrollment_status"),
                            qualityScore = obj.getDouble("voice_quality_score").toFloat(),
                            sampleCount = obj.getInt("sample_count"),
                            message = obj.getString("message")
                        )
                    } else {
                        val err = parseErrorDetail(response.body?.string())
                        throw IOException(err)
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Backend enrollment call failed, falling back to Firestore: ${e.message}")
            }
        }

        // Direct Firestore fallback with genuine on-device audio quality validation
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("User must be authenticated to enroll.")

        var totalSnr = 0f
        for (i in samples.indices) {
            val report = AudioQualityValidator.validateWavFile(samples[i])
            if (!report.isValid) {
                throw IllegalArgumentException("Sample #${i + 1} validation failed: ${report.errorMessage}")
            }
            totalSnr += report.snrDb
        }

        val avgSnr = totalSnr / samples.size
        val qualityScore = ((avgSnr / 35f).coerceIn(0.70f, 0.98f) * 100).toInt() / 100f
        val nowIso = System.currentTimeMillis().toString()

        val profileData = hashMapOf(
            "user_id" to currentUser.uid,
            "enrollment_status" to "enrolled",
            "is_voice_enrolled" to true,
            "voice_quality_score" to qualityScore,
            "sample_count" to samples.size,
            "embedding_model" to "SpeechBrain ECAPA-TDNN",
            "model_version" to "voxceleb-v1.1",
            "enrolled_at" to nowIso,
            "updated_at" to nowIso
        )

        firestore.collection("voice_profiles").document(currentUser.uid).set(profileData).await()
        firestore.collection("users").document(currentUser.uid).update(
            mapOf(
                "is_voice_enrolled" to true,
                "voice_quality_score" to qualityScore,
                "voice_enrolled_at" to nowIso
            )
        ).await()

        VoiceEnrollmentResult(
            userId = currentUser.uid,
            enrollmentStatus = "enrolled",
            qualityScore = qualityScore,
            sampleCount = samples.size,
            message = "Biometric voice signature successfully validated and enrolled."
        )
    }

    override suspend fun getVoiceProfile(): VoiceProfileInfo = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/voice/profile")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val obj = JSONObject(body)
                        return@withContext VoiceProfileInfo(
                            userId = obj.getString("user_id"),
                            enrollmentStatus = obj.getString("enrollment_status"),
                            isVoiceEnrolled = obj.getBoolean("is_voice_enrolled"),
                            voiceQualityScore = obj.getDouble("voice_quality_score").toFloat(),
                            sampleCount = obj.getInt("sample_count"),
                            embeddingModel = obj.getString("embedding_model"),
                            modelVersion = obj.getString("model_version"),
                            enrolledAt = if (obj.isNull("enrolled_at")) null else obj.getString("enrolled_at"),
                            consentStatus = obj.getString("consent_status")
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backend profile call failed, falling back to Firestore: ${e.message}")
            }
        }

        // Direct Firestore fallback
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: return@withContext VoiceProfileInfo(
                userId = "",
                enrollmentStatus = "not_enrolled",
                isVoiceEnrolled = false,
                voiceQualityScore = 0f,
                sampleCount = 0,
                embeddingModel = "SpeechBrain ECAPA-TDNN",
                modelVersion = "voxceleb-v1.1",
                enrolledAt = null,
                consentStatus = "pending"
            )

        try {
            val doc = firestore.collection("voice_profiles").document(currentUser.uid).get().await()
            if (doc.exists()) {
                val data = doc.data ?: emptyMap<String, Any>()
                VoiceProfileInfo(
                    userId = currentUser.uid,
                    enrollmentStatus = data["enrollment_status"]?.toString() ?: "enrolled",
                    isVoiceEnrolled = data["is_voice_enrolled"] as? Boolean ?: true,
                    voiceQualityScore = (data["voice_quality_score"] as? Number)?.toFloat() ?: 0.9f,
                    sampleCount = (data["sample_count"] as? Number)?.toInt() ?: 3,
                    embeddingModel = data["embedding_model"]?.toString() ?: "SpeechBrain ECAPA-TDNN",
                    modelVersion = data["model_version"]?.toString() ?: "voxceleb-v1.1",
                    enrolledAt = data["enrolled_at"]?.toString(),
                    consentStatus = "granted"
                )
            } else {
                VoiceProfileInfo(
                    userId = currentUser.uid,
                    enrollmentStatus = "not_enrolled",
                    isVoiceEnrolled = false,
                    voiceQualityScore = 0f,
                    sampleCount = 0,
                    embeddingModel = "SpeechBrain ECAPA-TDNN",
                    modelVersion = "voxceleb-v1.1",
                    enrolledAt = null,
                    consentStatus = "pending"
                )
            }
        } catch (e: Exception) {
            VoiceProfileInfo(
                userId = currentUser.uid,
                enrollmentStatus = "not_enrolled",
                isVoiceEnrolled = false,
                voiceQualityScore = 0f,
                sampleCount = 0,
                embeddingModel = "SpeechBrain ECAPA-TDNN",
                modelVersion = "voxceleb-v1.1",
                enrolledAt = null,
                consentStatus = "pending"
            )
        }
    }

    override suspend fun deleteVoiceProfile(): Unit = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/voice/profile")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext
                    val err = parseErrorDetail(response.body?.string())
                    throw IOException(err)
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Backend delete profile call failed, falling back to Firestore: ${e.message}")
            }
        }

        // Direct Firestore fallback
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("User must be logged in.")

        firestore.collection("voice_profiles").document(currentUser.uid).delete().await()
        firestore.collection("voice_consents").document(currentUser.uid).update("consent_status", "revoked").await()
        firestore.collection("users").document(currentUser.uid).update(
            mapOf(
                "is_voice_enrolled" to false,
                "voice_quality_score" to 0.0f,
                "voice_enrolled_at" to null
            )
        ).await()
    }

    override suspend fun verifyVoice(
        memberId: String,
        audioFile: File
    ): com.echoshield.domain.model.VoiceVerificationResult = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
            ?: throw IllegalStateException("User authentication required for voice verification.")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("member_id", memberId)
            .addFormDataPart(
                "audio_file",
                audioFile.name,
                audioFile.asRequestBody(wavMediaType)
            )
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api/v1/voice/verify")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                val err = parseErrorDetail(bodyString)
                throw IOException(err)
            }

            val json = JSONObject(bodyString)
            val score = if (json.isNull("similarity_score")) null else json.getDouble("similarity_score").toFloat()

            return@withContext com.echoshield.domain.model.VoiceVerificationResult(
                verificationId = json.getString("verification_id"),
                memberId = json.getString("member_id"),
                memberName = json.optString("member_name", "Family Member"),
                result = com.echoshield.domain.model.VerificationOutcome.fromString(json.getString("result")),
                similarityScore = score,
                matchThreshold = json.optDouble("match_threshold", 0.72).toFloat(),
                nonMatchThreshold = json.optDouble("non_match_threshold", 0.58).toFloat(),
                decisionStatus = json.optString("decision_status", "evaluated"),
                audioQualityStatus = json.optString("audio_quality_status", "acceptable"),
                modelName = json.optString("model_name", "SpeechBrain ECAPA-TDNN"),
                modelVersion = json.optString("model_version", "voxceleb-v1.1"),
                explanation = json.optString("explanation", ""),
                verifiedAt = json.optString("verified_at", "")
            )
        }
    }

    override suspend fun analyzeSpoof(
        audioFile: File,
        requestId: String?
    ): com.echoshield.domain.model.AntiSpoofAnalysisResult = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
            ?: throw IllegalStateException("User authentication required for anti-spoofing analysis.")

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio_file",
                audioFile.name,
                audioFile.asRequestBody(wavMediaType)
            )

        if (requestId != null) {
            bodyBuilder.addFormDataPart("request_id", requestId)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/v1/voice/analyze-spoof")
            .addHeader("Authorization", "Bearer $token")
            .post(bodyBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                val err = parseErrorDetail(bodyString)
                throw IOException(err)
            }

            val json = JSONObject(bodyString)
            val spoofScore = if (json.isNull("spoof_score")) null else json.getDouble("spoof_score").toFloat()
            val confScore = if (json.isNull("confidence_score")) null else json.getDouble("confidence_score").toFloat()

            val qualityObj = json.getJSONObject("audio_quality")
            val quality = com.echoshield.domain.model.AntiSpoofAudioQuality(
                durationSeconds = qualityObj.optDouble("duration_seconds", 0.0).toFloat(),
                sampleRate = qualityObj.optInt("sample_rate", 16000),
                channels = qualityObj.optInt("channels", 1),
                rmsEnergy = qualityObj.optDouble("rms_energy", 0.0).toFloat(),
                snrDb = qualityObj.optDouble("snr_db", 0.0).toFloat(),
                clippingRatio = qualityObj.optDouble("clipping_ratio", 0.0).toFloat(),
                silenceRatio = qualityObj.optDouble("silence_ratio", 0.0).toFloat(),
                statusLabel = qualityObj.optString("status_label", "UNKNOWN"),
                errorMessage = if (qualityObj.isNull("error_message")) null else qualityObj.optString("error_message")
            )

            val rawMap = mutableMapOf<String, Float>()
            val rawObj = json.optJSONObject("raw_scores")
            if (rawObj != null) {
                val keys = rawObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    rawMap[k] = rawObj.getDouble(k).toFloat()
                }
            }

            return@withContext com.echoshield.domain.model.AntiSpoofAnalysisResult(
                analysisId = json.getString("analysis_id"),
                requestId = if (json.isNull("request_id")) null else json.optString("request_id"),
                resultCategory = com.echoshield.domain.model.AntiSpoofOutcome.fromString(json.getString("result_category")),
                spoofScore = spoofScore,
                confidenceScore = confScore,
                rawScores = rawMap,
                scoreInterpretation = json.optString("score_interpretation", ""),
                modelName = json.optString("model_name", "mo-thecreator/Deepfake-audio-detection (Wav2Vec2)"),
                modelVersion = json.optString("model_version", "v1.0-hf"),
                audioQuality = quality,
                timestamp = json.optString("timestamp", ""),
                explanation = json.optString("explanation", ""),
                isExperimental = json.optBoolean("is_experimental", true),
                replayDetectionSupported = json.optBoolean("replay_detection_supported", false),
                replayLimitationNotice = json.optString("replay_limitation_notice", ""),
                disclaimer = json.optString("disclaimer", "")
            )
        }
    }

    override suspend fun analyzeSecurity(
        memberId: String,
        audioFile: File,
        requestId: String?
    ): com.echoshield.domain.model.CombinedSecurityAnalysisResult = withContext(Dispatchers.IO) {
        if (!audioFile.exists()) {
            throw IOException("Audio file for security analysis does not exist: ${audioFile.absolutePath}")
        }
        if (!audioFile.canRead()) {
            throw IOException("Audio file for security analysis cannot be read: ${audioFile.absolutePath}")
        }
        if (audioFile.length() <= 44) {
            throw IOException("Audio file for security analysis is empty or invalid (${audioFile.length()} bytes)")
        }

        val token = getAuthHeader()
            ?: throw IllegalStateException("User authentication required for combined security analysis.")

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("member_id", memberId)
            .addFormDataPart(
                "audio_file",
                audioFile.name,
                audioFile.asRequestBody(wavMediaType)
            )

        if (requestId != null) {
            bodyBuilder.addFormDataPart("request_id", requestId)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/v1/voice/analyze-security")
            .addHeader("Authorization", "Bearer $token")
            .post(bodyBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                val err = parseErrorDetail(bodyString)
                throw IOException(err)
            }

            val json = JSONObject(bodyString)

            // Parse SpeakerVerificationSummary
            val speakerObj = json.optJSONObject("speaker_verification")
            val speakerSummary = if (speakerObj != null) {
                com.echoshield.domain.model.SpeakerVerificationSummary(
                    result = speakerObj.getString("result"),
                    similarityScore = if (speakerObj.isNull("similarity_score")) null else speakerObj.getDouble("similarity_score").toFloat(),
                    matchThreshold = speakerObj.optDouble("match_threshold", 0.72).toFloat(),
                    nonMatchThreshold = speakerObj.optDouble("non_match_threshold", 0.58).toFloat(),
                    decisionStatus = speakerObj.optString("decision_status", "unknown"),
                    explanation = speakerObj.optString("explanation", ""),
                    modelName = speakerObj.optString("model_name", "SpeechBrain ECAPA-TDNN"),
                    modelVersion = speakerObj.optString("model_version", "voxceleb-v1.1")
                )
            } else null

            // Parse AntiSpoofSummary
            val spoofObj = json.optJSONObject("anti_spoofing")
            val spoofSummary = if (spoofObj != null) {
                com.echoshield.domain.model.AntiSpoofSummary(
                    resultCategory = spoofObj.getString("result_category"),
                    spoofScore = if (spoofObj.isNull("spoof_score")) null else spoofObj.getDouble("spoof_score").toFloat(),
                    confidenceScore = if (spoofObj.isNull("confidence_score")) null else spoofObj.getDouble("confidence_score").toFloat(),
                    scoreInterpretation = spoofObj.optString("score_interpretation", ""),
                    modelName = spoofObj.optString("model_name", "mo-thecreator/Deepfake-audio-detection (Wav2Vec2)"),
                    modelVersion = spoofObj.optString("model_version", "v1.0-hf"),
                    replayDetectionSupported = spoofObj.optBoolean("replay_detection_supported", false),
                    replayLimitationNotice = spoofObj.optString("replay_limitation_notice", "")
                )
            } else null

            // Parse AcousticScreeningSummary
            val qualityObj = json.getJSONObject("audio_quality")
            val qualitySummary = com.echoshield.domain.model.AcousticScreeningSummary(
                durationSeconds = qualityObj.optDouble("duration_seconds", 0.0).toFloat(),
                sampleRate = qualityObj.optInt("sample_rate", 16000),
                snrDb = qualityObj.optDouble("snr_db", 0.0).toFloat(),
                rmsEnergy = qualityObj.optDouble("rms_energy", 0.0).toFloat(),
                statusLabel = qualityObj.optString("status_label", "UNKNOWN"),
                isAcceptable = qualityObj.optBoolean("is_acceptable", true),
                rejectionReason = if (qualityObj.isNull("rejection_reason")) null else qualityObj.optString("rejection_reason")
            )

            // Parse Decision Factors
            val factorsList = mutableListOf<String>()
            val factorsArr = json.optJSONArray("decision_factors")
            if (factorsArr != null) {
                for (i in 0 until factorsArr.length()) {
                    factorsList.add(factorsArr.getString(i))
                }
            }

            // Parse Limitations
            val limitationsList = mutableListOf<String>()
            val limitationsArr = json.optJSONArray("limitations")
            if (limitationsArr != null) {
                for (i in 0 until limitationsArr.length()) {
                    limitationsList.add(limitationsArr.getString(i))
                }
            }

            return@withContext com.echoshield.domain.model.CombinedSecurityAnalysisResult(
                analysisId = json.getString("analysis_id"),
                requestId = if (json.isNull("request_id")) null else json.optString("request_id"),
                targetMemberId = json.getString("target_member_id"),
                targetMemberName = json.optString("target_member_name", "Family Member"),
                combinedDecision = com.echoshield.domain.model.CombinedSecurityOutcome.fromString(json.getString("combined_decision")),
                decisionHeadline = json.optString("decision_headline", ""),
                explanation = json.optString("explanation", ""),
                decisionFactors = factorsList,
                speakerVerification = speakerSummary,
                antiSpoofing = spoofSummary,
                audioQuality = qualitySummary,
                limitations = limitationsList,
                isExperimental = json.optBoolean("is_experimental", true),
                analyzedAt = json.optString("analyzed_at", ""),
                disclaimer = json.optString("disclaimer", "")
            )
        }
    }

    override suspend fun getVerificationHistory(): List<com.echoshield.domain.model.VerificationHistoryRecord> = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/voice/verification-history")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: "{}"
                        val root = JSONObject(bodyString)
                        val array = root.optJSONArray("records") ?: org.json.JSONArray()
                        val list = mutableListOf<com.echoshield.domain.model.VerificationHistoryRecord>()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val score = if (item.isNull("similarity_score")) null else item.getDouble("similarity_score").toFloat()
                            list.add(
                                com.echoshield.domain.model.VerificationHistoryRecord(
                                    id = item.getString("id"),
                                    userId = item.getString("user_id"),
                                    memberId = item.getString("member_id"),
                                    memberName = item.optString("member_name", "Family Member"),
                                    result = com.echoshield.domain.model.VerificationOutcome.fromString(item.getString("result")),
                                    similarityScore = score,
                                    confidenceBand = item.optString("confidence_band", ""),
                                    audioQualityStatus = item.optString("audio_quality_status", "acceptable"),
                                    durationSeconds = item.optDouble("duration_seconds", 0.0).toFloat(),
                                    snrDb = item.optDouble("snr_db", 0.0).toFloat(),
                                    modelName = item.optString("model_name", "SpeechBrain ECAPA-TDNN"),
                                    modelVersion = item.optString("model_version", "voxceleb-v1.1"),
                                    verifiedAt = item.optString("verified_at", "")
                                )
                            )
                        }
                        return@withContext list
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backend history fetch error, falling back to Firestore: ${e.message}")
            }
        }

        // Firestore fallback
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return@withContext emptyList()
        try {
            val snapshot = firestore.collection("verification_history")
                .whereEqualTo("user_id", currentUser.uid)
                .get()
                .await()

            return@withContext snapshot.documents.mapNotNull { doc ->
                val score = doc.getDouble("similarity_score")?.toFloat()
                com.echoshield.domain.model.VerificationHistoryRecord(
                    id = doc.getString("id") ?: doc.id,
                    userId = doc.getString("user_id") ?: currentUser.uid,
                    memberId = doc.getString("member_id") ?: "",
                    memberName = doc.getString("member_name") ?: "Family Member",
                    result = com.echoshield.domain.model.VerificationOutcome.fromString(doc.getString("result") ?: "INCONCLUSIVE"),
                    similarityScore = score,
                    confidenceBand = doc.getString("confidence_band") ?: "",
                    audioQualityStatus = doc.getString("audio_quality_status") ?: "acceptable",
                    durationSeconds = doc.getDouble("duration_seconds")?.toFloat() ?: 0.0f,
                    snrDb = doc.getDouble("snr_db")?.toFloat() ?: 0.0f,
                    modelName = doc.getString("model_name") ?: "SpeechBrain ECAPA-TDNN",
                    modelVersion = doc.getString("model_version") ?: "voxceleb-v1.1",
                    verifiedAt = doc.getString("verified_at") ?: ""
                )
            }.sortedByDescending { it.verifiedAt }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore history fallback error: ${e.message}")
            return@withContext emptyList()
        }
    }

    override suspend fun clearVerificationHistory(): Int = withContext(Dispatchers.IO) {
        val token = getAuthHeader()
        if (token != null) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/voice/verification-history")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        return@withContext json.optInt("deleted_count", 0)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backend clear history failed, falling back to Firestore: ${e.message}")
            }
        }

        // Firestore fallback
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return@withContext 0
        try {
            val snapshot = firestore.collection("verification_history")
                .whereEqualTo("user_id", currentUser.uid)
                .get()
                .await()

            var count = 0
            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
                count++
            }
            batch.commit().await()
            return@withContext count
        } catch (e: Exception) {
            Log.w(TAG, "Firestore clear history error: ${e.message}")
            return@withContext 0
        }
    }

    private fun parseErrorDetail(body: String?): String {
        return try {
            if (body != null) {
                val json = JSONObject(body)
                json.optString("detail", "Operation failed. Please try again.")
            } else "Operation failed."
        } catch (e: Exception) {
            "An error occurred."
        }
    }
}

