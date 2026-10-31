package com.smassistant.app.pipeline

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageReporterTest {
    @Test fun stageReportsRunningThenDone() = runBlocking {
        val seen = mutableListOf<Pair<StageId, StageStatus>>()
        val result = withContext(StageReporter { id, status, _ -> seen += id to status }) {
            stage(StageId.WRITE_POST) { "text" }
        }
        assertEquals("text", result)
        assertEquals(listOf(StageId.WRITE_POST to StageStatus.RUNNING, StageId.WRITE_POST to StageStatus.DONE), seen)
    }

    @Test fun aFailedResultFailsTheStage() = runBlocking {
        val tracking = StageReporter.Tracking(Pipelines.text) {}
        withContext(tracking.reporter) {
            stage(StageId.WRITE_POST, failed = { r: Int -> if (r < 0) "agent error" else null }) { -1 }
        }
        assertEquals(Stage(StageId.WRITE_POST, StageStatus.FAILED, "agent error"), tracking.pipeline[StageId.WRITE_POST])
    }

    @Test fun anExceptionFailsTheStage() = runBlocking {
        val tracking = StageReporter.Tracking(Pipelines.image) {}
        val thrown = runCatching { withContext(tracking.reporter) { stage(StageId.DRAW_IMAGE) { error("boom") } } }
        assertTrue(thrown.isFailure)
        assertEquals(StageStatus.FAILED, tracking.pipeline[StageId.DRAW_IMAGE]?.status)
        assertEquals("boom", tracking.pipeline[StageId.DRAW_IMAGE]?.note)
    }

    @Test fun withoutAReporterNothingHappens() = runBlocking {
        assertEquals(3, stage(StageId.POST) { 3 })
    }

    @Test fun trackingTellsOnlyRealChanges() = runBlocking {
        val changes = mutableListOf<Pipeline>()
        val tracking = StageReporter.Tracking(Pipelines.text) { changes += it }
        withContext(tracking.reporter) {
            reportStage(StageId.DRAW_IMAGE, StageStatus.RUNNING) // not in the text pipeline
            reportStage(StageId.WRITE_POST, StageStatus.RUNNING)
            reportStage(StageId.WRITE_POST, StageStatus.RUNNING)
        }
        assertEquals(1, changes.size)
    }
}
