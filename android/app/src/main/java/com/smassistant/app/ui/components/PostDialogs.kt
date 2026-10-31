package com.smassistant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smassistant.app.hashtags.PostText
import com.smassistant.app.hashtags.postLength
import com.smassistant.app.linkedin.NEAR_LIMIT_SHARE
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.feedFold
import com.smassistant.app.linkedin.hashtagRanges
import com.smassistant.app.media.isVideo

/**
 * Dialogs for the Post in LinkedIn flow (LP-3, LP-4, LP-6): confirm the final text,
 * then the result with Open, or the error with a way to LinkedIn settings.
 */
@Composable
fun PostDialogs(
    state: PostState,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onTrimWithAi: (() -> Unit)? = null,
    onCutToFit: () -> Unit = {},
    onSaveDraft: (() -> Unit)? = null,
) {
    when (state) {
        PostState.Idle, PostState.Posting -> Unit
        is PostState.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Post to LinkedIn?") },
            text = { ConfirmPostContent(state, onTrimWithAi, onCutToFit, onTextChange) },
            confirmButton = { TextButton(onClick = onConfirm, enabled = state.canPost) { Text("Post") } },
            dismissButton = {
                Row {
                    if (onSaveDraft != null) {
                        TextButton(
                            onClick = onSaveDraft,
                            enabled = !state.trimming && (state.text.isNotBlank() || state.images.isNotEmpty()),
                            modifier = Modifier.testTag("saveDraft"),
                        ) { Text("Save draft") }
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            },
        )
        PostState.DraftSaved -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Saved to Drafts") },
            text = { Text("Find it under Drafts on Home to edit and post it later.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is PostState.Posted -> {
            val uriHandler = LocalUriHandler.current
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Posted to LinkedIn") },
                text = { Text("Your post is live.") },
                confirmButton = {
                    TextButton(onClick = {
                        onDismiss()
                        uriHandler.openUri(state.post.url)
                    }) { Text("Open") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
            )
        }
        is PostState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't post") },
            text = { Text(state.message) },
            confirmButton = {
                if (state.openSettings) {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenSettings()
                    }) { Text("LinkedIn settings") }
                } else {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            },
            dismissButton = if (state.openSettings) {
                { TextButton(onClick = onDismiss) { Text("Cancel") } }
            } else {
                null
            },
        )
    }
}

/**
 * Body of the confirm dialog: the editable text (or its feed preview), the live character count,
 * the hashtags that were added, and Trim post (PL-10 to PL-12): Trim with AI ([onTrimWithAi],
 * hidden when null) and Cut to fit.
 */
@Composable
fun ConfirmPostContent(
    state: PostState.Confirm,
    onTrimWithAi: (() -> Unit)? = null,
    onCutToFit: () -> Unit = {},
    onTextChange: (String) -> Unit,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                countLine(state),
                style = MaterialTheme.typography.bodySmall,
                color = countColor(state),
                modifier = Modifier.weight(1f).testTag("liveCount"),
            )
            if (state.text.isNotBlank()) {
                TextButton(onClick = { preview = !preview }, modifier = Modifier.testTag("previewToggle")) {
                    Text(if (preview) "Edit" else "Preview")
                }
            }
        }
        if (preview && state.text.isNotBlank()) {
            FeedPreview(state.text)
        } else {
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                readOnly = state.trimming,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .testTag("postText"),
            )
        }
        if (state.images.any(::isVideo)) {
            Text("1 video attached", style = MaterialTheme.typography.bodySmall)
        } else if (state.images.isNotEmpty()) {
            Text(
                if (state.images.size == 1) "1 image attached" else "${state.images.size} images attached",
                style = MaterialTheme.typography.bodySmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.images) { GeneratedImage(it, Modifier.size(64.dp), maxSidePx = 160) }
            }
        }
        if (state.addedTags.isNotEmpty()) {
            val tagChars = postLength(state.addedTags.joinToString(" "))
            Text(
                "Default hashtags added at the end: ${state.addedTags.joinToString(" ")} ($tagChars characters, counted above)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("addedTags"),
            )
        }
        if (state.overLimitBy > 0) {
            Text(
                "${state.overLimitBy} characters over LinkedIn's ${state.limit} limit. Trim or edit the text to post.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.text.isNotBlank()) TrimPostRow(state, onTrimWithAi, onCutToFit)
    }
}

/** "1234 / 3000 characters", with how many are left once it's close to the limit (improvement #12). */
internal fun countLine(state: PostState.Confirm): String {
    val left = state.limit - state.length
    return when {
        left < 0 -> "${state.length} / ${state.limit} characters"
        state.length >= state.limit * NEAR_LIMIT_SHARE -> "${state.length} / ${state.limit} characters · $left left"
        else -> "${state.length} / ${state.limit} characters"
    }
}

@Composable
private fun countColor(state: PostState.Confirm) = when {
    state.overLimitBy > 0 -> MaterialTheme.colorScheme.error
    state.length >= state.limit * NEAR_LIMIT_SHARE -> NearLimit
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val NearLimit = Color(0xFFEF8F00)

/**
 * The post as it will read on LinkedIn (improvement #12): plain text with the hashtags as links,
 * and a "…see more" marker where the feed folds it, so the opening lines can be checked.
 */
@Composable
fun FeedPreview(text: String) {
    val tagColor = MaterialTheme.colorScheme.primary
    val foldColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fold = feedFold(text)
    val styled = buildAnnotatedString {
        append(text)
        hashtagRanges(text).forEach { addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold), it.first, it.last + 1) }
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("feedPreview")) {
        Column(
            Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(styled.subSequence(0, fold.shown.length), style = MaterialTheme.typography.bodyMedium)
            if (fold.rest.isNotEmpty()) {
                Text(
                    "…see more (the feed shows the text above first)",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = foldColor,
                    modifier = Modifier.testTag("feedFold"),
                )
                Text(styled.subSequence(fold.shown.length, text.length), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Trim post: the AI condenses the text keeping its key points; Cut to fit drops the least important lines (PL-10, PL-12). */
@Composable
private fun TrimPostRow(state: PostState.Confirm, onTrimWithAi: (() -> Unit)?, onCutToFit: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onTrimWithAi != null) {
                TextButton(onClick = onTrimWithAi, enabled = !state.trimming) { Text("✂ Trim with AI") }
            }
            TextButton(onClick = onCutToFit, enabled = !state.trimming && state.overLimitBy > 0) { Text("Cut to fit") }
        }
        if (state.trimming) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Trimming, keeping the key points…", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.trimMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Character count under a generated post (PL-7): the length it will post at, default hashtags
 * included, against LinkedIn's [limit]. Over the limit it says by how much, as posting is blocked.
 */
@Composable
fun PostLengthNote(post: PostText, limit: Int, modifier: Modifier = Modifier) {
    val length = postLength(post.text)
    val tags = if (post.addedTags.isNotEmpty()) " with hashtags" else ""
    Text(
        if (post.fitsLimit) {
            "$length / $limit characters$tags"
        } else {
            "$length / $limit characters$tags: ${post.overLimitBy} over LinkedIn's limit. Use Trim post when you post."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (post.fitsLimit) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = modifier.testTag("postLength"),
    )
}
