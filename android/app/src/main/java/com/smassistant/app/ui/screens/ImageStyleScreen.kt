package com.smassistant.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.smassistant.app.data.ImageLayout
import com.smassistant.app.data.ImageMode
import com.smassistant.app.image.ImageStyleStore
import com.smassistant.app.image.ImageStyleUiState
import com.smassistant.app.image.ImageStyleViewModel
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.SelectDropdown
import com.smassistant.app.ui.components.SmScreen

/**
 * Image Style (docs/design/image-templates.md, IT-I15): the inputs every image template is filled
 * with, saved over the app.yml `image_style` defaults.
 */
@Composable
fun ImageStyleScreen(onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel { ImageStyleViewModel(ImageStyleStore(appContext)) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    ImageStyleContent(
        state = state,
        actions = ImageStyleActions(
            onAudience = viewModel::onAudience,
            onBrandName = viewModel::onBrandName,
            onStyleGuide = viewModel::onStyleGuide,
            onSeries = viewModel::onSeries,
            onMode = viewModel::onMode,
            onLayout = viewModel::onLayout,
            onSave = viewModel::save,
            onReset = viewModel::reset,
        ),
        onBackToHome = onBackToHome,
    )
}

/** Everything the Image Style screen can change, so the content can be tested without a view model. */
data class ImageStyleActions(
    val onAudience: (String) -> Unit = {},
    val onBrandName: (String) -> Unit = {},
    val onStyleGuide: (String) -> Unit = {},
    val onSeries: (Boolean) -> Unit = {},
    val onMode: (ImageMode) -> Unit = {},
    val onLayout: (ImageLayout) -> Unit = {},
    val onSave: () -> Unit = {},
    val onReset: () -> Unit = {},
)

/** How each mode reads in its picker. */
fun ImageMode.label(): String = when (this) {
    ImageMode.AUTO -> "Auto: AI-painted, app-drawn if its text is wrong"
    ImageMode.BOTH -> "Both: two of each"
    ImageMode.DRAWN -> "App-drawn"
    ImageMode.PAINTED -> "AI-painted"
}

/** How each layout reads in its picker. */
fun ImageLayout.label(): String = when (this) {
    ImageLayout.AUTO -> "Auto: one poster"
    ImageLayout.SINGLE -> "Always one image"
}

@Composable
fun ImageStyleContent(state: ImageStyleUiState, actions: ImageStyleActions, onBackToHome: () -> Unit) {
    val style = state.style
    SmScreen(sectionTitle = "Image Style", onBackToHome = onBackToHome) {
        Text(
            "Every image is made from a template filled with your post's content and these settings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = style.audience,
            onValueChange = actions.onAudience,
            label = { Text("Audience") },
            modifier = Modifier.fillMaxWidth().testTag("styleAudience"),
        )
        OutlinedTextField(
            value = style.brandName,
            onValueChange = actions.onBrandName,
            label = { Text("Brand name (optional)") },
            supportingText = { Text("Shown small in the footer of each image.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("styleBrand"),
        )
        OutlinedTextField(
            value = style.styleGuide,
            onValueChange = actions.onStyleGuide,
            label = { Text("Style guide (optional)") },
            supportingText = { Text("Colours, typography, icon style or branding rules.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("styleGuide"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keep a series style")
                Text(
                    "Every image follows the style guide the same way, so your posts look like one series.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = style.series, onCheckedChange = actions.onSeries, modifier = Modifier.testTag("styleSeries"))
        }
        SelectDropdown("Images", style.mode, ImageMode.entries, { it.label() }, actions.onMode, "styleMode")
        Text(
            when (style.mode) {
                ImageMode.AUTO -> "The image agent paints a poster like the samples; if the review finds misspelt, missing or invented text, the app draws it instead."
                ImageMode.BOTH -> "Half the images are painted by the image agent and half drawn by the app."
                ImageMode.DRAWN -> "The app draws the image, so text, code and numbers are exact."
                ImageMode.PAINTED -> "The image agent paints the image from the template, always as one image."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        SelectDropdown("Layout", style.layout, ImageLayout.entries, { it.label() }, actions.onLayout, "styleLayout")
        Text(
            when (style.layout) {
                ImageLayout.AUTO -> "One poster per post, like the samples; content too dense for one readable poster becomes a numbered poster series (1/N), never cut."
                ImageLayout.SINGLE -> "Always one image; long content is drawn with smaller text."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f)) { MenuButton("Save", actions.onSave) }
            Row(Modifier.weight(1f)) { MenuButton("Reset", actions.onReset) }
        }
    }
}
