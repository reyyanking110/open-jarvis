package com.openjarvis.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val JarvisCyan = Color(0xFF38BDF8)
val JarvisTeal = Color(0xFF2DD4BF)
val JarvisEmerald = Color(0xFF34D399)
val JarvisAmber = Color(0xFFFBBF24)
val JarvisCoral = Color(0xFFF87171)

private val DarkColors = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF061523),
    primaryContainer = Color(0xFF0F2D45),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = JarvisTeal,
    onSecondary = Color(0xFF05201C),
    secondaryContainer = Color(0xFF103431),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = JarvisAmber,
    background = Color(0xFF0A0E14),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF10161F),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF182230),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF2B394D),
    error = JarvisCoral,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
)

private val JarvisTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun OpenJarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JarvisTypography,
        content = content,
    )
}
