package com.smassistant.app.ui.screens

import androidx.compose.runtime.Composable
import com.smassistant.app.navigation.Routes
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.SmScreen

/**
 * S1 Home. The wireframe shows "AI Agent" twice (Q-01); only one is shown
 * until that is resolved.
 */
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    SmScreen(sectionTitle = null, onBackToHome = null) {
        MenuButton("LinkedIn API") { onNavigate(Routes.LINKEDIN_SETTINGS) }
        MenuButton("AI Agent") { onNavigate(Routes.AI_AGENTS) }
        MenuButton("SM Assistant") { onNavigate(Routes.HUB) }
        MenuButton("Default Hash Tags") { onNavigate(Routes.DEFAULT_HASHTAGS) }
        MenuButton("API Status") { onNavigate(Routes.API_STATUS) }
    }
}
