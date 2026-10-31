package com.echoshield.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoConfirmationDialog
import com.echoshield.core.components.EchoDangerButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.data.preview.PreviewData
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal

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
    userName: String? = null,
    userEmail: String? = null
) {
    val user = PreviewData.currentUser
    val displayName = userName?.takeIf { it.isNotBlank() } ?: user.fullName
    val displayEmail = userEmail?.takeIf { it.isNotBlank() } ?: user.email
    var isDarkMode by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Security & Settings",
            subtitle = "Preferences & Privacy Controls",
            onOpenDrawer = onOpenDrawer
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Profile Card Preview
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
                        isVoiceEnrolled = user.isVoiceEnrolled
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
                            text = "${user.familyName} • ${user.role}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = PrimaryTeal
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
                subtitle = "Biometric data sovereignty & deletion",
                onClick = onNavigateToPrivacy
            )

            SettingsNavigationRow(
                icon = Icons.Default.Mic,
                title = "Voice Signature Data",
                subtitle = "Manage 192-dim ECAPA-TDNN profile",
                onClick = onNavigateToVoiceData
            )

            SettingsNavigationRow(
                icon = Icons.Default.Shield,
                title = "System Permissions",
                subtitle = "Microphone & Notification access",
                onClick = onNavigateToVoiceData
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Preferences & UI
            SettingsSectionHeader("Preferences & Appearance")

            // Appearance Toggle
            EchoCard(padding = 14.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cyber Dark Theme",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "High-contrast security palette",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { isDarkMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = PrimaryTeal
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SettingsNavigationRow(
                icon = Icons.Default.Notifications,
                title = "Notification Alerts",
                subtitle = "High-priority threat push notifications",
                onClick = onNavigateToNotifications
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Information & Support
            SettingsSectionHeader("Support & Disclosures")

            SettingsNavigationRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Help & AI Limitations",
                subtitle = "Troubleshooting & scam defense guide",
                onClick = onNavigateToHelp
            )

            SettingsNavigationRow(
                icon = Icons.Default.Policy,
                title = "Privacy Policy & Legal",
                subtitle = "Full data handling terms",
                onClick = onNavigateToAbout
            )

            SettingsNavigationRow(
                icon = Icons.Default.Info,
                title = "About EchoShield",
                subtitle = "Build v1.0.0 (Production Architecture)",
                onClick = onNavigateToAbout
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Logout Action
            EchoDangerButton(
                text = "Sign Out Account",
                onClick = { showLogoutDialog = true },
                leadingIcon = Icons.AutoMirrored.Filled.Logout
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showLogoutDialog) {
        EchoConfirmationDialog(
            title = "Sign Out Session?",
            message = "Signing out will pause active background threat monitoring until you re-authenticate.",
            confirmText = "Log Out",
            isDestructive = true,
            icon = Icons.AutoMirrored.Filled.Logout,
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = PrimaryTeal,
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
    Box(modifier = Modifier.padding(vertical = 4.dp)) {
        EchoCard(
            padding = 14.dp,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

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
}

