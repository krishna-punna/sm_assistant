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
    CompositionLocalProvider(LocalSmTheme provides theme, LocalReduceMotion provides systemReduceMotion()) {
        MaterialTheme(
            colorScheme = theme.colors,
            typography = Typography().withFonts(body = theme.fontFamily, heading = theme.headingFontFamily),
            content = content,
        )
    }
}

/** Display, headline and title styles take [heading]; body and label styles (buttons) take [body]. */
private fun Typography.withFonts(body: FontFamily?, heading: FontFamily?): Typography {
    if (body == null && heading == null) return this
    fun TextStyle.f() = if (body == null) this else copy(fontFamily = body)
    fun TextStyle.h() = if (heading == null) this else copy(fontFamily = heading)
    return Typography(
        displayLarge = displayLarge.h(), displayMedium = displayMedium.h(), displaySmall = displaySmall.h(),
        headlineLarge = headlineLarge.h(), headlineMedium = headlineMedium.h(), headlineSmall = headlineSmall.h(),
        titleLarge = titleLarge.h(), titleMedium = titleMedium.h(), titleSmall = titleSmall.h(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}
