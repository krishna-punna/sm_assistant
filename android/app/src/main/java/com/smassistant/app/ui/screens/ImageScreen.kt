package com.smassistant.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.agents.GeminiImageAgent
import com.smassistant.app.agents.IMAGES_PER_REQUEST
import com.smassistant.app.chat.ChatViewModel
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.generate.GenerateImagesUseCase
import com.smassistant.app.media.MediaCache
import com.smassistant.app.navigation.Feature
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.ui.components.ImagePreview
import com.smassistant.app.ui.components.ImageStrip
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.SmButton
import com.smassistant.app.ui.theme.SmGreen
import java.io.File

/** S10 Generate Image (docs/requirements/06 §6.2): prompt card, image strip, full preview. */
@Composable
fun ImageScreen(onSetUpAgent: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val settings = ApiSettingsStore(appContext)
        val agent = GeminiImageAgent({ settings.load(ApiService.GEMINI) }, MediaCache(appContext))
        val useCase = GenerateImagesUseCase(
            styleTemplate = loadPromptAsset(appContext, "image"),
            agent = { agent.takeIf { settings.load(ApiService.GEMINI).let { it.key.isNotBlank() && it.imageModel.isNotBlank() } } },
            count = IMAGES_PER_REQUEST,
        )
        ChatViewModel<List<File>> { prompt -> useCase(prompt) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        title = Feature.IMAGE.title,
        state = state,
        placeholder = "Paste your prompt",
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onSetUpAgent = onSetUpAgent,
        onBackToHome = onBackToHome,
        inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
        resultContent = { images -> ImageResult(images, onBackToHome) },
        noAgentMessage = "Images are made with Gemini. Add a Gemini API key in AI Agent settings.",
    )
}

/** One generation: the strip, with the selection and preview it controls. */
@Composable
fun ImageResult(images: List<File>, onBackToHome: () -> Unit) {
    var selected by rememberSaveable(images) { mutableStateOf(images.indices.toSet()) }
    var previewIndex by rememberSaveable(images) { mutableStateOf<Int?>(null) }

    ImageStrip(
        images = images,
        selected = selected,
        onToggle = { index -> selected = if (index in selected) selected - index else selected + index },
        onOpen = { previewIndex = it },
    )
    previewIndex?.let { start ->
        ImagePreview(images, start, onDismiss = { previewIndex = null }) {
            SmButton("Back to Home", SmGreen) {
                previewIndex = null
                onBackToHome()
            }
        }
    }
}
