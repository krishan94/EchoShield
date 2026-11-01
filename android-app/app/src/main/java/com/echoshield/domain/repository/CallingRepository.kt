package com.echoshield.domain.repository

import com.echoshield.domain.model.CallHistoryRecord
import com.echoshield.domain.model.CallReport
import com.echoshield.domain.model.CallSessionInfo
import com.echoshield.domain.model.CombinedSecurityOutcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for EchoShield WebRTC calling,
 * post-call security audit reports, and call history.
 */
interface CallingRepository {
    val currentCallSession: StateFlow<CallSessionInfo?>
    val liveSecurityOutcome: StateFlow<CombinedSecurityOutcome?>

    suspend fun getCallHistory(limit: Int = 50): Result<List<CallHistoryRecord>>
    suspend fun getCallReport(callId: String): Result<CallReport>
    suspend fun saveCallReport(report: CallReport): Result<CallReport>
    suspend fun getCallStatus(callId: String): Result<CallSessionInfo>
}

