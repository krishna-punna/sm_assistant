package com.smassistant.app.ui.screens

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.smassistant.app.pipeline.Pipelines
import com.smassistant.app.pipeline.StageId
import com.smassistant.app.ui.components.PipelineRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PipelineRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsEachStageWithItsStatusAndTheStatusLine() {
        val pipeline = Pipelines.image.done(StageId.PROMPT).done(StageId.WRITE_CONTENT).start(StageId.DRAW_IMAGE)
        compose.setContent { PipelineRow(pipeline) }

        compose.onNodeWithTag("stage_PROMPT").assertContentDescriptionEquals("Prompt: done")
        compose.onNodeWithTag("stage_DRAW_IMAGE").assertContentDescriptionEquals("Draw image: running")
        compose.onNodeWithTag("stage_POST").assertContentDescriptionEquals("Post: waiting")
        compose.onNodeWithTag("pipelineStatus").assertTextEquals("Drawing the image…")
    }

    @Test
    fun aFailedStageShowsItsError() {
        val pipeline = Pipelines.text.done(StageId.PROMPT).fail(StageId.WRITE_POST, "Gemini: rate limited")
        compose.setContent { PipelineRow(pipeline) }

        compose.onNodeWithTag("stage_WRITE_POST").assertContentDescriptionEquals("Write post: failed, Gemini: rate limited")
        compose.onNodeWithTag("pipelineStatus").assertTextEquals("Write post failed: Gemini: rate limited")
    }
}
