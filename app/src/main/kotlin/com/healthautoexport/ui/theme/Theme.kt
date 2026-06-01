package com.healthautoexport.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = md_light_primary,
    onPrimary = md_light_onPrimary,
    primaryContainer = md_light_primaryContainer,
    onPrimaryContainer = md_light_onPrimaryContainer,
    secondary = md_light_secondary,
    onSecondary = md_light_onSecondary,
    background = md_light_background,
    onBackground = md_light_onBackground,
    surface = md_light_surface,
    onSurface = md_light_onSurface,
    error = md_light_error,
    onError = md_light_onError,
)

private val DarkColors = darkColorScheme(
    primary = md_dark_primary,
    onPrimary = md_dark_onPrimary,
    primaryContainer = md_dark_primaryContainer,
    onPrimaryContainer = md_dark_onPrimaryContainer,
    secondary = md_dark_secondary,
    onSecondary = md_dark_onSecondary,
    background = md_dark_background,
    onBackground = md_dark_onBackground,
    surface = md_dark_surface,
    onSurface = md_dark_onSurface,
    error = md_dark_error,
    onError = md_dark_onError,
)

/**
 * Theme Material 3 gốc của App (task 21.2).
 *
 * Chọn bộ màu sáng/tối theo [darkTheme] (mặc định theo hệ thống) và áp [HealthExportTypography].
 * `AppRoot` và mọi màn hình được bọc trong theme này để có style nhất quán.
 *
 * @param darkTheme `true` để dùng bộ màu tối; mặc định theo cài đặt hệ thống.
 * @param content nội dung UI được áp theme.
 */
@Composable
fun HealthExportTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HealthExportTypography,
        content = content,
    )
}
