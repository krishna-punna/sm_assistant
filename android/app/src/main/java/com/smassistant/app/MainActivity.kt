package com.smassistant.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import com.smassistant.app.media.MediaCache
import com.smassistant.app.navigation.Routes
import com.smassistant.app.navigation.SmNavHost
import com.smassistant.app.scheduler.SchedulerEngine
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
            Thread {
                media.clearOlderThan()
                // Safety net for scheduled posts; WorkManager normally still has every run planned.
                runCatching { SchedulerEngine(applicationContext).ensurePlanned() }
            }.start()
        }
        // A notification can ask for Scheduled Posts, for a post waiting for review. The activity is
        // exported (it's the launcher), so only that route is accepted from an intent.
        val openRoute = intent?.getStringExtra(EXTRA_ROUTE)
            ?.takeIf { savedInstanceState == null && it == Routes.SCHEDULERS }
        val themes = ThemeStore(applicationContext)
        setContent {
            var themeId by rememberSaveable { mutableStateOf(themes.themeId) }
            LaunchedEffect(themeId) { matchSystemBars(SmThemes.byId(themeId)) }
            SmAssistantTheme(SmThemes.byId(themeId)) {
                SmNavHost(openRoute = openRoute, onThemeChosen = { chosen ->
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

    companion object {
        /** A route to open over Home when the app starts, from [com.smassistant.app.scheduler.RunNotifier]. */
        const val EXTRA_ROUTE = "com.smassistant.app.ROUTE"

        // The scrims enableEdgeToEdge() uses by default behind 3-button navigation.
        private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
