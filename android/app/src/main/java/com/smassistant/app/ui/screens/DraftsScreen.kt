package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.drafts.Draft
import com.smassistant.app.drafts.DraftStore
import com.smassistant.app.generate.appSmartTrim
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.media.isVideo
import com.smassistant.app.scheduler.formatRunTime
import com.smassistant.app.ui.components.GeneratedImage
import com.smassistant.app.ui.components.PostDialogs
import com.smassistant.app.ui.components.SmScreen

/**
 * Drafts (improvement #8): posts saved from the confirm dialog with Save draft. Open edits one in the
 * same dialog, where it can be posted (which removes the draft) or saved again.
 */
@Composable
fun DraftsScreen(onSetUpLinkedIn: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val store = remember { DraftStore(appContext) }
    var drafts by remember { mutableStateOf(store.all()) }
    val postViewModel = viewModel { PostViewModel(appPostToLinkedIn(appContext), appSmartTrim(appContext)) }
    val postState by postViewModel.state.collectAsStateWithLifecycle()
    // Saved or posted from the dialog: the list changed.
    LaunchedEffect(postState) {
        if (postState is PostState.DraftSaved || postState is PostState.Posted) drafts = store.all()
    }

    DraftsContent(
        drafts = drafts,
        onOpen = postViewModel::openDraft,
        onDelete = { id ->
            store.delete(id)
            drafts = store.all()
        },
        onBackToHome = onBackToHome,
    )
    PostDialogs(
        state = postState,
        onTextChange = postViewModel::onTextChange,
        onTrimWithAi = postViewModel::trimWithAi,
        onCutToFit = postViewModel::cutToFit,
        onConfirm = postViewModel::confirm,
        onSaveDraft = postViewModel::saveDraft,
        onDismiss = postViewModel::dismiss,
        onOpenSettings = onSetUpLinkedIn,
    )
}

@Composable
fun DraftsContent(drafts: List<Draft>, onOpen: (Draft) -> Unit, onDelete: (String) -> Unit, onBackToHome: () -> Unit) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    SmScreen(sectionTitle = "Drafts", onBackToHome = onBackToHome) {
        if (drafts.isEmpty()) {
            Text(
                "No drafts yet. Tap Save draft in the Post to LinkedIn dialog to keep a post for later.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("noDrafts"),
            )
        }
        drafts.forEach { draft -> DraftCard(draft, onOpen = { onOpen(draft) }, onDelete = { deleting = draft.id }) }
    }
    drafts.firstOrNull { it.id == deleting }?.let { draft ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete draft?") },
            text = { Text("“${draft.title}” and its media will be removed from the phone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDelete(draft.id)
                }, modifier = Modifier.testTag("confirmDeleteDraft")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DraftCard(draft: Draft, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("draft-${draft.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(draft.title, fontWeight = FontWeight.SemiBold)
            Text(
                "Saved ${formatRunTime(draft.savedAt)} · ${draft.mediaLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.text.isNotBlank()) {
                Text(draft.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val images = draft.files.filterNot(::isVideo)
            if (images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(images) { GeneratedImage(it, Modifier.size(64.dp), maxSidePx = 160) }
                }
            }
            Row {
                TextButton(onClick = onOpen, modifier = Modifier.testTag("openDraft-${draft.id}")) { Text("Edit & post") }
                TextButton(onClick = onDelete, modifier = Modifier.testTag("deleteDraft-${draft.id}")) { Text("Delete") }
            }
        }
    }
}
