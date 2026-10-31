package com.echoshield.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.PrimaryTeal

@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Legal & Biometric Disclosures",
            subtitle = "Privacy Policy & Terms of Service",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            EchoCard(padding = 18.dp) {
                Text(
                    text = "Effective Date: October 31, 2026",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryTeal
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "EchoShield Biometric Privacy Charter",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LegalSection(
                title = "1. Biometric Data Collection & Scope",
                body = "EchoShield collects acoustic voice samples solely for the purpose of computing mathematical speaker verification embeddings (192-dimensional floating-point vectors) using SpeechBrain ECAPA-TDNN neural architectures. We do not sell, rent, or trade biometric information under any circumstances."
            )

            Spacer(modifier = Modifier.height(14.dp))

            LegalSection(
                title = "2. Ephemeral Processing of Live Call Audio",
                body = "During encrypted in-app voice calls, live audio chunks (16kHz 16-bit mono PCM) are buffered ephemerally in volatile RAM strictly for the duration of inference. Chunks are immediately deallocated upon feature extraction. No continuous audio recordings of family calls are persisted to permanent physical media."
            )

            Spacer(modifier = Modifier.height(14.dp))

            LegalSection(
                title = "3. Encryption & Hardware Keystore Standards",
                body = "All stored biometric embeddings and authorization tokens are encrypted at rest using AES-256-GCM authenticated encryption backed by the Android Keystore system. Key material never leaves the secure hardware element."
            )

            Spacer(modifier = Modifier.height(14.dp))

            LegalSection(
                title = "4. Consent Revocation & Cryptographic Erasure",
                body = "In compliance with the Illinois Biometric Information Privacy Act (BIPA), European General Data Protection Regulation (GDPR Article 9), and California Consumer Privacy Act (CCPA), users possess the absolute right to revoke biometric consent and trigger instantaneous cryptographic erasure through Settings > Privacy > Voice Data."
            )

            Spacer(modifier = Modifier.height(14.dp))

            LegalSection(
                title = "5. Service Limitations & Disclaimers",
                body = "EchoShield provides probabilistic artificial intelligence defense against voice cloning and deepfakes. While tested against state-of-the-art text-to-speech and voice conversion engines, no software can guarantee 100% elimination of spoofing risk. EchoShield should be utilized in conjunction with family emergency passphrases."
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    EchoCard(padding = 16.dp) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

