package com.xx.weather.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live family theme for Compose. [init] loads the last launcher broadcast
 * (or AMOLED Night). [onThemeChanged] is called from [ThemeSyncReceiver]
 * so a visible screen repaints without a restart.
 */
object ThemeController {

    val DEFAULT_THEME = SyncedTheme(
        background = ThemePreset.AMOLED_NIGHT.background,
        isDark = ThemePreset.AMOLED_NIGHT.isDark,
        presetKey = ThemePreset.AMOLED_NIGHT.key,
    )

    private val _theme = MutableStateFlow(DEFAULT_THEME)
    val theme: StateFlow<SyncedTheme> = _theme

    fun init(context: Context) {
        _theme.value = ThemeStore.of(context).load() ?: DEFAULT_THEME
    }

    fun onThemeChanged(theme: SyncedTheme) {
        _theme.value = theme
    }

    fun current(context: Context): SyncedTheme =
        ThemeStore.of(context).load() ?: _theme.value
}
