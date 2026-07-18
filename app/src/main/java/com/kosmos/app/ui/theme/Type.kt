package com.kosmos.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO: Replace with actual Font(R.font.exo_2) when TTF files are placed in res/font
val Exo2 = FontFamily.SansSerif
val Inter = FontFamily.SansSerif
val JetBrainsMono = FontFamily.Monospace

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Exo2,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.12.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    )
)
