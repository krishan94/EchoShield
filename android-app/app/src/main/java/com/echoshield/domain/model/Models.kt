package com.echoshield.domain.model

import com.echoshield.core.components.EchoVerdict

data class UserProfile(
    val id: String,
    val fullName: String,
    val email: String,
    val familyName: String,
    val role: String,
    val isVoiceEnrolled: Boolean,
    val enrolledDate: String?
)

data class FamilyMember(
    val id: String,
    val name: String,
    val relationship: String,
    val isOnline: Boolean,
    val isVoiceEnrolled: Boolean,
    val lastActive: String,
    val voiceQualityScore: Float = 0.92f
)

data class PendingInvitation(
    val id: String,
    val inviterName: String,
    val inviterEmail: String,
    val relationship: String,
    val sentDate: String
)

data class CallRecord(
    val id: String,
    val contactName: String,
    val contactRelationship: String,
    val callType: CallType,
    val timestamp: String,
    val durationText: String,
    val verdict: EchoVerdict,
    val speakerSimilarity: Float,
    val spoofProbability: Float
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED
}

data class VerificationForensics(
    val callId: String,
    val callerName: String,
    val callerRole: String,
    val dateString: String,
    val durationString: String,
    val verdict: EchoVerdict,
    val speakerScore: Float,
    val spoofRiskScore: Float,
    val audioSnrScore: Float,
    val speechDurationSec: Float,
    val modelVersion: String,
    val forensicExplanation: String,
    val recommendation: String
)

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val type: NotificationType,
    val isRead: Boolean
)

enum class NotificationType {
    SECURITY_ALERT,
    FAMILY_INVITE,
    MISSED_CALL,
    SYSTEM_UPDATE
}

data class FaqItem(
    val question: String,
    val answer: String
)
