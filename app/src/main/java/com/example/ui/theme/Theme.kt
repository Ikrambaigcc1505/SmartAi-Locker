package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.AccentTheme
import com.example.data.ThemeMode

data class VaultExtendedColors(
    val glassSurface: Color,
    val glassBorder: Color,
    val accentGlow: Color,
    val verified: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean
)

val LocalVaultColors = staticCompositionLocalOf {
    VaultExtendedColors(
        glassSurface = GlassCardDark,
        glassBorder = GlassBorderDark,
        accentGlow = CyberCyan,
        verified = VerifiedGreen,
        warning = WarningAmber,
        danger = CrimsonAlert,
        isDark = true
    )
}

val SmartAiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun SmartAiLockerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentTheme: AccentTheme = AccentTheme.CYBER_CYAN,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val (primaryDark, primaryLight, secondaryDark, secondaryLight) = when (accentTheme) {
        AccentTheme.CYBER_CYAN -> listOf(CyberCyan, CyberCyanDark, IndigoShield, IndigoShieldDark)
        AccentTheme.EMERALD_SHIELD -> listOf(EmeraldShield, EmeraldShieldDark, CyberCyan, CyberCyanDark)
        AccentTheme.ROYAL_AMETHYST -> listOf(RoyalAmethyst, RoyalAmethystDark, CyberCyan, CyberCyanDark)
    }.let { Tuple4(it[0], it[1], it[2], it[3]) }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primaryDark,
            onPrimary = ObsidianBg,
            primaryContainer = GlassCardElevatedDark,
            onPrimaryContainer = primaryDark,
            secondary = secondaryDark,
            onSecondary = Color.White,
            secondaryContainer = GlassCardDark,
            onSecondaryContainer = TextPrimaryDark,
            tertiary = VerifiedGreen,
            background = ObsidianBg,
            onBackground = TextPrimaryDark,
            surface = MidnightSurface,
            onSurface = TextPrimaryDark,
            surfaceVariant = GlassCardDark,
            onSurfaceVariant = TextSecondaryDark,
            outline = GlassBorderDark,
            error = CrimsonAlert,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryLight,
            onPrimary = Color.White,
            primaryContainer = SoftSlateCard,
            onPrimaryContainer = primaryLight,
            secondary = secondaryLight,
            onSecondary = Color.White,
            secondaryContainer = SoftSlateCard,
            onSecondaryContainer = TextPrimaryLight,
            tertiary = VerifiedGreen,
            background = AlabasterBg,
            onBackground = TextPrimaryLight,
            surface = PureSurface,
            onSurface = TextPrimaryLight,
            surfaceVariant = SoftSlateCard,
            onSurfaceVariant = TextSecondaryLight,
            outline = GlassBorderLight,
            error = CrimsonAlert,
            onError = Color.White
        )
    }

    val extendedColors = VaultExtendedColors(
        glassSurface = if (isDark) GlassCardDark.copy(alpha = 0.85f) else PureSurface.copy(alpha = 0.92f),
        glassBorder = if (isDark) GlassBorderDark else GlassBorderLight,
        accentGlow = if (isDark) primaryDark else primaryLight,
        verified = VerifiedGreen,
        warning = WarningAmber,
        danger = CrimsonAlert,
        isDark = isDark
    )

    CompositionLocalProvider(LocalVaultColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmartAiTypography,
            shapes = SmartAiShapes,
            content = content
        )
    }
}

private data class Tuple4<T>(val a: T, val b: T, val c: T, val d: T)
