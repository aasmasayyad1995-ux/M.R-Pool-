package com.mrpool.eightball.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FeltGreen = Color(0xFF0B6E3A)
val FeltDeep = Color(0xFF07351E)
val Ink = Color(0xFF0B1416)
val InkSoft = Color(0xFF132023)
val Gold = Color(0xFFF4C542)
val GoldDim = Color(0xFF8A6E1F)
val Chalk = Color(0xFFE9F1EC)
val Crimson = Color(0xFFD5453F)
val Cyan = Color(0xFF7FF0FF)

private val MrPoolColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = FeltGreen,
    onSecondary = Chalk,
    background = Ink,
    onBackground = Chalk,
    surface = InkSoft,
    onSurface = Chalk,
    error = Crimson
)

private val MrPoolTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
)

@Composable
fun MrPoolTheme(content: @Composable () -> Unit) {
    // A pool hall is dark in every light, so the app never follows the system theme.
    MaterialTheme(
        colorScheme = MrPoolColors,
        typography = MrPoolTypography,
        content = content
    )
}
