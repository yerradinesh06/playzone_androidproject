package com.ps22.tournament.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================================================
// PLAYZONE LUXURY PALETTE (Exact User Specifications)
// #183630 (Deep Forest Spruce Green)
// #E3DAC9 (Soft Alabaster Bone Cream)
// #E5C690 (Champagne Gold / Warm Ochre)
// #8B5CF6 (PlayZone Brand Purple Pin from Logo)
// ==================================================

// DOMINANT SWATCHES
val PlayZoneDeepSpruce = Color(0xFF183630)        // #183630 Deep Forest Spruce
val PlayZoneDarkSpruce = Color(0xFF102521)        // Deeper obsidian spruce
val PlayZoneAlabaster = Color(0xFFE3DAC9)         // #E3DAC9 Soft Alabaster Cream
val PlayZoneAlabasterLight = Color(0xFFF7F4EE)    // Off-white bone surface
val PlayZoneChampagneGold = Color(0xFFE5C690)     // #E5C690 Champagne Gold
val PlayZoneGoldHover = Color(0xFFD6B375)         // Deep gold for pressed state
val PlayZoneBrandPurple = Color(0xFF8B5CF6)       // #8B5CF6 Electric Violet Pin from Logo
val PlayZoneBrandPurpleDark = Color(0xFF7C3AED)   // Deep Purple
val PlayZoneBrandPurpleLight = Color(0xFFDDD6FE)  // Soft Lavender

// CONTRAST TEXT & DIVIDERS
val PlayZoneWhite = Color(0xFFFFFFFF)
val PlayZoneTextDark = Color(0xFF183630)          // Deep spruce text on light bone
val PlayZoneTextMuted = Color(0xFF5E756F)         // Secondary text on light
val PlayZoneTextLight = Color(0xFFF8F5EE)         // Cream text on dark spruce
val PlayZoneTextLightMuted = Color(0xFFB5C4BE)    // Muted text on dark spruce
val PlayZoneBorderSpruce = Color(0xFF254B43)      // Border on dark
val PlayZoneBorderBone = Color(0xFFD8CEBD)        // Border on light

// STATUS COLOURS
val PlayZoneSuccessGreen = Color(0xFF34D399)
val PlayZoneWarningAmber = Color(0xFFFBBF24)
val PlayZoneErrorRed = Color(0xFFF87171)
val PlayZoneInfoBlue = Color(0xFF60A5FA)

// Backward Compatibility Aliases for All App Components
val PlayZoneDeepNavy = PlayZoneDeepSpruce
val PlayZoneSecondaryNavy = PlayZoneDarkSpruce
val PlayZoneSlate = Color(0xFF152F2A)
val PlayZoneWarmIvory = PlayZoneAlabasterLight
val PlayZoneSoftCream = PlayZoneAlabaster
val PlayZoneDivider = PlayZoneBorderBone
val PlayZonePrimaryCoral = PlayZoneChampagneGold
val PlayZoneDeepCoral = PlayZoneGoldHover
val PlayZonePeachLight = PlayZoneAlabaster
val PlayZonePrimaryText = PlayZoneTextDark
val PlayZoneSecondaryText = PlayZoneTextMuted

val PrimaryPlum = PlayZoneChampagneGold
val PrimaryPlumLight = PlayZoneAlabaster
val PrimaryPlumDark = PlayZoneGoldHover
val PeachAccent = PlayZoneBrandPurple
val PeachAccentLight = PlayZoneBrandPurpleLight

val DeepBackground = PlayZoneDeepSpruce
val DarkSurface = PlayZoneDarkSpruce
val DarkSurfaceVariant = PlayZoneSlate
val LightSurface = PlayZoneAlabasterLight
val WarmSurface = PlayZoneAlabaster
val TextPrimary = PlayZoneTextDark
val TextSecondary = PlayZoneTextMuted
val TextMuted = PlayZoneTextMuted
val BorderLight = PlayZoneBorderBone
val BorderDark = PlayZoneBorderSpruce

val SuccessGreen = PlayZoneSuccessGreen
val WarningAmber = PlayZoneWarningAmber
val ErrorRed = PlayZoneErrorRed
val InfoBlue = PlayZoneInfoBlue

private val LightColors = lightColorScheme(
    primary = PlayZoneChampagneGold,
    onPrimary = PlayZoneDeepSpruce,
    primaryContainer = PlayZoneAlabaster,
    onPrimaryContainer = PlayZoneDeepSpruce,
    secondary = PlayZoneBrandPurple,
    onSecondary = Color.White,
    secondaryContainer = PlayZoneBrandPurpleLight,
    onSecondaryContainer = PlayZoneBrandPurpleDark,
    tertiary = PlayZoneDeepSpruce,
    onTertiary = Color.White,
    background = PlayZoneAlabasterLight,
    onBackground = PlayZoneTextDark,
    surface = PlayZoneWhite,
    onSurface = PlayZoneTextDark,
    surfaceVariant = PlayZoneAlabaster,
    onSurfaceVariant = PlayZoneTextMuted,
    outline = PlayZoneBorderBone,
    outlineVariant = Color(0xFFCCC0AE),
    error = PlayZoneErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = PlayZoneChampagneGold,
    onPrimary = PlayZoneDeepSpruce,
    primaryContainer = PlayZoneDarkSpruce,
    onPrimaryContainer = PlayZoneChampagneGold,
    secondary = PlayZoneBrandPurple,
    onSecondary = Color.White,
    secondaryContainer = PlayZoneSlate,
    onSecondaryContainer = PlayZoneAlabaster,
    tertiary = PlayZoneBrandPurple,
    onTertiary = Color.White,
    background = PlayZoneDeepSpruce,
    onBackground = PlayZoneTextLight,
    surface = PlayZoneDarkSpruce,
    onSurface = PlayZoneTextLight,
    surfaceVariant = PlayZoneSlate,
    onSurfaceVariant = PlayZoneTextLightMuted,
    outline = PlayZoneBorderSpruce,
    outlineVariant = Color(0xFF2A534B),
    error = PlayZoneErrorRed,
    onError = Color.White,
)

private val PlayZoneTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.8.sp,
    ),
)

private val PlayZoneShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun Ps22Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PlayZoneTypography,
        shapes = PlayZoneShapes,
        content = content,
    )
}
