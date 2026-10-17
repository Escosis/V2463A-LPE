package com.escosis.v2463alpe.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.escosis.v2463alpe.Prefs
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalThemeMode = compositionLocalOf { 0 }
val LocalSetThemeMode = compositionLocalOf<(Int) -> Unit> { {} }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var themeMode by remember { mutableIntStateOf(Prefs.getThemeMode(context)) }

    val systemIsDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemIsDark
    }

    val configuration = LocalConfiguration.current
    val newConfig = remember(configuration, isDark) {
        Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }

    val schemeMode = when (themeMode) {
        1 -> ColorSchemeMode.Light
        2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }

    val controller = remember(themeMode) {
        ThemeController(colorSchemeMode = schemeMode)
    }

    val setMode: (Int) -> Unit = { m ->
        themeMode = m
        Prefs.setThemeMode(context, m)
    }

    CompositionLocalProvider(
        LocalConfiguration provides newConfig,
        LocalThemeMode provides themeMode,
        LocalSetThemeMode provides setMode,
    ) {
        MiuixTheme(controller = controller, content = content)
    }
}