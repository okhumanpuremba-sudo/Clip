package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ClipForgeDarkColorScheme = darkColorScheme(
    primary = ForgeAmber,
    onPrimary = StudioObsidian,
    primaryContainer = ForgeAmberContainer,
    onPrimaryContainer = OnForgeAmberContainer,
    secondary = TechnicalCyan,
    onSecondary = StudioObsidian,
    secondaryContainer = TechnicalCyanContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = VerifiedEmerald,
    onTertiary = StudioObsidian,
    tertiaryContainer = VerifiedEmeraldContainer,
    onTertiaryContainer = TextPrimary,
    error = WarningCoral,
    errorContainer = WarningCoralContainer,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder
)

val ClipForgeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun ClipForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ClipForgeDarkColorScheme,
        typography = Typography,
        shapes = ClipForgeShapes
    ) {
        ProvideStudioTextToolbar(content = content)
    }
}
