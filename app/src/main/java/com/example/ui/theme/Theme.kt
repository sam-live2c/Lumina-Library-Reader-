package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AppSettingsManager

// 1. Clean Alabaster White Color Scheme
private val WhiteColorScheme = lightColorScheme(
    primary = LuminaAccentPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7EFE5),
    onPrimaryContainer = Color(0xFFA66D35),
    secondary = DeepSlate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = DeepSlate,
    tertiary = LuminaAccentLight,
    onTertiary = DeepSlate,
    background = Color(0xFFFBFBFB),
    onBackground = Color(0xFF181D26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181D26),
    surfaceVariant = Color(0xFFF4F6F8),
    onSurfaceVariant = Color(0xFF5A6679),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFEDE8E1)
)

// 2. Warm Linen Cream Color Scheme
private val LinenColorScheme = lightColorScheme(
    primary = Color(0xFF8C5E2D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBE0D0),
    onPrimaryContainer = Color(0xFF5E3C17),
    secondary = Color(0xFF5C5245),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFE8DB),
    onSecondaryContainer = Color(0xFF26211B),
    tertiary = Color(0xFFAD7841),
    onTertiary = Color.White,
    background = Color(0xFFF7F3E9),
    onBackground = Color(0xFF26211B),
    surface = Color(0xFFFAF6EE),
    onSurface = Color(0xFF26211B),
    surfaceVariant = Color(0xFFEFE8DB),
    onSurfaceVariant = Color(0xFF6B6256),
    outline = Color(0xFFDCD2C0),
    outlineVariant = Color(0xFFE6DCCB)
)

// 3. Heritage Classic Sepia Color Scheme
private val SepiaColorScheme = lightColorScheme(
    primary = Color(0xFF7C4F22),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5D5BD),
    onPrimaryContainer = Color(0xFF4A2B0E),
    secondary = Color(0xFF6B5844),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3D6BE),
    onSecondaryContainer = Color(0xFF2E2419),
    tertiary = Color(0xFF96632F),
    onTertiary = Color.White,
    background = Color(0xFFEFE5D3),
    onBackground = Color(0xFF2E2419),
    surface = Color(0xFFF5EBD9),
    onSurface = Color(0xFF2E2419),
    surfaceVariant = Color(0xFFE3D6BE),
    onSurfaceVariant = Color(0xFF756550),
    outline = Color(0xFFD4C3A7),
    outlineVariant = Color(0xFFDECFB4)
)

// 4. Deep Velvet Night Color Scheme
private val NightColorScheme = darkColorScheme(
    primary = Color(0xFFDFAB72),
    onPrimary = Color(0xFF0F141C),
    primaryContainer = Color(0xFF222B3A),
    onPrimaryContainer = Color(0xFFDFAB72),
    secondary = Color(0xFF90A4BE),
    onSecondary = Color(0xFF0F141C),
    secondaryContainer = Color(0xFF1F2837),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFFC68A4C),
    onTertiary = Color(0xFF0F141C),
    background = Color(0xFF0F141C),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF161D28),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1F2837),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF283447),
    outlineVariant = Color(0xFF334155)
)

@Composable
fun LuminaTheme(
    themeMode: ReaderThemeMode = AppSettingsManager.currentAppTheme.collectAsStateWithLifecycle().value,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ReaderThemeMode.WHITE -> WhiteColorScheme
        ReaderThemeMode.CREAM -> LinenColorScheme
        ReaderThemeMode.SEPIA -> SepiaColorScheme
        ReaderThemeMode.NIGHT -> NightColorScheme
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !themeMode.isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !themeMode.isDark
            }
        }
    }

    val currentDensity = LocalDensity.current
    val stableDensity = remember(currentDensity) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.15f)
        )
    }

    CompositionLocalProvider(LocalDensity provides stableDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    LuminaTheme(
        themeMode = if (darkTheme) ReaderThemeMode.NIGHT else ReaderThemeMode.WHITE,
        content = content
    )
}

