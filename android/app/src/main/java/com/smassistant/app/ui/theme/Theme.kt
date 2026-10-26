package com.smassistant.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** The app theme in use; components read button and bar colours, shapes and icon style from it. */
val LocalSmTheme = staticCompositionLocalOf { SmThemes.Classic }

@Composable
fun SmAssistantTheme(theme: SmTheme = SmThemes.Classic, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSmTheme provides theme) {
        MaterialTheme(
            colorScheme = theme.colors,
            typography = Typography().withFont(theme.fontFamily),
            content = content,
        )
    }
}

private fun Typography.withFont(family: FontFamily?): Typography {
    if (family == null) return this
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}
