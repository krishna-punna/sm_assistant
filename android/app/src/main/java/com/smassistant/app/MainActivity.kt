package com.smassistant.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import com.smassistant.app.media.MediaCache
import com.smassistant.app.navigation.SmNavHost
import com.smassistant.app.ui.theme.SmAssistantTheme
import com.smassistant.app.ui.theme.SmTheme
import com.smassistant.app.ui.theme.SmThemes
import com.smassistant.app.ui.theme.ThemeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            val media = MediaCache(applicationContext)
            Thread { media.clearOlderThan() }.start()
        }
        val themes = ThemeStore(applicationContext)
        setContent {
            var themeId by rememberSaveable { mutableStateOf(themes.themeId) }
            LaunchedEffect(themeId) { matchSystemBars(SmThemes.byId(themeId)) }
            SmAssistantTheme(SmThemes.byId(themeId)) {
                SmNavHost(onThemeChosen = { chosen ->
                    themes.save(chosen)
                    themeId = chosen
                })
            }
        }
    }

    /** Every theme's app bar is dark, so status bar icons are light; nav bar icons follow the background. */
    private fun matchSystemBars(theme: SmTheme) {
        val darkBackground = theme.colors.background.luminance() < 0.5f
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = if (darkBackground) {
                SystemBarStyle.dark(DarkScrim)
            } else {
                SystemBarStyle.light(LightScrim, DarkScrim)
            },
        )
    }

    private companion object {
        // The scrims enableEdgeToEdge() uses by default behind 3-button navigation.
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
