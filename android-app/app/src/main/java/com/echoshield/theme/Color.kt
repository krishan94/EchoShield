package com.echoshield.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Centralized Brand & Cyber Theme Color Tokens
val DarkBackground = Color(0xFF0B1220)
val DarkCard = Color(0xFF111C30)
val DarkCardElevated = Color(0xFF17253F)
val DarkSurfaceStroke = Color(0xFF1E2D4A)

val LightBackground = Color(0xFFF6F8FC)
val LightCard = Color(0xFFFFFFFF)
val LightCardElevated = Color(0xFFF0F3F9)
val LightSurfaceStroke = Color(0xFFE2E8F0)

// Brand Core Accents
val PrimaryTeal = Color(0xFF22C7A9)
val PrimaryTealDark = Color(0xFF1AA88E)
val AccentBlue = Color(0xFF438BFF)
val AccentBlueDark = Color(0xFF2B70E0)
val CyberPurple = Color(0xFF8B5CF6)

// Functional & State Indicators
val SuccessGreen = Color(0xFF22C55E)
val WarningAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)
val InconclusiveGray = Color(0xFF64748B)

// Text & Content Muted Tokens
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextTertiaryDark = Color(0xFF64748B)

val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextTertiaryLight = Color(0xFF94A3B8)

// Dynamic Cyber Gradients
val CyberTealGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF22C7A9), Color(0xFF438BFF))
)

val CyberShieldGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF111C30), Color(0xFF17253F), Color(0xFF0F1E38))
)

val ThreatAlertGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFEF4444), Color(0xFFDC2626))
)

val SafeVerifiedGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF22C55E), Color(0xFF16A34A))
)

val RadarGlowBrush = Brush.radialGradient(
    colors = listOf(Color(0x6622C7A9), Color(0x1A438BFF), Color.Transparent)
)
