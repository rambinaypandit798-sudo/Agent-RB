package com.rbagent.assistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RBTypography = Typography(
    displayLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 32.sp, 40.sp, color = TextPrimary),
    headlineMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 22.sp, 28.sp, color = TextPrimary),
    titleLarge = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 18.sp, 24.sp, color = TextPrimary),
    titleMedium = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 16.sp, 22.sp, color = TextPrimary),
    bodyLarge = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 15.sp, 22.sp, color = TextPrimary),
    bodyMedium = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 14.sp, 20.sp, color = TextSecondary),
    labelMedium = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 12.sp, 16.sp, color = SlateMuted),
    labelSmall = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 10.sp, 14.sp, color = SlateMuted)
)
