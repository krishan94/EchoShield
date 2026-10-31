package com.echoshield.data.preview

import com.echoshield.core.components.EchoVerdict
import com.echoshield.domain.model.CallRecord
import com.echoshield.domain.model.CallType
import com.echoshield.domain.model.FamilyMember
import com.echoshield.domain.model.FaqItem
import com.echoshield.domain.model.NotificationItem
import com.echoshield.domain.model.NotificationType
import com.echoshield.domain.model.PendingInvitation
import com.echoshield.domain.model.UserProfile
import com.echoshield.domain.model.VerificationForensics

/**
 * Preview and UI Mock Data Source.
 * Isolated from production repositories in compliance with architecture guidelines.
 */
object PreviewData {

    val currentUser = UserProfile(
        id = "usr_001",
        fullName = "Sarah Henderson",
        email = "sarah.h@echoshield.family",
        familyName = "Henderson Family Circle",
        role = "Family Admin",
        isVoiceEnrolled = true,
        enrolledDate = "Oct 28, 2026"
    )

    val familyMembers = listOf(
        FamilyMember(
            id = "mem_001",
            name = "Mark Henderson",
            relationship = "Spouse",
            isOnline = true,
            isVoiceEnrolled = true,
            lastActive = "Active now",
            voiceQualityScore = 0.95f
        ),
        FamilyMember(
            id = "mem_002",
            name = "Emily Henderson",
            relationship = "Daughter",
            isOnline = false,
            isVoiceEnrolled = true,
            lastActive = "15m ago",
            voiceQualityScore = 0.91f
        ),
        FamilyMember(
            id = "mem_003",
            name = "David Henderson",
            relationship = "Father",
            isOnline = true,
            isVoiceEnrolled = false,
            lastActive = "Active now",
            voiceQualityScore = 0.0f
        ),
        FamilyMember(
            id = "mem_004",
            name = "Claire Walker",
            relationship = "Sister",
            isOnline = false,
            isVoiceEnrolled = true,
            lastActive = "2h ago",
            voiceQualityScore = 0.89f
        )
    )

    val pendingInvitations = listOf(
        PendingInvitation(
            id = "inv_001",
            inviterName = "Lucas Walker",
            inviterEmail = "lucas.w@example.com",
            relationship = "Brother-in-Law",
            sentDate = "Yesterday at 4:15 PM"
        ),
        PendingInvitation(
            id = "inv_002",
            inviterName = "Eleanor Vance",
            inviterEmail = "e.vance@example.org",
            relationship = "Aunt",
            sentDate = "3 days ago"
        )
    )

    val callRecords = listOf(
        CallRecord(
            id = "call_9821",
            contactName = "Mark Henderson",
            contactRelationship = "Spouse",
            callType = CallType.INCOMING,
            timestamp = "Today, 10:42 AM",
            durationText = "3m 14s",
            verdict = EchoVerdict.VOICE_MATCHED,
            speakerSimilarity = 0.94f,
            spoofProbability = 0.03f
        ),
        CallRecord(
            id = "call_9820",
            contactName = "Unknown Caller (Claiming David)",
            contactRelationship = "Suspected Impersonator",
            callType = CallType.INCOMING,
            timestamp = "Yesterday, 6:18 PM",
            durationText = "48s",
            verdict = EchoVerdict.SUSPICIOUS_SPOOF,
            speakerSimilarity = 0.42f,
            spoofProbability = 0.89f
        ),
        CallRecord(
            id = "call_9819",
            contactName = "Emily Henderson",
            contactRelationship = "Daughter",
            callType = CallType.OUTGOING,
            timestamp = "Oct 29, 2:15 PM",
            durationText = "7m 02s",
            verdict = EchoVerdict.VOICE_MATCHED,
            speakerSimilarity = 0.92f,
            spoofProbability = 0.02f
        ),
        CallRecord(
            id = "call_9818",
            contactName = "David Henderson",
            contactRelationship = "Father",
            callType = CallType.MISSED,
            timestamp = "Oct 28, 9:05 AM",
            durationText = "0s",
            verdict = EchoVerdict.INCONCLUSIVE,
            speakerSimilarity = 0.0f,
            spoofProbability = 0.0f
        ),
        CallRecord(
            id = "call_9817",
            contactName = "Claire Walker",
            contactRelationship = "Sister",
            callType = CallType.INCOMING,
            timestamp = "Oct 27, 4:50 PM",
            durationText = "1m 30s",
            verdict = EchoVerdict.IDENTITY_MISMATCH,
            speakerSimilarity = 0.58f,
            spoofProbability = 0.12f
        )
    )

