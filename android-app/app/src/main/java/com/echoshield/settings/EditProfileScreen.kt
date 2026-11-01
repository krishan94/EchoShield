package com.echoshield.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.echoshield.authentication.AuthViewModel
import com.echoshield.core.components.EchoAvatar
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTextField
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.voice.VoiceViewModel

@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onProfileSaved: () -> Unit,
    authViewModel: AuthViewModel? = null,
    voiceViewModel: VoiceViewModel? = null
) {
    val authState by authViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }
    val voiceState by voiceViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }

    val user = authState?.user
    val initialName = user?.displayName ?: "Family Guardian"
    val initialEmail = user?.email ?: "family@echoshield.io"
    val isVoiceEnrolled = voiceState?.voiceProfile?.isVoiceEnrolled == true

    var fullName by remember(user) { mutableStateOf(initialName) }
    var email by remember(user) { mutableStateOf(initialEmail) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Edit Member Profile",
            subtitle = "Account Identity",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Avatar with Edit Badge
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                EchoAvatar(
                    name = fullName.ifBlank { "User" },
                    size = 96.dp,
                    isVoiceEnrolled = isVoiceEnrolled
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Profile Photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            EchoTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full Name",
                placeholder = "Enter your display name",
                leadingIcon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(18.dp))

            EchoTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email Address",
                placeholder = "your.email@example.com",
                leadingIcon = Icons.Default.Email,
                enabled = false // Email is locked to Firebase Auth credential
            )

            Spacer(modifier = Modifier.height(32.dp))

            EchoPrimaryButton(
                text = "Save Changes",
                leadingIcon = Icons.Default.Check,
                isLoading = isSaving,
                onClick = {
                    isSaving = true
                    // Real profile updates can be dispatched through auth repository
                    onProfileSaved()
                }
            )
        }
    }
}
