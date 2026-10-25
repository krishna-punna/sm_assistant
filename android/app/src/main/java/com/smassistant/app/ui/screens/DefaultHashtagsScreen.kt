package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.SmScreen

/** S14 Default Hash Tags (docs/requirements/10-default-hashtags.md). Saving is not wired up yet. */
@Composable
fun DefaultHashtagsScreen(onBackToHome: () -> Unit) {
    var tags by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }

    SmScreen(sectionTitle = "Default Hash Tags#", onBackToHome = onBackToHome) {
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            placeholder = { Text("#JavaDeveloper #AIEngineer") },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f)) { MenuButton("Save") { /* TODO: normalise and persist */ } }
            Row(Modifier.weight(1f)) { MenuButton("Clear") { tags = "" } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add default hashtags to LinkedIn posts", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
    }
}
