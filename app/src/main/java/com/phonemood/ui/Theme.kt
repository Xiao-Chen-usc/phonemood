package com.phonemood.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF365F49)
val Ink = Color(0xFF25392E)
val Muted = Color(0xFF738075)
val Cream = Color(0xFFF8F8F2)
val Sage = Color(0xFFE6EDDE)
val Peach = Color(0xFFF4E8DA)
val Line = Color(0xFFE2E7DC)
@Composable
fun PhoneMoodTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White, primaryContainer = Sage, onPrimaryContainer = Ink, background = Cream, onBackground = Ink, surface = Color.White, onSurface = Ink, surfaceVariant = Sage, onSurfaceVariant = Muted, outlineVariant = Line),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 42.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 30.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        ), content = content)
}
