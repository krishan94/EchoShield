package com.echoshield

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.echoshield.authentication.AuthViewModel
import com.echoshield.core.components.EchoBottomNavigation
import com.echoshield.core.components.EchoNavigationDrawerContent
import com.echoshield.core.di.AppContainer
import com.echoshield.core.theme.AppThemeMode
import com.echoshield.navigation.EchoNavGraph
import com.echoshield.navigation.Screen
import com.echoshield.theme.EchoShieldTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val themePrefs = AppContainer.getThemePreferences(applicationContext)

        setContent {
            val themeMode by themePrefs.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)
            EchoShieldTheme(themeMode = themeMode) {
                EchoShieldApp(pendingRoute = pendingRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra("EXTRA_NAVIGATE_TO") == "active_call") {
            val callId = intent.getStringExtra("EXTRA_CALL_ID") ?: "call_active"
            pendingRoute.value = Screen.ActiveCall.createRoute(callId)
        }
    }
}

@Composable
fun EchoShieldApp(
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
    pendingRoute: StateFlow<String?>? = null
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val authUiState by authViewModel.uiState.collectAsState()

    // Handle deep navigation from incoming call notifications / full screen activity
    pendingRoute?.let { routeFlow ->
        val targetRoute by routeFlow.collectAsState()
        LaunchedEffect(targetRoute) {
            targetRoute?.let { route ->
                navController.navigate(route) {
                    launchSingleTop = true
                }
                (routeFlow as? MutableStateFlow)?.value = null
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Splash.route

    // Define top-level routes that display the bottom navigation bar
    val topLevelRoutes = listOf(
        Screen.HomeDashboard.route,
        Screen.FamilyList.route,
        Screen.CallingContacts.route,
        Screen.CallHistory.route,
        Screen.Settings.route
    )

    val showBottomBar = currentRoute in topLevelRoutes

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            EchoNavigationDrawerContent(
                currentRoute = currentRoute,
                userName = authUiState.user?.displayName?.takeIf { it.isNotBlank() } ?: "Family Guardian",
                userEmail = authUiState.user?.email?.takeIf { it.isNotBlank() } ?: "family@echoshield.io",
                onNavigate = { route ->
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    coroutineScope.launch { drawerState.close() }
                    authViewModel.logout {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    EchoBottomNavigation(
                        currentRoute = currentRoute,
                        onNavigateToRoute = { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.HomeDashboard.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                EchoNavGraph(
                    navController = navController,
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    authViewModel = authViewModel
                )
            }
        }
    }
}
