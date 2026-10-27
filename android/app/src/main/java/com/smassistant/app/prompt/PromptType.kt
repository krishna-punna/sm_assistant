package com.smassistant.app.prompt

import com.smassistant.app.data.MediaPurpose

/**
 * What a prepared prompt is for (docs/requirements/14, "Prompt Type"): a LinkedIn post, content to be
 * shown as an image or a video, or both. [post] asks for the post prompt, kept within LinkedIn's
 * limit; [media] asks for the "no limit" content prompt behind the image or video.
 */
enum class PromptType(val label: String, val post: Boolean, val media: MediaPurpose?) {
    TEXT("Text", post = true, media = null),
    IMAGE("Image", post = false, media = MediaPurpose.IMAGE),
    VIDEO("Video", post = false, media = MediaPurpose.VIDEO),
    TEXT_AND_IMAGE("Text & Image", post = true, media = MediaPurpose.IMAGE),
    TEXT_AND_VIDEO("Text & Video", post = true, media = MediaPurpose.VIDEO),
}

/** Card label of the prompt for the LinkedIn post. */
const val POST_PROMPT_LABEL = "Text Generation Prompt [Within LinkedIn Post Limit]"

/** Card label of the prompt for the content behind an image or video. */
const val CONTENT_PROMPT_LABEL = "Text Generation Prompt [No Limit]"
