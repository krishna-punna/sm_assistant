package com.smassistant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.smassistant.app.hashtags.LINKEDIN_POST_LIMIT
import com.smassistant.app.linkedin.PostState

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
) {
    when (state) {
        PostState.Idle, PostState.Posting -> Unit
        is PostState.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Post to LinkedIn?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .testTag("postText"),
                    )
                    if (state.images.isNotEmpty()) {
                        Text(
                            if (state.images.size == 1) "1 image attached" else "${state.images.size} images attached",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.images) { GeneratedImage(it, Modifier.size(64.dp), maxSidePx = 160) }
                        }
                    }
                    if (state.addedTags.isNotEmpty()) {
                        Text("Default hashtags added: ${state.addedTags.joinToString(" ")}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (state.overLimitBy > 0) {
                            "${state.overLimitBy} characters over LinkedIn's $LINKEDIN_POST_LIMIT limit. Shorten the text to post."
                        } else {
                            "${state.text.length} / $LINKEDIN_POST_LIMIT characters"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.overLimitBy > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onConfirm, enabled = state.canPost) { Text("Post") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
