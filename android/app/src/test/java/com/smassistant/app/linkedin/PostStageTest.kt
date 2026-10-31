package com.smassistant.app.linkedin

import com.smassistant.app.pipeline.StageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Post stage follows the Post in LinkedIn flow (docs/requirements/20, PS-5). */
class PostStageTest {
    private val confirm = PostState.Confirm("text", emptyList())

    @Test fun openingTheDialogRunsThePostStage() {
        assertEquals(StageStatus.RUNNING to REVIEW_NOTE, postStage(PostState.Idle, confirm))
        assertNull(postStage(confirm, confirm.copy(text = "edited")))
    }

    @Test fun postingThenPosted() {
        assertEquals(StageStatus.RUNNING to null, postStage(confirm, PostState.Posting))
        assertEquals(StageStatus.DONE to "Posted to LinkedIn", postStage(PostState.Posting, PostState.Posted(LinkedInPost("urn:li:share:1"))))
        assertEquals(StageStatus.FAILED to "No network", postStage(PostState.Posting, PostState.Failed("No network", openSettings = false)))
    }

    @Test fun closingTheDialogPutsItBackToWaiting() {
        assertEquals(StageStatus.WAITING to null, postStage(confirm, PostState.Idle))
        assertEquals(StageStatus.WAITING to null, postStage(confirm, PostState.DraftSaved))
        assertNull(postStage(PostState.Failed("x", false), PostState.Idle))
    }
}
