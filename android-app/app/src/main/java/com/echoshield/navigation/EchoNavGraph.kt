package com.echoshield.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.echoshield.authentication.AuthViewModel
import com.echoshield.authentication.EmailVerificationScreen
import com.echoshield.authentication.ForgotPasswordScreen
import com.echoshield.authentication.LoginScreen
import com.echoshield.authentication.OnboardingScreen
import com.echoshield.authentication.SignupScreen
import com.echoshield.authentication.SplashScreen
import com.echoshield.calling.ActiveCallScreen
import com.echoshield.calling.CallingContactsScreen
import com.echoshield.calling.IncomingCallScreen
import com.echoshield.calling.OutgoingCallScreen
import com.echoshield.core.components.StateShowcaseScreen
import com.echoshield.dashboard.DashboardScreen
import com.echoshield.family.AddFamilyMemberScreen
import com.echoshield.family.FamilyListScreen
import com.echoshield.family.FamilyMemberProfileScreen
import com.echoshield.family.FamilyViewModel
import com.echoshield.family.InvitationsScreen
import com.echoshield.notifications.NotificationsScreen
import com.echoshield.reports.CallHistoryScreen
import com.echoshield.reports.ReportDetailsScreen
import com.echoshield.settings.EditProfileScreen
import com.echoshield.settings.HelpSupportScreen
import com.echoshield.settings.PrivacyPolicyScreen
import com.echoshield.settings.PrivacySettingsScreen
import com.echoshield.settings.SettingsScreen
import com.echoshield.verification.VerificationProgressScreen
import com.echoshield.verification.VerificationResultsScreen
import com.echoshield.voice.MicrophonePermissionScreen
import com.echoshield.voice.RecordingReviewScreen
import com.echoshield.voice.VoiceEnrollmentIntroScreen
import com.echoshield.voice.VoiceProfileStatusScreen
import com.echoshield.voice.VoiceRecordingStudioScreen

