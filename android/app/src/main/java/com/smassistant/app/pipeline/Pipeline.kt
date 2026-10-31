package com.smassistant.app.pipeline

import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.prompt.PromptType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Where a stage of a pipeline is (docs/requirements/20, PS-2). */
enum class StageStatus { WAITING, RUNNING, DONE, FAILED, SKIPPED }

/**
 * One step from the prompt to LinkedIn: [label] is shown under its mark, [running] is the status
 * line while it runs (docs/design/pipeline-status.md).
 */
enum class StageId(val label: String, val running: String) {
    TOPIC("Topic", "Choosing the topic…"),
    PROMPT("Prompt", "Prompt submitted"),
    PREPARE_PROMPT("Prompt", "Preparing the prompt…"),
    WRITE_POST("Write post", "Writing the post…"),
    WRITE_CONTENT("Write content", "Writing the image content…"),
    DRAW_IMAGE("Draw image", "Drawing the image…"),
    MAKE_IMAGE("Make image", "Making the image…"),
    WRITE_SLIDES("Write slides", "Writing the slides…"),
    DRAW_SLIDES("Draw slides", "Drawing the slides…"),
    MAKE_VIDEO("Make video", "Making the video…"),
    CHECK_LENGTH("Check length", "Checking the length…"),
    POST("Post", "Posting to LinkedIn…"),
    HOLD("Hold for review", "Waiting for your review"),
    ;

    /** Stages that end a pipeline: everything before them is the generation. */
    val isFinal get() = this == POST || this == HOLD
}

/** [note] is the error of a failed stage, or a word about a finished one. */
data class Stage(val id: StageId, val status: StageStatus = StageStatus.WAITING, val note: String? = null) {
    /** What TalkBack reads for the stage (PS-9). */
    val description: String
        get() = "${id.label}: " + when (status) {
            StageStatus.WAITING -> "waiting"
            StageStatus.RUNNING -> "running"
            StageStatus.DONE -> "done"
            StageStatus.FAILED -> "failed" + (note?.let { ", $it" } ?: "")
            StageStatus.SKIPPED -> "skipped"
        }
}

/**
 * A row of stages from the prompt to LinkedIn (docs/requirements/20). Immutable: every change
 * returns a new pipeline. A change to a stage that isn't in the pipeline returns it unchanged, so
 * shared code can report stages that only some screens show.
 */
data class Pipeline(val stages: List<Stage>) {
    operator fun get(id: StageId): Stage? = stages.firstOrNull { it.id == id }
    operator fun contains(id: StageId) = stages.any { it.id == id }

    fun set(id: StageId, status: StageStatus, note: String? = null): Pipeline =
        if (id !in this) this else Pipeline(stages.map { if (it.id == id) Stage(id, status, note) else it })

    fun start(id: StageId) = set(id, StageStatus.RUNNING)
    fun done(id: StageId, note: String? = null) = set(id, StageStatus.DONE, note)
    fun fail(id: StageId, note: String?) = set(id, StageStatus.FAILED, note)
    fun skip(id: StageId) = set(id, StageStatus.SKIPPED)
    fun reset(id: StageId) = set(id, StageStatus.WAITING)

    val running get() = stages.filter { it.status == StageStatus.RUNNING }
    val failed get() = stages.firstOrNull { it.status == StageStatus.FAILED }
    val generation get() = stages.filterNot { it.id.isFinal }

    /** The generation finished: every generation stage that didn't fail or get skipped is done; the final stage waits. */
    fun finishGeneration() = Pipeline(stages.map {
        if (!it.id.isFinal && (it.status == StageStatus.WAITING || it.status == StageStatus.RUNNING)) it.copy(status = StageStatus.DONE) else it
    })

    /**
     * A generation stopped with [note]: the running generation stages fail, or the first waiting one when
     * none is running and none failed yet.
     */
    fun failGeneration(note: String): Pipeline {
        val running = generation.filter { it.status == StageStatus.RUNNING }.map { it.id }
        // A stage that already failed with its own error is the one to show.
        if (running.isEmpty() && generation.any { it.status == StageStatus.FAILED }) return this
        val targets = running.ifEmpty { listOfNotNull(generation.firstOrNull { it.status == StageStatus.WAITING }?.id) }
        return targets.fold(this) { p, id -> p.fail(id, note) }
    }

