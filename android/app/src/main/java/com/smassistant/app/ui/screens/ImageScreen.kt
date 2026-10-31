package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.chat.sessionChatViewModel
import com.smassistant.app.chat.ImagesSaver
import com.smassistant.app.generate.GenerateContentAndMediaUseCase
import com.smassistant.app.generate.appGenerateImages
import com.smassistant.app.generate.imageLabel
import com.smassistant.app.generate.isCarousel
import com.smassistant.app.generate.parseImageTemplates
import com.smassistant.app.generate.text
import com.smassistant.app.generate.pickedImageRequest
import com.smassistant.app.generate.appSmartTrim
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.navigation.Feature
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.ui.components.CarouselPager
import com.smassistant.app.ui.components.ImageTemplatePickers
import com.smassistant.app.ui.components.MediaAgentDropdown
import com.smassistant.app.ui.components.ImagePreview
import com.smassistant.app.ui.components.ImageStrip
import com.smassistant.app.ui.components.PostDialogs
import com.smassistant.app.ui.components.PostInLinkedInButton
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.TextAgentDropdown
import com.smassistant.app.ui.components.BackToHomeButton
import java.io.File

/** S10 Generate Image (docs/requirements/06 §6.2): prompt card, image strip, full preview. */
@Composable
fun ImageScreen(onSetUpAgent: () -> Unit, onSetUpLinkedIn: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val useCase = appGenerateImages(appContext)
        sessionChatViewModel(appContext, Feature.IMAGE.name, createSavedStateHandle(), ImagesSaver) { prompt ->
            useCase.generate(pickedImageRequest(appContext, prompt))
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val postViewModel = viewModel { PostViewModel(appPostToLinkedIn(appContext), appSmartTrim(appContext)) }
    val postState by postViewModel.state.collectAsStateWithLifecycle()

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
        header = {
            TextAgentDropdown(label = "Text agent", showModel = true)
            MediaAgentDropdown()
            ImageTemplatePickers()
        },
        inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
        resultContent = { images ->
            ImageResult(
                images = images,
                posting = postState == PostState.Posting,
                onPost = { selected -> postViewModel.start("", selected) },
                onBackToHome = onBackToHome,
            )
        },
        noAgentMessage = GenerateContentAndMediaUseCase.IMAGES_NOT_SET_UP,
        onRegenerate = viewModel::retry,
    )
    PostDialogs(
        state = postState,
        onTextChange = postViewModel::onTextChange,
        onTrimWithAi = postViewModel::trimWithAi,
        onCutToFit = postViewModel::cutToFit,
        onConfirm = postViewModel::confirm,
        onDismiss = postViewModel::dismiss,
        onOpenSettings = onSetUpLinkedIn,
    )
}

/**
 * One generation: the strip, then Post in LinkedIn for the selected images (IM-4, IM-5).
 * The preview posts the image it shows. Content & Image also posts its text through [onPost].
 * A carousel's pages are shown one at a time and always posted together (SC-I5).
 */
@Composable
fun ImageResult(
    images: List<File>,
    posting: Boolean,
    onPost: (List<File>) -> Unit,
    onBackToHome: () -> Unit,
    canPostWithoutImages: Boolean = false,
) {
    val carousel = remember(images) { isCarousel(images) }
    var selected by rememberSaveable(images) { mutableStateOf(images.indices.toSet()) }
    var previewIndex by rememberSaveable(images) { mutableStateOf<Int?>(null) }
    val appContext = LocalContext.current.applicationContext
    // "Workflow · 2:3" under each image made from a template (docs/design/image-templates.md).
    val labels = remember(images) {
        val templates = runCatching { parseImageTemplates(loadPromptAsset(appContext, "image_templates")) }.getOrDefault(emptyList())
        images.map { file -> imageLabel(file)?.text(templates) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (carousel) {
            CarouselPager(images, onOpen = { previewIndex = it })
        } else {
            ImageStrip(
                images = images,
                selected = selected,
                onToggle = { index -> selected = if (index in selected) selected - index else selected + index },
                onOpen = { previewIndex = it },
                labels = labels,
            )
        }
        PostInLinkedInButton(
            onClick = { onPost(if (carousel) images else images.filterIndexed { index, _ -> index in selected }) },
            enabled = carousel || canPostWithoutImages || selected.isNotEmpty(),
            busy = posting,
        )
    }
    previewIndex?.let { start ->
        ImagePreview(images, start, onDismiss = { previewIndex = null }, noun = if (carousel) "Page" else "Image") { current ->
            PostInLinkedInButton(
                onClick = {
                    previewIndex = null
                    onPost(if (carousel) images else listOf(images[current]))
                },
                busy = posting,
            )
            BackToHomeButton(onClick = {
                previewIndex = null
                onBackToHome()
            })
        }
    }
}
