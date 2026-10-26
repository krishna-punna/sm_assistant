package com.smassistant.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smassistant.app.ui.theme.LocalSmTheme

private val PromptCardColor = Color(0xFF3A3A3A)

/**
 * Dark card with a title, copy and expand actions, and the prompt text (GEN-2, PP-3 to PP-5).
 * Long text is cut to [collapsedLines]; Expand shows all of it full-screen.
 */
@Composable
fun PromptCard(title: String, text: String, modifier: Modifier = Modifier, collapsedLines: Int = 8) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = PromptCardColor, contentColor = Color.White),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy", color = Color.White) }
                TextButton(onClick = { expanded = true }) { Text("Expand", color = Color.White) }
            }
            Text(
                text,
                maxLines = collapsedLines,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = PromptCardColor, contentColor = Color.White) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                            Text("Copy", color = Color.White)
                        }
                        TextButton(onClick = { expanded = false }) { Text("Close", color = Color.White) }
                    }
                    SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                        Text(text, modifier = Modifier.testTag("expandedPrompt"))
                    }
                }
            }
        }
    }
}

/** The user's message, right-aligned (slides 5 to 11). */
@Composable
fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(start = 48.dp),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/** Light input with a green Submit, pinned above the footer; grows to six lines for pasted prompts (UI-4). */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    placeholder: String,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("chatInput"),
        )
        Spacer(Modifier.width(8.dp))
        PrimaryButton(
            "Submit",
            onClick = onSubmit,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.width(110.dp),
        )
    }
}

/** Post in LinkedIn button under a result (07), in the theme's menu colour and shape. */
@Composable
fun PostInLinkedInButton(onClick: () -> Unit, enabled: Boolean = true, busy: Boolean = false) {
    val colors = LocalSmTheme.current.menu
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        colors = ButtonDefaults.buttonColors(containerColor = colors.container, contentColor = colors.content),
        shape = LocalSmTheme.current.buttonShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = colors.content, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Posting…")
        } else {
            Text("Post in LinkedIn")
        }
    }
}

/** Spinner shown while a generation runs, with Cancel when [onCancel] is given (GEN-4). */
@Composable
fun GeneratingRow(onCancel: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(
            "Generating…",
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

/** Error card with Retry (GEN-4). */
@Composable
fun ErrorCard(message: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Announced when it appears, so TalkBack users hear why the generation stopped.
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            SmButton(actionLabel, LocalSmTheme.current.action, onAction)
        }
    }
}
