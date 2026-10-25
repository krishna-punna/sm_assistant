package com.smassistant.app.ui.screens

import androidx.compose.runtime.Composable
import com.smassistant.app.navigation.Feature
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.SmScreen

/** S7 Social Media Assistant hub. Back to Home added here too (Q-02). */
@Composable
fun AssistantHubScreen(onFeatureSelected: (Feature) -> Unit, onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "Social Media Assistant", onBackToHome = onBackToHome) {
        Feature.entries.forEach { feature ->
            MenuButton(feature.title) { onFeatureSelected(feature) }
        }
    }
}
