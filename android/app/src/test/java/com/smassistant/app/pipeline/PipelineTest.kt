package com.smassistant.app.pipeline

import com.smassistant.app.prompt.PromptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PipelineTest {
    private fun statuses(p: Pipeline) = p.stages.map { it.status }

    @Test fun startsWaiting() {
        assertEquals(List(4) { StageStatus.WAITING }, statuses(Pipelines.image))
        assertEquals("Waiting", Pipelines.image.statusLine)
    }

    @Test fun stageIdNotInThePipelineIsIgnored() {
        assertSame(Pipelines.text, Pipelines.text.start(StageId.DRAW_IMAGE))
    }

    @Test fun runningStagesMakeTheStatusLine() {
        val p = Pipelines.contentAndImage.done(StageId.PROMPT).start(StageId.WRITE_POST).start(StageId.WRITE_CONTENT)
        assertEquals("Writing the post…  ·  Writing the image content…", p.statusLine)
    }

    @Test fun finishGenerationLeavesPostWaiting() {
        val p = Pipelines.image.done(StageId.PROMPT).start(StageId.DRAW_IMAGE).finishGeneration()
        assertEquals(listOf(StageStatus.DONE, StageStatus.DONE, StageStatus.DONE, StageStatus.WAITING), statuses(p))
        assertEquals("Ready: tap Post in LinkedIn", p.statusLine)
        assertEquals("Posted", p.done(StageId.POST).statusLine)
    }

    @Test fun finishGenerationKeepsAFailedHalf() {
        val p = Pipelines.contentAndImage.done(StageId.PROMPT).fail(StageId.WRITE_POST, "Rate limited").finishGeneration()
        assertEquals(StageStatus.FAILED, p[StageId.WRITE_POST]?.status)
        assertEquals(StageStatus.DONE, p[StageId.DRAW_IMAGE]?.status)
        assertEquals("Write post failed: Rate limited", p.statusLine)
    }

    @Test fun failGenerationFailsTheRunningStage() {
        val p = Pipelines.video.done(StageId.PROMPT).done(StageId.WRITE_SLIDES).start(StageId.DRAW_SLIDES).failGeneration("Cancelled")
        assertEquals(StageStatus.FAILED, p[StageId.DRAW_SLIDES]?.status)
        assertEquals(StageStatus.WAITING, p[StageId.MAKE_VIDEO]?.status)
        assertEquals("Draw slides failed: Cancelled", p.statusLine)
    }

    @Test fun failGenerationWithNothingRunningFailsTheFirstWaitingStage() {
        val p = Pipelines.text.done(StageId.PROMPT).failGeneration("No agent set up")
        assertEquals(StageStatus.FAILED, p[StageId.WRITE_POST]?.status)
    }

    @Test fun failGenerationKeepsAnEarlierFailure() {
        val failed = Pipelines.image.done(StageId.PROMPT).fail(StageId.WRITE_CONTENT, "Bad JSON")
        assertSame(failed, failed.failGeneration("other"))
    }

    @Test fun jsonRoundTripAndStopped() {
        val p = Pipelines.image.done(StageId.PROMPT).start(StageId.WRITE_CONTENT)
        assertEquals(p, Pipeline.decode(p.encode()))
        val restored = Pipeline.decode(p.encode())!!.stopped()
        assertEquals(StageStatus.FAILED, restored[StageId.WRITE_CONTENT]?.status)
        assertEquals("Stopped", restored[StageId.WRITE_CONTENT]?.note)
        assertNull(Pipeline.decode("not json"))
        assertNull(Pipeline.decode("[]"))
    }

    @Test fun schedulerStagesFollowThePostType() {
        assertEquals(
            listOf(StageId.TOPIC, StageId.PREPARE_PROMPT, StageId.WRITE_POST, StageId.CHECK_LENGTH, StageId.POST),
            Pipelines.scheduler(PromptType.TEXT, reviewFirst = false).stages.map { it.id },
        )
        assertEquals(
            listOf(StageId.TOPIC, StageId.PREPARE_PROMPT, StageId.MAKE_VIDEO, StageId.CHECK_LENGTH, StageId.HOLD),
            Pipelines.scheduler(PromptType.VIDEO, reviewFirst = true).stages.map { it.id },
        )
        assertEquals(
            listOf(StageId.TOPIC, StageId.PREPARE_PROMPT, StageId.WRITE_POST, StageId.MAKE_IMAGE, StageId.CHECK_LENGTH, StageId.POST),
            Pipelines.scheduler(PromptType.TEXT_AND_IMAGE, reviewFirst = false).stages.map { it.id },
        )
    }

    @Test fun holdDoneReadsReviewed() {
        val p = Pipelines.scheduler(PromptType.TEXT, reviewFirst = true).let { s -> s.stages.fold(s) { acc, st -> acc.done(st.id) } }
        assertEquals("Reviewed", p.statusLine)
        val waiting = p.start(StageId.HOLD)
        assertEquals("Waiting for your review", waiting.statusLine)
    }

    @Test fun descriptionsForTalkBack() {
        assertEquals("Draw image: failed, too small", Stage(StageId.DRAW_IMAGE, StageStatus.FAILED, "too small").description)
        assertEquals("Post: waiting", Stage(StageId.POST).description)
    }
}