@Composable
fun EchoNavGraph(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
    familyViewModel: FamilyViewModel = viewModel(factory = FamilyViewModel.Factory),
    voiceViewModel: com.echoshield.voice.VoiceViewModel = viewModel(factory = com.echoshield.voice.VoiceViewModel.Factory)
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        // --- SCREEN 1: SPLASH ---
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.HomeDashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToVerification = {
                    navController.navigate(Screen.EmailVerification.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        // --- SCREEN 2: ONBOARDING ---
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // --- SCREEN 3: LOGIN ---
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.HomeDashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                onNavigateToVerification = { navController.navigate(Screen.EmailVerification.route) },
                viewModel = authViewModel
            )
        }

        // --- SCREEN 4: SIGNUP ---
        composable(Screen.Signup.route) {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate(Screen.EmailVerification.route) {
                        popUpTo(Screen.Signup.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
                onNavigateToTerms = { navController.navigate(Screen.PrivacyPolicy.route) },
                viewModel = authViewModel
            )
        }

        // --- SCREEN 5: FORGOT PASSWORD ---
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = authViewModel
            )
        }

        // --- SCREEN 6: EMAIL VERIFICATION ---
        composable(Screen.EmailVerification.route) {
            EmailVerificationScreen(
                onVerificationSuccess = {
                    navController.navigate(Screen.HomeDashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateBackToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        // --- SCREEN 7: HOME DASHBOARD ---
        composable(Screen.HomeDashboard.route) {
            DashboardScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onCallFamily = { navController.navigate(Screen.CallingContacts.route) },
                onAddFamilyMember = { navController.navigate(Screen.AddFamilyMember.route) },
                onRegisterVoice = { navController.navigate(Screen.VoiceEnrollmentIntro.route) },
                onViewReports = { navController.navigate(Screen.CallHistory.route) },
                onViewCallDetail = { callId -> navController.navigate(Screen.ReportDetails.createRoute(callId)) }
            )
        }

        // --- SCREEN 8: FAMILY LIST ---
        composable(Screen.FamilyList.route) {
            FamilyListScreen(
                onOpenDrawer = onOpenDrawer,
                onAddMember = { navController.navigate(Screen.AddFamilyMember.route) },
                onViewInvitations = { navController.navigate(Screen.Invitations.route) },
                onMemberClick = { memberId -> navController.navigate(Screen.FamilyMemberProfile.createRoute(memberId)) },
                onStartCall = { memberId -> navController.navigate(Screen.OutgoingCall.createRoute(memberId)) },
                viewModel = familyViewModel
            )
        }

        // --- SCREEN 9: ADD FAMILY MEMBER ---
        composable(Screen.AddFamilyMember.route) {
            AddFamilyMemberScreen(
                onNavigateBack = { navController.popBackStack() },
                onInvitationSent = { navController.popBackStack() },
                viewModel = familyViewModel
            )
        }

        // --- SCREEN 10: INVITATIONS ---
        composable(Screen.Invitations.route) {
            InvitationsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = familyViewModel
            )
        }

        // --- SCREEN 11: FAMILY MEMBER PROFILE ---
        composable(
            route = Screen.FamilyMemberProfile.route,
            arguments = listOf(navArgument("memberId") { type = NavType.StringType })
        ) { backStackEntry ->
            val memberId = backStackEntry.arguments?.getString("memberId") ?: "mem_001"
            FamilyMemberProfileScreen(
                memberId = memberId,
                onNavigateBack = { navController.popBackStack() },
                onStartCall = { id -> navController.navigate(Screen.OutgoingCall.createRoute(id)) },
                viewModel = familyViewModel
            )
        }

        // --- SCREEN 12: VOICE ENROLLMENT INTRO ---
        composable(Screen.VoiceEnrollmentIntro.route) {
            VoiceEnrollmentIntroScreen(
                onNavigateBack = { navController.popBackStack() },
                onProceedToPermission = { navController.navigate(Screen.MicrophonePermission.route) },
                viewModel = voiceViewModel
            )
        }

        // --- SCREEN 13: MICROPHONE PERMISSION ---
        composable(Screen.MicrophonePermission.route) {
            MicrophonePermissionScreen(
                onPermissionGranted = { navController.navigate(Screen.VoiceRecordingStudio.route) },
                onNavigateBack = { navController.popBackStack() },
                viewModel = voiceViewModel
            )
        }

        // --- SCREEN 14: VOICE RECORDING STUDIO ---
        composable(Screen.VoiceRecordingStudio.route) {
            VoiceRecordingStudioScreen(
                totalSamples = 3,
                onRecordingCompleted = { navController.navigate(Screen.RecordingReview.route) },
                onNavigateBack = { navController.popBackStack() },
                viewModel = voiceViewModel
            )
        }

        // --- SCREEN 15: RECORDING REVIEW ---
        composable(Screen.RecordingReview.route) {
            val uiState by voiceViewModel.uiState.collectAsState()
            RecordingReviewScreen(
                sampleNumber = uiState.currentSampleIndex,
                onAcceptSample = {
                    if (uiState.currentSampleIndex < uiState.totalSamples) {
                        navController.navigate(Screen.VoiceRecordingStudio.route) {
                            popUpTo(Screen.VoiceRecordingStudio.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.VoiceProfileStatus.route) {
                            popUpTo(Screen.VoiceEnrollmentIntro.route) { inclusive = true }
                        }
                    }
                },
                onRecordAgain = { navController.popBackStack() },
                onDeleteSample = {
                    navController.navigate(Screen.VoiceEnrollmentIntro.route) {
                        popUpTo(Screen.VoiceEnrollmentIntro.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
                viewModel = voiceViewModel
            )
        }

        // --- SCREEN 16: VOICE PROFILE STATUS ---
        composable(Screen.VoiceProfileStatus.route) {
            VoiceProfileStatusScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.HomeDashboard.route) {
                        popUpTo(Screen.VoiceEnrollmentIntro.route) { inclusive = true }
                    }
                },
                onReEnrollVoice = { navController.navigate(Screen.VoiceRecordingStudio.route) },
                viewModel = voiceViewModel
            )
        }

        // --- SCREEN 17: CALLING CONTACTS ---
        composable(Screen.CallingContacts.route) {
            CallingContactsScreen(
                onOpenDrawer = onOpenDrawer,
                onStartOutgoingCall = { contactId -> navController.navigate(Screen.OutgoingCall.createRoute(contactId)) },
                onSimulateIncomingCall = { contactId -> navController.navigate(Screen.IncomingCall.createRoute(contactId)) }
            )
        }

        // --- SCREEN 18: INCOMING CALL ---
        composable(
            route = Screen.IncomingCall.route,
            arguments = listOf(navArgument("callerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callerId = backStackEntry.arguments?.getString("callerId") ?: "mem_001"
            IncomingCallScreen(
                callerId = callerId,
                onAcceptCall = { navController.navigate(Screen.ActiveCall.createRoute("call_9822")) },
                onRejectCall = { navController.popBackStack() }
            )
        }

        // --- SCREEN 19: OUTGOING CALL ---
        composable(
            route = Screen.OutgoingCall.route,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: "mem_001"
            OutgoingCallScreen(
                contactId = contactId,
                onCallConnected = { navController.navigate(Screen.ActiveCall.createRoute("call_9822")) },
                onCancelCall = { navController.popBackStack() }
            )
        }

        // --- SCREEN 20: ACTIVE CALL ---
        composable(
            route = Screen.ActiveCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: "call_9822"
            ActiveCallScreen(
                callId = callId,
                onEndCall = { navController.navigate(Screen.VerificationResults.createRoute(callId)) },
                onViewVerificationProgress = { id -> navController.navigate(Screen.VerificationProgress.createRoute(id)) }
            )
        }

        // --- SCREEN 21: VERIFICATION PROGRESS ---
        composable(
            route = Screen.VerificationProgress.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: "call_9822"
            VerificationProgressScreen(
                callId = callId,
                onNavigateBack = { navController.popBackStack() },
                onViewFinalResults = { id -> navController.navigate(Screen.VerificationResults.createRoute(id)) }
            )
        }

        // --- SCREEN 22: VERIFICATION RESULTS ---
        composable(
            route = Screen.VerificationResults.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: "call_9822"
            VerificationResultsScreen(
                callId = callId,
                onNavigateBack = {
                    navController.navigate(Screen.CallHistory.route) {
                        popUpTo(Screen.ActiveCall.route) { inclusive = true }
                    }
                },
                onViewFullForensicReport = { id -> navController.navigate(Screen.ReportDetails.createRoute(id)) }
            )
        }

        // --- SCREEN 23: CALL HISTORY ---
        composable(Screen.CallHistory.route) {
            CallHistoryScreen(
                onOpenDrawer = onOpenDrawer,
                onViewReportDetails = { callId -> navController.navigate(Screen.ReportDetails.createRoute(callId)) }
            )
        }

        // --- SCREEN 24: REPORT DETAILS ---
        composable(
            route = Screen.ReportDetails.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: "call_9820"
            ReportDetailsScreen(
                callId = callId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // --- SCREEN 25: NOTIFICATIONS ---
        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNotificationClick = { _ -> navController.navigate(Screen.ReportDetails.createRoute("call_9820")) }
            )
        }

        // --- SCREEN 26: SETTINGS ---
        composable(Screen.Settings.route) {
            val user = authViewModel.uiState.value.user
            SettingsScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                onNavigateToPrivacy = { navController.navigate(Screen.PrivacySettings.route) },
                onNavigateToVoiceData = { navController.navigate(Screen.VoiceProfileStatus.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToHelp = { navController.navigate(Screen.HelpSupport.route) },
                onNavigateToAbout = { navController.navigate(Screen.PrivacyPolicy.route) },
                onLogout = {
                    authViewModel.logout {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                userName = user?.displayName,
                userEmail = user?.email
            )
        }

        // --- SCREEN 27: PRIVACY SETTINGS ---
        composable(Screen.PrivacySettings.route) {
            PrivacySettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // --- SCREEN 28: EDIT PROFILE ---
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onProfileSaved = { navController.popBackStack() }
            )
        }

        // --- SCREEN 29: HELP & SUPPORT ---
        composable(Screen.HelpSupport.route) {
            HelpSupportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // --- SCREEN 30: PRIVACY POLICY & TERMS ---
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // --- SCREEN 31: STATE SHOWCASE (ERROR/EMPTY/OFFLINE) ---
        composable(Screen.StateShowcase.route) {
            StateShowcaseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

