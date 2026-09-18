package com.KonstantinShramko.Ulenspigel.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SandPrimary,
    onPrimary = Color(0xFF3C2E20),
    primaryContainer = Color(0xFF4D3D2C),
    onPrimaryContainer = SandPrimary,
    secondary = SandSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2D3033),
    onSecondaryContainer = SandPrimary,
    background = NightBackground,
    surface = NightSurface,
    onBackground = Color(0xFFE6E1D9),
    onSurface = Color(0xFFE6E1D9),
    outline = DimGold
)

private val LightColorScheme = lightColorScheme(
    primary = LeatherPrimary,
    onPrimary = Color.White,
    primaryContainer = AntiqueGold.copy(alpha = 0.2f),
    onPrimaryContainer = LeatherPrimary,
    secondary = LeatherSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E5CF),
    onSecondaryContainer = LeatherPrimary,
    background = PaperBackground,
    surface = PaperSurface,
    onBackground = Color(0xFF3C2E20),
    onSurface = Color(0xFF3C2E20),
    outline = AntiqueGold
)

@Composable
fun UlenspigelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
