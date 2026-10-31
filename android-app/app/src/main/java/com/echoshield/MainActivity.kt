package com.echoshield

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
import com.echoshield.navigation.EchoNavGraph
import com.echoshield.navigation.Screen
import com.echoshield.theme.EchoShieldTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EchoShieldTheme {
                EchoShieldApp()
            }
        }
    }
}

@Composable
fun EchoShieldApp(
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val authUiState by authViewModel.uiState.collectAsState()

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
