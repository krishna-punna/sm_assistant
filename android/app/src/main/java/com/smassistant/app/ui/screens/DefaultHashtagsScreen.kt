package com.smassistant.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.hashtags.DefaultHashtagsUiState
import com.smassistant.app.hashtags.DefaultHashtagsViewModel
import com.smassistant.app.hashtags.HashtagSettingsStore
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.SmScreen
import com.smassistant.app.ui.theme.SmAmber

/** S14 Default Hash Tags (docs/requirements/10-default-hashtags.md). */
@Composable
fun DefaultHashtagsScreen(onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel { DefaultHashtagsViewModel(HashtagSettingsStore(appContext)) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    DefaultHashtagsContent(
        state = state,
        onTextChange = viewModel::onTextChange,
        onSave = viewModel::onSave,
        onClear = viewModel::onClear,
        onEnabledChange = viewModel::onEnabledChange,
        onBackToHome = onBackToHome,
    )
}

@Composable
fun DefaultHashtagsContent(
    state: DefaultHashtagsUiState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onBackToHome: () -> Unit,
) {
    val error = state.error
    SmScreen(sectionTitle = "Default Hash Tags#", onBackToHome = onBackToHome) {
        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            placeholder = { Text("#JavaDeveloper #AIEngineer") },
            isError = error != null,
            supportingText = if (error != null) {
                { Text(error) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("hashtagText"),
        )
        state.warning?.let {
            Text(it, color = SmAmber, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f)) { MenuButton("Save", onSave) }
            Row(Modifier.weight(1f)) { MenuButton("Clear", onClear) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add default hashtags to LinkedIn posts", modifier = Modifier.weight(1f))
            Switch(
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("hashtagToggle"),
            )
        }
    }
}
