package com.visionit.vcut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Clean White & Soft Sky-Blue Design Palette
val PureWhite = Color(0xFFFFFFFF)
val IceBlueTint = Color(0xFFF2F7FF)
val SoftSkySurface = Color(0xFFEAF4FF)
val SkyBluePrimary = Color(0xFF2EA7F8)
val SkyBlueDeep = Color(0xFF0284C7)
val OceanWaveBlue = Color(0xFF1D9BF0)
val PastelLavenderTrack = Color(0xFFE4E9FF)
val MintClipBorder = Color(0xFF38BDF8)
val CharcoalText = Color(0xFF111827)
val SlateSubtext = Color(0xFF64748B)
val SubtleBorder = Color(0xFFE2E8F0)
val DangerRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)

private val VisionCutLightColors = lightColorScheme(
    primary = SkyBluePrimary,
    onPrimary = PureWhite,
    primaryContainer = SoftSkySurface,
    onPrimaryContainer = CharcoalText,
    secondary = SkyBlueDeep,
    onSecondary = PureWhite,
    background = PureWhite,
    onBackground = CharcoalText,
    surface = PureWhite,
    onSurface = CharcoalText,
    surfaceVariant = IceBlueTint,
    onSurfaceVariant = SlateSubtext,
    outline = SubtleBorder,
    error = DangerRed
)

@Composable
fun VisionCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VisionCutLightColors,
        content = content
    )
}
