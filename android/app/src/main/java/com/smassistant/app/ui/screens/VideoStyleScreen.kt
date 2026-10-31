package com.smassistant.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.SmScreen
import com.smassistant.app.video.VideoStyleStore
import com.smassistant.app.video.VideoStyleUiState
import com.smassistant.app.video.VideoStyleViewModel

/**
 * Video Style (docs/requirements/19, SP-15): the inputs every video template is filled with, saved
 * over the app.yml `video_style` defaults and kept apart from the Image Style.
 */
@Composable
fun VideoStyleScreen(onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel { VideoStyleViewModel(VideoStyleStore(appContext)) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    VideoStyleContent(
        state = state,
        actions = VideoStyleActions(
            onAudience = viewModel::onAudience,
            onBrandName = viewModel::onBrandName,
            onStyleGuide = viewModel::onStyleGuide,
            onSave = viewModel::save,
            onReset = viewModel::reset,
        ),
        onBackToHome = onBackToHome,
    )
}

/** Everything the Video Style screen can change, so the content can be tested without a view model. */
data class VideoStyleActions(
    val onAudience: (String) -> Unit = {},
    val onBrandName: (String) -> Unit = {},
    val onStyleGuide: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onReset: () -> Unit = {},
)

@Composable
fun VideoStyleContent(state: VideoStyleUiState, actions: VideoStyleActions, onBackToHome: () -> Unit) {
    val style = state.style
    SmScreen(sectionTitle = "Video Style", onBackToHome = onBackToHome) {
        Text(
            "Every video's slides are made from a template filled with your post's content and these settings. " +
                "They don't change your images.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = style.audience,
            onValueChange = actions.onAudience,
            label = { Text("Audience") },
            modifier = Modifier.fillMaxWidth().testTag("videoStyleAudience"),
        )
        OutlinedTextField(
            value = style.brandName,
            onValueChange = actions.onBrandName,
            label = { Text("Brand name (optional)") },
            supportingText = { Text("Shown small in the footer of each slide.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("videoStyleBrand"),
        )
        OutlinedTextField(
            value = style.styleGuide,
            onValueChange = actions.onStyleGuide,
            label = { Text("Style guide (optional)") },
            supportingText = { Text("Colours, typography, icon style or branding rules for the slides.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("videoStyleGuide"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f)) { MenuButton("Save", actions.onSave) }
            Row(Modifier.weight(1f)) { MenuButton("Reset", actions.onReset) }
        }
    }
}
