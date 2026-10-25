package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smassistant.app.navigation.Feature
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.PrimaryButton
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

/** S8–S13 chat-style feature screen. Generation and posting are not wired up yet. */
@Composable
fun FeatureScreen(feature: Feature, onBackToHome: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }

    SmScreen(sectionTitle = feature.title, onBackToHome = onBackToHome) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Results will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Enter a topic or prompt") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                "Submit",
                onClick = { /* TODO: call the selected AI agent */ },
                modifier = Modifier.width(110.dp),
            )
        }
    }
}
