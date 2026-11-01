package com.echoshield.domain.model

/**
 * WebRTC Calling and Security Decision Data Contracts for EchoShield.
 */

enum class CallStatus {
    IDLE,
    INITIATING,
    RINGING,
    CONNECTING,
    CONNECTED,
    ENDED,
    REJECTED,
    MISSED,
    FAILED,
    BUSY;

    companion object {
        fun fromString(value: String): CallStatus = try {
            valueOf(value.uppercase())
        } catch (_: Exception) {
            FAILED
        }
    }
}

enum class SignalingMessageType(val wireValue: String) {
    CALL_INITIATE("call_initiate"),
    CALL_RINGING("call_ringing"),
    CALL_ACCEPTED("call_accepted"),
    CALL_REJECTED("call_rejected"),
    CALL_ENDED("call_ended"),
    CALL_FAILED("call_failed"),
    CALL_BUSY("call_busy"),
    OFFER("offer"),
    ANSWER("answer"),
    ICE_CANDIDATE("ice_candidate"),
    PING("ping"),
    PONG("pong"),
    ERROR("error");

    companion object {
        fun fromWireValue(value: String): SignalingMessageType =
            entries.find { it.wireValue.equals(value, ignoreCase = true) } ?: ERROR
    }
}

data class SignalingMessage(
    val type: SignalingMessageType,
    val callId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null,
    val receiverId: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
    val timestamp: String? = null
)

data class CallSessionInfo(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val calleeId: String,
    val calleeName: String,
    val status: CallStatus,
    val createdAt: String,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val durationSeconds: Int = 0,
    val endReason: String? = null
)

data class CallReport(
    val reportId: String,
    val callId: String,
    val callerId: String,
    val callerName: String,
    val calleeId: String,
    val calleeName: String,
    val overallSecurityOutcome: CombinedSecurityOutcome,
    val securityVerdictHeadline: String,
    val securityExplanation: String,
    val analysesCount: Int = 1,
    val speakerVerificationSummary: Map<String, Any?>? = null,
    val antispoofSummary: Map<String, Any?>? = null,
    val audioQualitySummary: Map<String, Any?>? = null,
    val riskLevel: String = "LOW",
    val isThreatDetected: Boolean = false,
    val durationSeconds: Int = 0,
    val keyFindings: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val disclaimer: String = "EchoShield Voice Security Engine provides calibrated probabilistic defense against voice impersonation.",
    val createdAt: String = ""
)

data class CallHistoryRecord(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val calleeId: String,
    val calleeName: String,
    val direction: String, // "incoming" or "outgoing"
    val otherPartyName: String,
    val otherPartyId: String,
    val status: CallStatus,
    val durationSeconds: Int,
    val securityOutcome: CombinedSecurityOutcome? = null,
    val securityHeadline: String? = null,
    val hasReport: Boolean = false,
    val createdAt: String
)

