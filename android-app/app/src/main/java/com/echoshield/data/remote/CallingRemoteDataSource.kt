package com.echoshield.data.remote

import com.echoshield.core.network.NetworkConfig
import com.echoshield.domain.model.AuthResult
import com.echoshield.domain.model.CallHistoryRecord
import com.echoshield.domain.model.CallReport
import com.echoshield.domain.model.CallSessionInfo
import com.echoshield.domain.model.CallStatus
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface CallingRemoteDataSource {
    suspend fun getCallHistory(limit: Int = 50): List<CallHistoryRecord>
    suspend fun getCallReport(callId: String): CallReport
    suspend fun saveCallReport(report: CallReport): CallReport
    suspend fun getCallStatus(callId: String): CallSessionInfo
}

class CallingRemoteDataSourceImpl(
    private val authRepository: AuthRepository,
    private val baseUrl: String = NetworkConfig.BASE_URL
) : CallingRemoteDataSource {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun getAuthHeader(): String {
        return when (val tokenResult = authRepository.getIdToken()) {
            is AuthResult.Success -> "Bearer ${tokenResult.data}"
            is AuthResult.Error -> throw IOException("Authentication error: ${tokenResult.message}")
        }
    }

    override suspend fun getCallHistory(limit: Int): List<CallHistoryRecord> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val url = "$baseUrl/api/v1/calling/history?limit=$limit"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", authHeader)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Server returned HTTP ${response.code}: $errorBody")
            }

            val body = response.body?.string() ?: throw IOException("Empty response from calling history")
            val json = JSONObject(body)
            val callsArray = json.optJSONArray("calls") ?: JSONArray()
            val result = mutableListOf<CallHistoryRecord>()

            for (i in 0 until callsArray.length()) {
                val obj = callsArray.getJSONObject(i)
                val outcomeStr = obj.optString("security_outcome", "")
                val outcome = if (outcomeStr.isNotEmpty()) CombinedSecurityOutcome.fromString(outcomeStr) else null

                result.add(
                    CallHistoryRecord(
                        callId = obj.getString("call_id"),
                        callerId = obj.getString("caller_id"),
                        callerName = obj.optString("caller_name", "Family Member"),
                        calleeId = obj.getString("callee_id"),
                        calleeName = obj.optString("callee_name", "Family Member"),
                        direction = obj.optString("direction", "incoming"),
                        otherPartyName = obj.optString("other_party_name", "Family Member"),
                        otherPartyId = obj.optString("other_party_id", ""),
                        status = CallStatus.fromString(obj.optString("status", "CONNECTED")),
                        durationSeconds = obj.optInt("duration_seconds", 0),
                        securityOutcome = outcome,
                        securityHeadline = if (obj.isNull("security_headline")) null else obj.optString("security_headline"),
                        hasReport = obj.optBoolean("has_report", false),
                        createdAt = obj.optString("created_at", "")
                    )
                )
            }
            result
        }
    }

    override suspend fun getCallReport(callId: String): CallReport = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val url = "$baseUrl/api/v1/calling/reports/$callId"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", authHeader)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Failed to fetch call report (${response.code}): $errorBody")
            }

            val body = response.body?.string() ?: throw IOException("Empty response for call report")
            parseCallReportJson(JSONObject(body))
        }
    }

    override suspend fun saveCallReport(report: CallReport): CallReport = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val url = "$baseUrl/api/v1/calling/reports"

        val json = JSONObject().apply {
            put("call_id", report.callId)
            put("caller_id", report.callerId)
            put("caller_name", report.callerName)
            put("callee_id", report.calleeId)
            put("callee_name", report.calleeName)
            put("overall_security_outcome", report.overallSecurityOutcome.name)
            put("security_verdict_headline", report.securityVerdictHeadline)
            put("security_explanation", report.securityExplanation)
            put("analyses_count", report.analysesCount)
            put("risk_level", report.riskLevel)
            put("is_threat_detected", report.isThreatDetected)
            put("duration_seconds", report.durationSeconds)

            val findingsArr = JSONArray()
            report.keyFindings.forEach { findingsArr.put(it) }
            put("key_findings", findingsArr)

            val recsArr = JSONArray()
            report.recommendations.forEach { recsArr.put(it) }
            put("recommendations", recsArr)

            if (report.speakerVerificationSummary != null) {
                put("speaker_verification_summary", JSONObject(report.speakerVerificationSummary))
            }
            if (report.antispoofSummary != null) {
                put("antispoof_summary", JSONObject(report.antispoofSummary))
            }
            if (report.audioQualitySummary != null) {
                put("audio_quality_summary", JSONObject(report.audioQualitySummary))
            }
        }

        val requestBody = json.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", authHeader)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Failed to save call report (${response.code}): $errorBody")
            }

            val body = response.body?.string() ?: throw IOException("Empty response saving call report")
            parseCallReportJson(JSONObject(body))
        }
    }

    override suspend fun getCallStatus(callId: String): CallSessionInfo = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val url = "$baseUrl/api/v1/calling/status/$callId"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", authHeader)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Failed to query call status (${response.code}): $errorBody")
            }

            val body = response.body?.string() ?: throw IOException("Empty response for call status")
            val obj = JSONObject(body)
            CallSessionInfo(
                callId = obj.getString("call_id"),
                callerId = obj.getString("caller_id"),
                callerName = obj.optString("caller_name", "Caller"),
                calleeId = obj.getString("callee_id"),
                calleeName = obj.optString("callee_name", "Callee"),
                status = CallStatus.fromString(obj.getString("status")),
                createdAt = obj.optString("created_at", ""),
                startedAt = if (obj.isNull("started_at")) null else obj.optString("started_at"),
                endedAt = if (obj.isNull("ended_at")) null else obj.optString("ended_at"),
                durationSeconds = obj.optInt("duration_seconds", 0),
                endReason = if (obj.isNull("end_reason")) null else obj.optString("end_reason")
            )
        }
    }

    private fun parseCallReportJson(obj: JSONObject): CallReport {
        val findings = mutableListOf<String>()
        val findingsArr = obj.optJSONArray("key_findings")
        if (findingsArr != null) {
            for (i in 0 until findingsArr.length()) {
                findings.add(findingsArr.getString(i))
            }
        }

        val recs = mutableListOf<String>()
        val recsArr = obj.optJSONArray("recommendations")
        if (recsArr != null) {
            for (i in 0 until recsArr.length()) {
                recs.add(recsArr.getString(i))
            }
        }

        return CallReport(
            reportId = obj.optString("report_id", "rep_unknown"),
            callId = obj.getString("call_id"),
            callerId = obj.getString("caller_id"),
            callerName = obj.optString("caller_name", "Caller"),
            calleeId = obj.getString("callee_id"),
            calleeName = obj.optString("callee_name", "Callee"),
            overallSecurityOutcome = CombinedSecurityOutcome.fromString(obj.getString("overall_security_outcome")),
            securityVerdictHeadline = obj.getString("security_verdict_headline"),
            securityExplanation = obj.getString("security_explanation"),
            analysesCount = obj.optInt("analyses_count", 1),
            speakerVerificationSummary = null,
            antispoofSummary = null,
            audioQualitySummary = null,
            riskLevel = obj.optString("risk_level", "LOW"),
            isThreatDetected = obj.optBoolean("is_threat_detected", false),
            durationSeconds = obj.optInt("duration_seconds", 0),
            keyFindings = findings,
            recommendations = recs,
            disclaimer = obj.optString("disclaimer", ""),
            createdAt = obj.optString("created_at", "")
        )
    }
}