    /**
     * A run stopped with [note] (a scheduled run, docs/requirements/20 PS-7): its running stages fail, or
     * the first waiting one when none is running and none failed yet.
     */
    fun failCurrent(note: String): Pipeline {
        val running = running.map { it.id }
        if (running.isNotEmpty()) return running.fold(this) { p, id -> p.fail(id, note) }
        if (failed != null) return this
        return stages.firstOrNull { it.status == StageStatus.WAITING }?.let { fail(it.id, note) } ?: this
    }

    /** Running stages from an earlier process can't still be running: they fail with [note]. */
    fun stopped(note: String = STOPPED) = Pipeline(stages.map { if (it.status == StageStatus.RUNNING) it.copy(status = StageStatus.FAILED, note = note) else it })

    /** The words under the stages (PS-3): what runs now, else what failed, else where it ended. */
    val statusLine: String
        get() {
            failed?.let { stage -> return "${stage.id.label} failed" + (stage.note?.let { ": $it" } ?: "") }
            running.takeIf { it.isNotEmpty() }?.let { now -> return now.joinToString("  ·  ") { it.note ?: it.id.running } }
            val last = stages.last()
            val generated = generation.all { it.status == StageStatus.DONE || it.status == StageStatus.SKIPPED }
            return when {
                last.status == StageStatus.DONE -> last.note ?: if (last.id == StageId.HOLD) "Reviewed" else "Posted"
                generated && last.id == StageId.POST -> "Ready: tap Post in LinkedIn"
                generated -> last.id.running
                else -> "Waiting"
            }
        }

    fun toJson(): JsonElement = JsonArray(stages.map { stage ->
        buildJsonObject {
            put("id", stage.id.name)
            put("status", stage.status.name)
            stage.note?.let { put("note", it) }
        }
    })

    fun encode(): String = toJson().toString()

    companion object {
        const val STOPPED = "Stopped"

        fun of(vararg ids: StageId) = Pipeline(ids.map { Stage(it) })

        /** A saved pipeline, or null when it can't be read. Unknown stage ids are dropped. */
        fun fromJson(json: JsonElement?): Pipeline? = runCatching {
            val stages = (json as JsonArray).mapNotNull { element ->
                val o = element as JsonObject
                val id = StageId.entries.firstOrNull { it.name == o["id"]?.jsonPrimitive?.contentOrNull } ?: return@mapNotNull null
                val status = StageStatus.entries.firstOrNull { it.name == o["status"]?.jsonPrimitive?.contentOrNull } ?: StageStatus.WAITING
                Stage(id, status, o["note"]?.jsonPrimitive?.contentOrNull)
            }
            Pipeline(stages).takeIf { stages.isNotEmpty() }
        }.getOrNull()

        fun decode(text: String?): Pipeline? = text?.let { fromJson(runCatching { Json.parseToJsonElement(it) }.getOrNull()) }
    }
}

/** The stages of each screen and of the scheduler (docs/requirements/20, "Stages by screen"). */
object Pipelines {
    val text = Pipeline.of(StageId.PROMPT, StageId.WRITE_POST, StageId.POST)
    val image = Pipeline.of(StageId.PROMPT, StageId.WRITE_CONTENT, StageId.DRAW_IMAGE, StageId.POST)
    val video = Pipeline.of(StageId.PROMPT, StageId.WRITE_SLIDES, StageId.DRAW_SLIDES, StageId.MAKE_VIDEO, StageId.POST)
    val contentAndImage = Pipeline.of(StageId.PROMPT, StageId.WRITE_POST, StageId.WRITE_CONTENT, StageId.DRAW_IMAGE, StageId.POST)
    val contentAndVideo = Pipeline.of(
        StageId.PROMPT, StageId.WRITE_POST, StageId.WRITE_SLIDES, StageId.DRAW_SLIDES, StageId.MAKE_VIDEO, StageId.POST,
    )

    /** A scheduled run of [type] (PS-8): its text and media stages only, ending in Post or, with [reviewFirst], Hold for review. */
    fun scheduler(type: PromptType, reviewFirst: Boolean): Pipeline = Pipeline.of(
        *buildList {
            add(StageId.TOPIC)
            add(StageId.PREPARE_PROMPT)
            if (type.post) add(StageId.WRITE_POST)
            when (type.media) {
                MediaPurpose.IMAGE -> add(StageId.MAKE_IMAGE)
                MediaPurpose.VIDEO -> add(StageId.MAKE_VIDEO)
                null -> Unit
            }
            add(StageId.CHECK_LENGTH)
            add(if (reviewFirst) StageId.HOLD else StageId.POST)
        }.toTypedArray(),
    )
}
