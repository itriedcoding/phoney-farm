package com.phonefarm.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The entire palette. Black, white, and four greys — nothing else. */
object Ink {
    val Void = Color(0xFF000000)
    val Layer = Color(0xFF0A0A0A)
    val Layer2 = Color(0xFF121212)
    val Line = Color(0xFF262626)
    val LineSoft = Color(0xFF171717)
    val Chalk = Color(0xFFF5F5F5)
    val Ash = Color(0xFF8A8A8A)
    val Dim = Color(0xFF555555)
    val Paper = Color(0xFFFFFFFF)
}

private val FarmTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        letterSpacing = (-0.2).sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Light,
        fontSize = 38.sp,
        letterSpacing = (-1.5).sp,
        lineHeight = 42.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp,
    ),
)

@Composable
fun PhoneFarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.Paper,
            onPrimary = Ink.Void,
            primaryContainer = Ink.Chalk,
            onPrimaryContainer = Ink.Void,
            secondary = Ink.Ash,
            onSecondary = Ink.Void,
            background = Ink.Void,
            onBackground = Ink.Chalk,
            surface = Ink.Void,
            onSurface = Ink.Chalk,
            surfaceVariant = Ink.Layer,
            onSurfaceVariant = Ink.Ash,
            outline = Ink.Line,
            outlineVariant = Ink.LineSoft,
            error = Ink.Chalk,
            onError = Ink.Void,
        ),
        typography = FarmTypography,
        content = content,
    )
}
