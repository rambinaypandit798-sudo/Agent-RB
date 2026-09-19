package com.rbagent.assistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RBColorScheme = darkColorScheme(
    primary = NeonCyan, onPrimary = DeepSpaceStart,
    primaryContainer = NeonCyanDim, onPrimaryContainer = TextPrimary,
    secondary = ElectricPink, onSecondary = TextPrimary,
    tertiary = ElectricPinkAlt,
    background = DeepSpaceStart, onBackground = TextPrimary,
    surface = FrostedSlateHeavy, onSurface = TextPrimary,
    surfaceVariant = FrostedSlateLight, onSurfaceVariant = SlateMuted,
    outline = FrostedBorder, error = ElectricPink, onError = TextPrimary
)

@Composable
fun RBAgentTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = DeepSpaceStart.toArgb()
            window.navigationBarColor = DeepSpaceEnd.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(colorScheme = RBColorScheme, typography = RBTypography, content = content)
}
