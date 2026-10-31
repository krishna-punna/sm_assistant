package com.smassistant.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smassistant.app.history.PostHistoryStore
import com.smassistant.app.history.PublishedPost
import com.smassistant.app.scheduler.formatRunTime
import com.smassistant.app.ui.components.SmScreen

/** Post History (improvement #7): every post the app published, newest first, manual and scheduled. */
@Composable
fun PostHistoryScreen(onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val store = remember { PostHistoryStore(appContext) }
    var posts by remember { mutableStateOf(store.all()) }
    PostHistoryContent(
        posts = posts,
        onDelete = { id ->
            store.delete(id)
            posts = store.all()
        },
        onClear = {
            store.clear()
            posts = emptyList()
        },
        onBackToHome = onBackToHome,
    )
}

@Composable
fun PostHistoryContent(posts: List<PublishedPost>, onDelete: (String) -> Unit, onClear: () -> Unit, onBackToHome: () -> Unit) {
    var clearing by rememberSaveable { mutableStateOf(false) }
    SmScreen(sectionTitle = "Post History", onBackToHome = onBackToHome) {
        if (posts.isEmpty()) {
            Text(
                "Nothing posted yet. Posts you publish, and the ones your schedulers publish, show up here.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("noHistory"),
            )
        } else {
            Text(
                "${posts.size} posts on this phone, newest first.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("historyCount"),
            )
            posts.forEach { post -> HistoryCard(post, onDelete = { onDelete(post.id) }) }
            TextButton(onClick = { clearing = true }, modifier = Modifier.testTag("clearHistory")) { Text("Clear history") }
        }
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("Clear post history?") },
            text = { Text("This only clears the list on this phone. Your posts stay on LinkedIn.") },
            confirmButton = {
                TextButton(onClick = {
                    clearing = false
                    onClear()
                }, modifier = Modifier.testTag("confirmClear")) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } },
        )
    }
}

/** One post: title, when and how it was made, the text (tap to expand), Open, Copy and Remove. */
@Composable
private fun HistoryCard(post: PublishedPost, onDelete: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("history-${post.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(post.title, fontWeight = FontWeight.SemiBold)
            Text(
                "${formatRunTime(post.postedAt)} · ${post.source.label} · ${post.mediaLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                post.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClickLabel = if (expanded) "Show less" else "Show all") { expanded = !expanded }
                    .testTag("historyText-${post.id}"),
            )
            Row {
                TextButton(onClick = { runCatching { uriHandler.openUri(post.url) } }) { Text("Open post") }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(post.text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy text") }
                TextButton(onClick = onDelete, modifier = Modifier.testTag("removeHistory-${post.id}")) { Text("Remove") }
            }
        }
    }
}
