package com.echoshield.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.authentication.AuthViewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoConfirmationDialog
import com.echoshield.core.components.EchoDangerButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.core.di.AppContainer
import com.echoshield.core.theme.AppThemeMode
import com.echoshield.theme.DangerRed
import com.echoshield.theme.SuccessGreen
import com.echoshield.theme.WarningAmber
import com.echoshield.voice.VoiceViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToVoiceData: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel? = null,
    voiceViewModel: VoiceViewModel? = null,
    userName: String? = null,
    userEmail: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePrefs = remember { AppContainer.getThemePreferences(context) }
    val currentThemeMode by themePrefs.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)

    val authState by authViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }
    val voiceState by voiceViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }

    val user = authState?.user
    val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: userName?.takeIf { it.isNotBlank() } ?: "Family Guardian"
    val displayEmail = user?.email?.takeIf { it.isNotBlank() } ?: userEmail?.takeIf { it.isNotBlank() } ?: "family@echoshield.io"
    val isVoiceEnrolled = voiceState?.voiceProfile?.isVoiceEnrolled == true

    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Security & Settings",
            subtitle = "Preferences, Appearance & Biometric Controls",
            onOpenDrawer = onOpenDrawer
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Profile Card (Real Authenticated User)
            EchoCard(
                padding = 16.dp,
                onClick = onNavigateToEditProfile
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoAvatar(
                        name = displayName,
                        size = 56.dp,
                        isVoiceEnrolled = isVoiceEnrolled
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = displayEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isVoiceEnrolled) "Voice Biometrics Enrolled" else "Voice Enrollment Pending",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isVoiceEnrolled) SuccessGreen else WarningAmber
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Security & Biometrics
            SettingsSectionHeader("Security & Biometrics")

            SettingsNavigationRow(
                icon = Icons.Default.Security,
                title = "Privacy & Consent",
                subtitle = "Biometric data rights, encryption & policy",
                onClick = onNavigateToPrivacy
            )

            SettingsNavigationRow(
                icon = Icons.Default.Mic,
                title = "Voice Signature Studio",
                subtitle = "Manage enrolled SpeechBrain biometric profile",
                onClick = onNavigateToVoiceData
            )

            SettingsNavigationRow(
                icon = Icons.Default.Shield,
                title = "System Permissions",
                subtitle = "Microphone, WebRTC & Notification status",
                onClick = onNavigateToVoiceData
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Appearance (Light, Dark, System Default)
            SettingsSectionHeader("Appearance")

            EchoCard(padding = 16.dp) {
                Column {
                    Text(
                        text = "Theme Selection",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose your preferred display aesthetic across all screens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val themeOptions = listOf(
                        Triple(AppThemeMode.LIGHT, "Light", "Warm Ivory theme (#F5F3EA)"),
                        Triple(AppThemeMode.DARK, "Dark", "Midnight Teal theme (#102827)"),
                        Triple(AppThemeMode.SYSTEM, "System Default", "Follows device system settings")
                    )

                    themeOptions.forEach { (mode, title, desc) ->
                        val isSelected = currentThemeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        themePrefs.setThemeMode(mode)
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch {
                                        themePrefs.setThemeMode(mode)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Information & Support
            SettingsSectionHeader("Support & Disclosures")

            SettingsNavigationRow(
                icon = Icons.Default.Notifications,
                title = "Security Notifications",
                subtitle = "Recent system alerts & circle invitations",
                onClick = onNavigateToNotifications
            )

            SettingsNavigationRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Help & AI Limitations",
                subtitle = "Acoustic spoofing FAQs & defense guidance",
                onClick = onNavigateToHelp
            )

            SettingsNavigationRow(
                icon = Icons.Default.Policy,
                title = "Privacy Policy & Disclosures",
                subtitle = "Zero-cloud audio retention & consent terms",
                onClick = onNavigateToAbout
            )

            SettingsNavigationRow(
                icon = Icons.Default.Info,
                title = "About EchoShield",
                subtitle = "EchoShield v1.0 • WebRTC Voice Defense",
                onClick = onNavigateToAbout
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Sign Out Button
            EchoDangerButton(
                text = "Sign Out Session",
                leadingIcon = Icons.AutoMirrored.Filled.Logout,
                onClick = { showLogoutDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        EchoConfirmationDialog(
            title = "Confirm Session Sign Out",
            message = "Signing out will disconnect your active family WebRTC calling channel and invalidate your session tokens. Continue?",
            confirmText = "Sign Out",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    EchoCard(
        padding = 14.dp,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
