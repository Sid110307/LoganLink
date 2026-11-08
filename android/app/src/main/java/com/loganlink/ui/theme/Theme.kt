package com.loganlink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loganlink.R

val BackgroundDark = Color(0xFF0E0E0E)
val ErrorRed = Color(0xFFFF5E72)
val StateIdle = Color(0xFF8A8F98)
val StateGood = Color(0xFF35C759)
val StateWarning = Color(0xFFFF9F0A)
val StateCritical = Color(0xFFFF3B4E)
val StateConnected = Color(0xFF2EC4B6)
val StateInfo = Color(0xFF5AA9FF)
val Ember = Color(0xFFFF8A5B)
val EmberDeep = Color(0xFFC2512A)

val BlockIndigo = Color(0xFF4B2BE0)
val BlockPeach = Color(0xFFFF9A6B)
val BlockBlush = Color(0xFFE6C7C2)
val BlockMint = Color(0xFFE6F7EC)
val BlockTeal = Color(0xFF2E7D6E)
val BlockInk = Color(0xFF111111)

enum class Severity(val color: Color, val label: String) {
    Ok(StateGood, "OK"), Info(StateInfo, "Info"), CheckSoon(StateWarning, "Check soon"), StopSafely(StateCritical, "Stop safely")
}

private val Light = lightColorScheme(
    primary = Color(0xFF4B2BE0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE2DFFF), onPrimaryContainer = Color(0xFF16104F),
    secondary = Color(0xFF0E8C82), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEFEA), onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFFE85F1F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCB), onTertiaryContainer = Color(0xFF360F00),
    error = ErrorRed,
    background = Color(0xFFF7F7FB), onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFF7F7FB), onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE4E3EE), onSurfaceVariant = Color(0xFF5D5E6C),
    outline = Color(0xFF8E8F9C), outlineVariant = Color(0xFFD5D5E0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F0F7), surfaceContainerHigh = Color(0xFFEAEAF2),
    surfaceContainerHighest = Color(0xFFE4E4ED),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8C7BFF), onPrimary = Color(0xFF14085C),
    primaryContainer = Color(0xFF4B2BE0), onPrimaryContainer = Color(0xFFEDE9FF),
    secondary = Color(0xFF6FD8CC), onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF00504A), onSecondaryContainer = Color(0xFFCDEFEA),
    tertiary = Ember, onTertiary = Color(0xFF3A1200),
    tertiaryContainer = Color(0xFF5A2208), onTertiaryContainer = Color(0xFFFFDBCB),
    error = ErrorRed,
    background = BackgroundDark, onBackground = Color(0xFFE4E4EE),
    surface = BackgroundDark, onSurface = Color(0xFFE4E4EE),
    surfaceVariant = Color(0xFF262626), onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF6E6E6E), outlineVariant = Color(0xFF2E2E2E),
    surfaceContainerLowest = Color(0xFF080808), surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1C1C1C), surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2D2D2D),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight) =
    Font(R.font.outfit, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Outfit = FontFamily(
    listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
        .map(::outfit)
)
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

private val base = Typography()
private fun TextStyle.sans() = copy(fontFamily = Outfit, fontFeatureSettings = "tnum")
private fun TextStyle.serif() = copy(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)

val AppTypography = Typography(
    displayLarge = base.displayLarge.sans(), displayMedium = base.displayMedium.sans(), displaySmall = base.displaySmall.sans(),
    headlineLarge = base.headlineLarge.serif(), headlineMedium = base.headlineMedium.serif(), headlineSmall = base.headlineSmall.serif(),
    titleLarge = base.titleLarge.sans(), titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.sans(), bodyLarge = base.bodyLarge.sans(), bodyMedium = base.bodyMedium.sans(),
    bodySmall = base.bodySmall.sans(), labelLarge = base.labelLarge.sans(), labelMedium = base.labelMedium.sans(),
    labelSmall = base.labelSmall.sans(),
)

enum class ThemeMode { System, Dark, Light }

@Composable
fun LoganTheme(mode: ThemeMode = ThemeMode.System, forceDark: Boolean = false, content: @Composable () -> Unit) {
    val dark = forceDark || when (mode) {
        ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Dark -> true; ThemeMode.Light -> false
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
}
