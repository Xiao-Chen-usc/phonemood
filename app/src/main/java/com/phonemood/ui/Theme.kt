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
val Muted = Color(0xFF526256)
val Cream = Color(0xFFF3F7EE)
val Sage = Color(0xFFDDEBD8)
val Peach = Color(0xFFF1E4D3)
val Line = Color(0xFFD9E4D4)
@Composable
fun PhoneMoodTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White, primaryContainer = Sage, onPrimaryContainer = Ink, background = Cream, onBackground = Ink, surface = Color.White, onSurface = Ink, surfaceVariant = Sage, onSurfaceVariant = Muted, outlineVariant = Line),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 42.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 30.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
        ), content = content)
}
