package com.smassistant.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SmBlue,
    onPrimary = Color.White,
    secondary = SmGreen,
    onSecondary = Color.White,
    tertiary = SmAmber,
    onTertiary = Color.Black,
    surfaceVariant = SmLavender,
)

@Composable
fun SmAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
