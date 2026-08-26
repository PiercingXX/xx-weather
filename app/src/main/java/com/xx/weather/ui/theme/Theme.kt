package com.xx.weather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.xx.weather.theme.FOREGROUND_INK
import com.xx.weather.theme.FOREGROUND_WHITE
import com.xx.weather.theme.SyncedTheme
import com.xx.weather.theme.ThemeController
import com.xx.weather.theme.mix

data class WeatherPalette(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val error: Color,
    val isDark: Boolean,
)

fun paletteFor(theme: SyncedTheme): WeatherPalette {
    val fgLong = if (theme.isDark) FOREGROUND_WHITE else FOREGROUND_INK
    val fg = Color(fgLong.toInt())
    val bg = Color(theme.background.toInt())
    val surface = Color(mix(theme.background, fgLong, 0.12).toInt())
    val accentLong = if (theme.isDark) 0xFF8AB4F8L else 0xFF1A5FB4L
    return WeatherPalette(
        background = bg,
        onBackground = fg,
        surface = surface,
        onSurface = fg,
        muted = fg.copy(alpha = 0.70f),
        faint = fg.copy(alpha = 0.45f),
        accent = Color(accentLong.toInt()),
        error = Color(0xFFFFB4AB),
        isDark = theme.isDark,
    )
}

val LocalWeatherPalette = staticCompositionLocalOf {
    paletteFor(ThemeController.DEFAULT_THEME)
}

@Composable
fun XXWeatherTheme(
    theme: SyncedTheme = ThemeController.DEFAULT_THEME,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(theme)
    val scheme = if (theme.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.background,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onSurface,
            error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onSurface,
            error = Color(0xFFBA1A1A),
        )
    }
    CompositionLocalProvider(LocalWeatherPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
