package com.echoshield.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoGhostButton
import com.echoshield.core.components.EchoOutlinedButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.WarningAmber

@Composable
fun MicrophonePermissionScreen(
    onPermissionGranted: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setPermissionGranted(true)
            onPermissionGranted()
        } else {
            viewModel.setPermissionGranted(false)
            val activity = context as? Activity
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
                isPermanentlyDenied = true
                viewModel.setPermissionPermanentlyDenied(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        val currentPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (currentPermission == PackageManager.PERMISSION_GRANTED) {
            viewModel.setPermissionGranted(true)
            onPermissionGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Hardware Permission",
            subtitle = "Microphone Access Required",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPermanentlyDenied) DangerRed.copy(alpha = 0.15f) else PrimaryTeal.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPermanentlyDenied) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isPermanentlyDenied) DangerRed else PrimaryTeal,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isPermanentlyDenied) "Microphone Access Blocked" else "Grant Microphone Access",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isPermanentlyDenied) {
                    "Microphone permission has been permanently denied in Android system settings. Please grant microphone access to capture voice enrollment samples and inspect live call acoustics."
                } else {
                    "EchoShield requires access to your microphone to capture high-fidelity 16kHz audio during voice enrollment and in-app call anti-spoofing verification. No background recording takes place."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (isPermanentlyDenied) {
                EchoPrimaryButton(
                    text = "Open App Settings",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                EchoPrimaryButton(
                    text = "Grant Microphone Permission",
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    leadingIcon = Icons.Default.Security,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            EchoGhostButton(
                text = "Cancel & Return",
                onClick = onNavigateBack,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
