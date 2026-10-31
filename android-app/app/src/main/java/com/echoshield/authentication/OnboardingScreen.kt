package com.echoshield.authentication

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.core.components.EchoGhostButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.PrimaryTeal

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val badge: String
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }

    val pages = listOf(
        OnboardingStep(
            title = "Protect Your Voice Identity",
            subtitle = "Consent-Based Family Biometrics",
            description = "Register your unique acoustic profile with 3 guided voice samples. Samples are transformed into 192-dimensional mathematical embeddings and securely encrypted. Raw audio is never stored permanently.",
            icon = Icons.Default.RecordVoiceOver,
            badge = "STEP 1 • ENROLLMENT"
        ),
        OnboardingStep(
            title = "Verify Every In-App Call",
            subtitle = "SpeechBrain ECAPA-TDNN & Anti-Spoofing",
            description = "During encrypted in-app voice calls, EchoShield's dual-engine pipeline verifies speaker similarity while detecting synthetic text-to-speech, voice conversion, and replay attacks in real time.",
            icon = Icons.Default.Security,
            badge = "STEP 2 • REAL-TIME AI SHIELD"
        ),
        OnboardingStep(
            title = "Stay Informed",
            subtitle = "Forensic Reports & Threat Transparency",
            description = "Receive instant alerts when acoustic anomalies or spoof risks are detected. Inspect forensic acoustic breakdowns and maintain full sovereignty over your biometric data with one-tap purge controls.",
            icon = Icons.Default.Assessment,
            badge = "STEP 3 • AUDIT & TRANSPARENCY"
        )
    )

    val currentStep = pages[currentPage]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        // Top Bar: Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EchoShield",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PrimaryTeal
            )
            if (currentPage < pages.size - 1) {
                EchoGhostButton(
                    text = "Skip",
                    onClick = onFinishOnboarding,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        Spacer(modifier = Modifier.weight(0.8f))

        // Center Content with Animation
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboardingContent"
        ) { step ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon Container
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(PrimaryTeal.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = step.title,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(68.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = step.badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentBlue
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = step.title,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryTeal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Page Indicator Dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.indices.forEach { index ->
                val isSelected = index == currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (isSelected) 28.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) PrimaryTeal else MaterialTheme.colorScheme.outline)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (currentPage > 0) {
                Box(modifier = Modifier.weight(1f)) {
                    EchoPrimaryButton(
                        text = "Back",
                        onClick = { currentPage-- },
                        enabled = true
                    )
                }
            }

            Box(modifier = Modifier.weight(2f)) {
                if (currentPage < pages.size - 1) {
                    EchoPrimaryButton(
                        text = "Next",
                        onClick = { currentPage++ }
                    )
                } else {
                    EchoPrimaryButton(
                        text = "Get Started",
                        onClick = onFinishOnboarding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

