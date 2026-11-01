package com.echoshield.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// ECHOSHIELD OFFICIAL COLOR SYSTEM
// ============================================================================

// --- MIDNIGHT TEAL (DARK MODE) ---
val DarkBackground = Color(0xFF102827)
val DarkSurface = Color(0xFF19413D)
val DarkPrimary = Color(0xFF36A69A)
val DarkSecondary = Color(0xFF8BCBC0)
val DarkPrimaryText = Color(0xFFF4F7F3)
val DarkSecondaryText = Color(0xFFB5C9C3)
val DarkBorder = Color(0xFF31534E)

// Legacy alias compatibility mapped directly to Midnight Teal Dark
val DarkCard = DarkSurface
val DarkCardElevated = Color(0xFF1E4C47)
val DarkSurfaceStroke = DarkBorder
val TextPrimaryDark = DarkPrimaryText
val TextSecondaryDark = DarkSecondaryText
val TextTertiaryDark = Color(0xFF8DA39D)

// --- WARM IVORY (LIGHT MODE) ---
val LightBackground = Color(0xFFF5F3EA)
val LightSurface = Color(0xFFFFFFFF)
val LightPrimary = Color(0xFF246F68)
val LightSecondary = Color(0xFF36A69A)
val LightPrimaryText = Color(0xFF19312F)
val LightSecondaryText = Color(0xFF647873)
val LightBorder = Color(0xFFE0E7E1)

// Legacy alias compatibility mapped directly to Warm Ivory Light
val LightCard = LightSurface
val LightCardElevated = Color(0xFFEDE9DD)
val LightSurfaceStroke = LightBorder
val TextPrimaryLight = LightPrimaryText
val TextSecondaryLight = LightSecondaryText
val TextTertiaryLight = Color(0xFF8A9C97)

// --- BRAND CORE ACCENTS ---
val PrimaryTeal = DarkPrimary
val PrimaryTealDark = LightPrimary
val SecondaryTeal = DarkSecondary

// --- FUNCTIONAL STATUS INDICATORS ---
val SuccessGreen = Color(0xFF229B68)
val WarningAmber = Color(0xFFD99528)
val DangerRed = Color(0xFFD94F52)
val InfoBlue = Color(0xFF3189A5)
val AccentBlue = InfoBlue
val AccentBlueDark = InfoBlue
val InconclusiveGray = Color(0xFF8DA39D)

// --- MODERN REFINED GRADIENTS (NO LOUD PURPLE/BLUE NEON) ---
val CyberTealGradient = Brush.horizontalGradient(
    colors = listOf(DarkPrimary, DarkSecondary)
)

val CyberShieldGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF102827), Color(0xFF19413D))
)

val ThreatAlertGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFD94F52), Color(0xFFB83A3D))
)

val SafeVerifiedGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF229B68), Color(0xFF1A7D53))
)

val RadarGlowBrush = Brush.radialGradient(
    colors = listOf(Color(0x5536A69A), Color(0x1536A69A), Color.Transparent)
)
