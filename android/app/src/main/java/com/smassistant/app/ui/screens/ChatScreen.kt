package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.smassistant.app.chat.ChatItem
import com.smassistant.app.chat.ChatUiState
import com.smassistant.app.ui.components.ChatInputBar
import com.smassistant.app.ui.components.ErrorCard
import com.smassistant.app.ui.components.GeneratingRow
import com.smassistant.app.ui.components.SmScreen

/**
 * Chat-style screen shared by the hub features (06 "Shared screen layout"): scrolling
 * conversation and input bar with Submit; Back to Home is the app bar arrow. Header and input stay fixed (GEN-3).
 */
@Composable
fun <R> ChatScreen(
    title: String,
    state: ChatUiState<R>,
    placeholder: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: (ChatItem<R>) -> Unit,
    onCancel: () -> Unit,
    onSetUpAgent: () -> Unit,
    onBackToHome: () -> Unit,
    inputContent: @Composable (String) -> Unit,
    resultContent: @Composable (R) -> Unit,
    noAgentMessage: String = "No AI agent is set up yet. Add an API key for one of the agents in AI Agent settings.",
    header: @Composable () -> Unit = {},
    /** Shows Regenerate under each result, which runs its input again in its place (one image per request, SC-I1). */
    onRegenerate: ((ChatItem<R>) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }

    SmScreen(sectionTitle = title, onBackToHome = onBackToHome, scrollable = false) {
        header()
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (state.items.isEmpty()) {
                Text(
                    "Results will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chatList"),
            ) {
                items(state.items, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.Input -> inputContent(item.text)
                        is ChatItem.Loading -> GeneratingRow(onCancel)
                        is ChatItem.Failure -> ErrorCard(item.message, "Retry") { onRetry(item) }
                        is ChatItem.NoAgent -> ErrorCard(
                            noAgentMessage,
                            "Set up an AI agent",
                            onSetUpAgent,
                        )
                        is ChatItem.Result -> if (onRegenerate == null) {
                            resultContent(item.value)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                resultContent(item.value)
                                TextButton(onClick = { onRegenerate(item) }, enabled = !state.busy) { Text("Regenerate") }
                            }
                        }
                    }
                }
            }
        }
        ChatInputBar(
            value = state.input,
            onValueChange = onInputChange,
            onSubmit = onSubmit,
            enabled = !state.busy,
            placeholder = placeholder,
        )
    }
}
