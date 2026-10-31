package com.smassistant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smassistant.app.pipeline.Pipeline
import com.smassistant.app.pipeline.Stage
import com.smassistant.app.pipeline.StageStatus

private val MarkSize = 22.dp
private val LabelWidth = 64.dp

/**
 * Where a post is, from the prompt to LinkedIn (docs/requirements/20, PS-2, PS-3): a row of stage
 * marks joined by lines, each stage's label under it, and one line saying what happens now. The row
 * scrolls sideways when it doesn't fit (PS-10). Colours come from the theme (PS-9).
 */
@Composable
fun PipelineRow(pipeline: Pipeline, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("pipeline"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Top,
        ) {
            pipeline.stages.forEachIndexed { i, stage ->
                if (i > 0) {
                    val filled = pipeline.stages[i - 1].status == StageStatus.DONE
                    Box(
                        Modifier
                            .padding(top = MarkSize / 2 - 1.dp)
                            .width(16.dp)
                            .height(2.dp)
                            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                StageMark(stage)
            }
        }
        val failed = pipeline.failed != null
        Text(
            pipeline.statusLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .testTag("pipelineStatus")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun StageMark(stage: Stage) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = LabelWidth)
            .testTag("stage_${stage.id.name}")
            .clearAndSetSemantics { contentDescription = stage.description },
    ) {
        Box(Modifier.size(MarkSize), contentAlignment = Alignment.Center) {
            when (stage.status) {
                StageStatus.WAITING -> Box(Modifier.size(MarkSize).border(2.dp, colors.outline, CircleShape))
                StageStatus.SKIPPED -> Box(Modifier.size(MarkSize).border(2.dp, colors.outlineVariant, CircleShape), Alignment.Center) {
                    Glyph("–", colors.outlineVariant)
                }
                StageStatus.RUNNING -> CircularProgressIndicator(Modifier.size(MarkSize), color = colors.primary, strokeWidth = 2.5.dp)
                StageStatus.DONE -> Box(Modifier.size(MarkSize).background(colors.primary, CircleShape), Alignment.Center) {
                    Glyph("✓", colors.onPrimary)
                }
                StageStatus.FAILED -> Box(Modifier.size(MarkSize).background(colors.error, CircleShape), Alignment.Center) {
                    Glyph("✕", colors.onError)
                }
            }
        }
        Text(
            stage.id.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (stage.status == StageStatus.RUNNING) FontWeight.Bold else FontWeight.Normal,
            color = when (stage.status) {
                StageStatus.FAILED -> colors.error
                StageStatus.SKIPPED, StageStatus.WAITING -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Glyph(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
}
