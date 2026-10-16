package com.wananjiaoda.assistant.ui.theme

import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val LightColors = lightColorScheme(
    primary = Color(0xFF1684B5), onPrimary = Color.White,
    primaryContainer = PaleBlue, onPrimaryContainer = BlueInk,
    secondaryContainer = Color(0xFFDFF3FC), onSecondaryContainer = BlueInk,
    secondary = Color(0xFF28728F), onSecondary = Color.White,
    tertiary = Color(0xFFB14F79), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAF3F8), onTertiaryContainer = Color(0xFF173448),
    background = LightBackground, onBackground = Color(0xFF17232B),
    surface = Color.White, onSurface = Color(0xFF17232B),
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = Color(0xFF536A78),
    surfaceTint = Color(0xFF1684B5),
    outline = Color(0xFFB8CBD6), outlineVariant = Color(0xFFD7E4EA),
    error = FailureRed, onError = Color.White,
)
private val DarkColors = darkColorScheme(
    primary = LotianyiBlue, onPrimary = BlueInk,
    primaryContainer = Color(0xFF16445A), onPrimaryContainer = Color(0xFFD0F1FF),
    secondaryContainer = Color(0xFF16445A), onSecondaryContainer = Color(0xFFD0F1FF),
    secondary = Color(0xFF9CCFE3), onSecondary = Color(0xFF173746),
    tertiary = Color(0xFFFFA9CD), onTertiary = Color(0xFF51233B),
    background = DarkBackground, onBackground = Color(0xFFE4EDF2),
    surface = DarkSurface, onSurface = Color(0xFFE4EDF2),
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = Color(0xFFB1C0C9),
    surfaceTint = LotianyiBlue,
    outline = Color(0xFF526773), outlineVariant = Color(0xFF394B56),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

@Composable
fun WanAnJiaoDaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = (if (darkTheme) DarkBackground else LightBackground).toArgb()
            window.navigationBarColor = (if (darkTheme) DarkSurface else Color.White).toArgb()
            @Suppress("DEPRECATION")
            run {
                var flags = view.systemUiVisibility
                flags = if (darkTheme) flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    else flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    flags = if (darkTheme) flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                        else flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
                view.systemUiVisibility = flags
            }
        }
    }
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WanAnJiaoDaTypography, content = content)
}


