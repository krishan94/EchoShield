package com.echoshield.navigation

sealed class Screen(val route: String) {
    // Auth & Onboarding
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Signup : Screen("signup")
    data object ForgotPassword : Screen("forgot_password")
    data object EmailVerification : Screen("email_verification")

    // Main Tabs
    data object HomeDashboard : Screen("home_dashboard")
    data object FamilyList : Screen("family_list")
    data object CallingContacts : Screen("calling_contacts")
    data object CallHistory : Screen("call_history")
    data object Settings : Screen("settings")

    // Family Sub-Screens
    data object AddFamilyMember : Screen("add_family_member")
    data object Invitations : Screen("invitations")
    data object FamilyMemberProfile : Screen("family_member_profile/{memberId}") {
        fun createRoute(memberId: String) = "family_member_profile/$memberId"
    }

    // Voice Studio & Biometric Enrollment
    data object VoiceEnrollmentIntro : Screen("voice_enrollment_intro")
    data object MicrophonePermission : Screen("microphone_permission")
    data object VoiceRecordingStudio : Screen("voice_recording_studio")
    data object RecordingReview : Screen("recording_review")
    data object VoiceProfileStatus : Screen("voice_profile_status")

    // Calling & Real-Time Verification
    data object VoiceVerification : Screen("voice_verification?memberId={memberId}") {
        fun createRoute(memberId: String? = null) = if (memberId != null) "voice_verification?memberId=$memberId" else "voice_verification"
    }
    data object VerificationHistory : Screen("verification_history")
    data object IncomingCall : Screen("incoming_call/{callerId}") {
        fun createRoute(callerId: String) = "incoming_call/$callerId"
    }
    data object OutgoingCall : Screen("outgoing_call/{contactId}") {
        fun createRoute(contactId: String) = "outgoing_call/$contactId"
    }
    data object ActiveCall : Screen("active_call/{callId}") {
        fun createRoute(callId: String) = "active_call/$callId"
    }
    data object VerificationProgress : Screen("verification_progress/{callId}") {
        fun createRoute(callId: String) = "verification_progress/$callId"
    }
    data object VerificationResults : Screen("verification_results/{callId}") {
        fun createRoute(callId: String) = "verification_results/$callId"
    }

    // Reports & Notifications
    data object ReportDetails : Screen("report_details/{callId}") {
        fun createRoute(callId: String) = "report_details/$callId"
    }
    data object Notifications : Screen("notifications")

    // Settings & Privacy
    data object PrivacySettings : Screen("privacy_settings")
    data object EditProfile : Screen("edit_profile")
    data object HelpSupport : Screen("help_support")
    data object PrivacyPolicy : Screen("privacy_policy")

    // Diagnostic & State Showcase
    data object StateShowcase : Screen("state_showcase")
}

