package com.smassistant.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.postStage
import com.smassistant.app.pipeline.StageId
import com.smassistant.app.pipeline.StageStatus

/** The id of the input whose result is being drawn, given by ChatScreen to its result content. */
val LocalChatInputId = compositionLocalOf<Long?> { null }

/**
 * Moves the Post stage of the result [key] was posted from as the Post in LinkedIn flow goes
 * (docs/requirements/20, PS-5). [mark] is the screen's ChatViewModel.markStage.
 */
@Composable
fun TrackPostStage(state: PostState, key: Long?, mark: (Long, StageId, StageStatus, String?) -> Unit) {
    val previous = remember { mutableStateOf<PostState>(PostState.Idle) }
    LaunchedEffect(state) {
        val before = previous.value
        previous.value = state
        val target = key ?: return@LaunchedEffect
        postStage(before, state)?.let { (status, note) -> mark(target, StageId.POST, status, note) }
    }
}
