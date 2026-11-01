package com.echoshield.data.repository

import com.echoshield.data.remote.CallingRemoteDataSource
import com.echoshield.domain.model.CallHistoryRecord
import com.echoshield.domain.model.CallReport
import com.echoshield.domain.model.CallSessionInfo
import com.echoshield.domain.model.CombinedSecurityOutcome
import com.echoshield.domain.repository.CallingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CallingRepositoryImpl(
    private val remoteDataSource: CallingRemoteDataSource
) : CallingRepository {

    private val _currentCallSession = MutableStateFlow<CallSessionInfo?>(null)
    override val currentCallSession: StateFlow<CallSessionInfo?> = _currentCallSession.asStateFlow()

    private val _liveSecurityOutcome = MutableStateFlow<CombinedSecurityOutcome?>(null)
    override val liveSecurityOutcome: StateFlow<CombinedSecurityOutcome?> = _liveSecurityOutcome.asStateFlow()

    fun updateCurrentCallSession(session: CallSessionInfo?) {
        _currentCallSession.value = session
    }

    fun updateLiveSecurityOutcome(outcome: CombinedSecurityOutcome?) {
        _liveSecurityOutcome.value = outcome
    }

    override suspend fun getCallHistory(limit: Int): Result<List<CallHistoryRecord>> {
        return try {
            val list = remoteDataSource.getCallHistory(limit)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCallReport(callId: String): Result<CallReport> {
        return try {
            val report = remoteDataSource.getCallReport(callId)
            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveCallReport(report: CallReport): Result<CallReport> {
        return try {
            val saved = remoteDataSource.saveCallReport(report)
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCallStatus(callId: String): Result<CallSessionInfo> {
        return try {
            val session = remoteDataSource.getCallStatus(callId)
            _currentCallSession.value = session
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

