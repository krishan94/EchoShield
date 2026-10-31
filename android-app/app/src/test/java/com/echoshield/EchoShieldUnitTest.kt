package com.echoshield

import com.echoshield.core.components.EchoVerdict
import com.echoshield.data.preview.PreviewData
import com.echoshield.domain.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying domain models, verdict calibration, and preview data integrity.
 */
class EchoShieldUnitTest {

    @Test
    fun testCurrentUserProfileInitialization() {
        val user = PreviewData.currentUser
        assertNotNull(user)
        assertEquals("Sarah Henderson", user.fullName)
        assertTrue(user.isVoiceEnrolled)
        assertEquals("Family Admin", user.role)
    }

    @Test
    fun testFamilyMembersCountAndIntegrity() {
        val members = PreviewData.familyMembers
        assertTrue(members.isNotEmpty())
        assertEquals(4, members.size)

        val enrolledCount = members.count { it.isVoiceEnrolled }
        assertEquals(3, enrolledCount)

        val pendingMember = members.find { !it.isVoiceEnrolled }
        assertNotNull(pendingMember)
        assertEquals("David Henderson", pendingMember?.name)
    }

    @Test
    fun testCallRecordsAndVerdictSeparation() {
        val calls = PreviewData.callRecords
        assertTrue(calls.isNotEmpty())

        val matchedCall = calls.find { it.verdict == EchoVerdict.VOICE_MATCHED }
        assertNotNull(matchedCall)
        assertTrue(matchedCall!!.speakerSimilarity >= 0.75f)
        assertTrue(matchedCall.spoofProbability <= 0.20f)

        val spoofCall = calls.find { it.verdict == EchoVerdict.SUSPICIOUS_SPOOF }
        assertNotNull(spoofCall)
        assertTrue(spoofCall!!.spoofProbability > 0.50f)
        assertTrue(spoofCall.speakerSimilarity < 0.75f)
    }

    @Test
    fun testForensicReportIntegrity() {
        val report = PreviewData.sampleForensics
        assertEquals("call_9820", report.callId)
        assertEquals(EchoVerdict.SUSPICIOUS_SPOOF, report.verdict)
        assertTrue(report.spoofRiskScore >= 0.80f)
        assertTrue(report.audioSnrScore > 0f)
        assertFalse(report.forensicExplanation.isBlank())
    }

    @Test
    fun testPendingInvitations() {
        val invitations = PreviewData.pendingInvitations
        assertTrue(invitations.size >= 2)
        assertEquals("Lucas Walker", invitations[0].inviterName)
    }

    @Test
    fun testFaqContent() {
        val faqs = PreviewData.faqs
        assertTrue(faqs.size >= 4)
        assertTrue(faqs.any { it.question.contains("ECAPA-TDNN", ignoreCase = true) || it.answer.contains("ECAPA-TDNN") })
    }
}
