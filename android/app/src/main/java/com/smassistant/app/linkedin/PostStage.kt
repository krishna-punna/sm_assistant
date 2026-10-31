package com.smassistant.app.linkedin

import com.smassistant.app.pipeline.StageStatus

/**
 * The Post stage of a result's pipeline as the Post in LinkedIn flow moves from [before] to [now]
 * (docs/requirements/20, PS-5), or null when it doesn't change: running while the confirm dialog is
 * open or the post is being sent, done once posted, failed with LinkedIn's message, and back to
 * waiting when the dialog is closed without posting (or the post is kept as a draft).
 */
fun postStage(before: PostState, now: PostState): Pair<StageStatus, String?>? = when (now) {
    is PostState.Confirm -> if (before is PostState.Confirm) null else StageStatus.RUNNING to REVIEW_NOTE
    PostState.Posting -> StageStatus.RUNNING to null
    is PostState.Posted -> StageStatus.DONE to "Posted to LinkedIn"
    is PostState.Failed -> StageStatus.FAILED to now.message
    PostState.Idle, PostState.DraftSaved -> if (before is PostState.Confirm) StageStatus.WAITING to null else null
}

const val REVIEW_NOTE = "Check the post and tap Post"
