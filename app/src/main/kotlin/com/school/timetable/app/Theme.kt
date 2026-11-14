package com.school.timetable.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF0E5A6B)
private val Amber = Color(0xFFF4A62A)

private val AppColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEAF0),
    onPrimaryContainer = Color(0xFF00323C),
    secondary = Amber,
    onSecondary = Color(0xFF3A2600),
    secondaryContainer = Color(0xFFFFE8C2),
    onSecondaryContainer = Color(0xFF3A2600),
    background = Color(0xFFF5F9FA),
    onBackground = Color(0xFF172B30),
    surface = Color.White,
    onSurface = Color(0xFF172B30),
    surfaceVariant = Color(0xFFE3ECEE),
    onSurfaceVariant = Color(0xFF455A60),
    outline = Color(0xFF8AA3A9),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

/** Brand theme: deep teal with an amber accent, light only, rounded shapes. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