    val sampleForensics = VerificationForensics(
        callId = "call_9820",
        callerName = "Claimed: David Henderson",
        callerRole = "Suspected Voice Clone Scam",
        dateString = "October 30, 2026 at 6:18:22 PM",
        durationString = "48 seconds",
        verdict = EchoVerdict.SUSPICIOUS_SPOOF,
        speakerScore = 0.42f,
        spoofRiskScore = 0.89f,
        audioSnrScore = 24.5f,
        speechDurationSec = 18.2f,
        modelVersion = "SpeechBrain ECAPA-TDNN v1.2 / AntiSpoof-AASIST",
        forensicExplanation = "Acoustic frequency analysis detected phase incoherency and neural vocoder spectral artifacts typical of generative text-to-speech voice synthesis. Biometric cosine similarity against David Henderson's registered profile fell well below confidence threshold (0.42 vs 0.75 target).",
        recommendation = "Do not transfer funds, share personal passwords, or verify sensitive information. Immediately contact your family member on an alternate verified device."
    )

    val notifications = listOf(
        NotificationItem(
            id = "notif_001",
            title = "CRITICAL: Voice Clone Threat Detected",
            message = "An incoming in-app call claiming to be David Henderson exhibited synthetic AI voice cloning artifacts (Spoof Risk 89%).",
            timestamp = "10m ago",
            type = NotificationType.SECURITY_ALERT,
            isRead = false
        ),
        NotificationItem(
            id = "notif_002",
            title = "Family Circle Invitation",
            message = "Lucas Walker has requested to join your Henderson Family Circle.",
            timestamp = "1h ago",
            type = NotificationType.FAMILY_INVITE,
            isRead = false
        ),
        NotificationItem(
            id = "notif_003",
            title = "Missed Encrypted Call",
            message = "Missed call from Emily Henderson. Both biometric certificates were active.",
            timestamp = "Yesterday",
            type = NotificationType.MISSED_CALL,
            isRead = true
        ),
        NotificationItem(
            id = "notif_004",
            title = "Biometric Model Update",
            message = "ECAPA-TDNN feature extraction pipeline upgraded to enhanced noise tolerance.",
            timestamp = "3 days ago",
            type = NotificationType.SYSTEM_UPDATE,
            isRead = true
        )
    )

    val faqs = listOf(
        FaqItem(
            question = "How does EchoShield verify a caller's voice?",
            answer = "EchoShield uses a dual-engine AI pipeline: First, SpeechBrain ECAPA-TDNN extracts a 192-dimensional mathematical embedding from the incoming speech and compares it with your family member's registered biometric profile. Simultaneously, a neural anti-spoofing model analyzes high-frequency spectral phase characteristics to detect synthetic AI generators (TTS/VC) and acoustic replay artifacts."
        ),
        FaqItem(
            question = "Are my phone calls recorded?",
            answer = "No. EchoShield never records normal cellular SIM phone calls. For encrypted in-app calls, audio chunks are analyzed ephemerally in volatile RAM and immediately discarded. EchoShield only retains encrypted mathematical vector embeddings and forensic audit logs."
        ),
        FaqItem(
            question = "What if a family member has a cold or bad reception?",
            answer = "If acoustic conditions or temporary voice changes reduce similarity below confidence thresholds without showing synthetic anomalies, EchoShield flags the call as 'Identity Not Matched' or 'Inconclusive' rather than an immediate spoof, prompting verification via a secondary family passphrase."
        ),
        FaqItem(
            question = "Can I delete my biometric voice profile?",
            answer = "Yes. You maintain complete sovereignty over your biometric data. Navigating to Settings > Privacy > Voice Data allows instantaneous cryptographic erasure of your stored embeddings from both your device and cloud storage."
        )
    )
}

