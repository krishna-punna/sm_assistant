package com.smassistant.app.scheduler

import com.smassistant.app.data.ImageSize
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.prompt.PromptType
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * What a scheduler posts (SC-5), the same choices as Prepare Prompt's Prompt Type
 * (docs/requirements/14). Image and Video post the media with the title and hashtags as the
 * caption. The names of the combined types are kept from older saves.
 */
enum class PostType(val label: String, val promptType: PromptType) {
    TEXT("Text", PromptType.TEXT),
    IMAGE("Image", PromptType.IMAGE),
    VIDEO("Video", PromptType.VIDEO),
    TEXT_WITH_IMAGE("Text & Image", PromptType.TEXT_AND_IMAGE),
    TEXT_WITH_VIDEO("Text & Video", PromptType.TEXT_AND_VIDEO),
    ;

    /** True when the post has images, so the image template and format apply. */
    val hasImages get() = promptType.media == MediaPurpose.IMAGE
}

/**
 * How the last run went, shown on the scheduler list. [postUrl] opens the post when it was published;
 * [awaitingReview] is true when the run made a post that waits for the user's approval; [template]
 * is the id of the image template its first image followed; [seriesParts] counts the posters when
 * its images were one poster series (AC-7).
 */
data class RunRecord(
    val at: Long,
    val ok: Boolean,
    val message: String,
    val postUrl: String? = null,
    val awaitingReview: Boolean = false,
    val template: String? = null,
    val seriesParts: Int? = null,
)

/**
 * A post a "review before posting" scheduler made and holds until the user approves it: the final
 * [text] (hashtags included), the paths of its generated [media] in the app's cache and the image
 * [template] they followed.
 */
data class PendingPost(
    val title: String,
    val text: String,
    val media: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val template: String? = null,
)

/**
 * One scheduler (docs/requirements/13-scheduled-posts.md): on its [schedule] it picks the next of
 * its [topics], turns it into a prompt, generates a [postType] post and publishes it. The user can
 * have several, each on or off (SC-8).
 */
data class AutoPostScheduler(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val topics: List<String> = emptyList(),
    val postType: PostType = PostType.TEXT,
    val schedule: Schedule = Schedule(),
    /** Index into [topics] of the next run's topic, so the topics take turns (SC-2). */
    val nextTopic: Int = 0,
    /** Titles of the latest posts, newest first, so new prompts can avoid repeating them. */
    val recentTitles: List<String> = emptyList(),
    val lastRun: RunRecord? = null,
    /** When on, a run makes the post and waits for the user to approve it instead of publishing it. */
    val reviewFirst: Boolean = false,
    /** The post waiting for review; while there is one, later runs are skipped. */
    val pending: PendingPost? = null,
    /** Epoch millis of the next planned run while [enabled]; null when nothing is planned. */
    val nextRunAt: Long? = null,
    /** The image template id every image post of this scheduler follows; null is Auto (the AI agent picks). */
    val imageTemplate: String? = null,
    /** The image format every image post of this scheduler uses; null is Auto. */
    val imageFormat: ImageSize? = null,
) {
    val displayName get() = name.ifBlank { topics.take(2).joinToString(", ").ifBlank { "Scheduler" } }

    /** The topic for the next run, or null when none are chosen. */
    fun currentTopic(): String? = topics.takeIf { it.isNotEmpty() }?.let { it[nextTopic.mod(it.size)] }

    /** After a run on [currentTopic]: the next topic's turn, with [title] remembered when a post was made. */
    fun advanced(title: String?): AutoPostScheduler = copy(
        nextTopic = if (topics.isEmpty()) 0 else (nextTopic + 1).mod(topics.size),
        recentTitles = if (title.isNullOrBlank()) recentTitles else (listOf(title) + recentTitles).take(MAX_RECENT_TITLES),
    )

    /** Saved settings are usable: at least one topic (SC-3), and weekly needs a day (SC-4). */
    fun problem(): String? = when {
        topics.isEmpty() -> "Choose at least one technical content type."
        schedule.frequency == Frequency.WEEKLY && schedule.days.isEmpty() -> "Choose at least one day of the week."
        else -> null
    }

    companion object {
        const val MAX_RECENT_TITLES = 10
    }
}

/** JSON for [SchedulerStore]. Unknown or missing values fall back to the defaults, so older saves still load. */
object SchedulerJson {
    fun encode(list: List<AutoPostScheduler>): String = buildJsonArray { list.forEach { add(toJson(it)) } }.toString()

    fun decode(text: String?): List<AutoPostScheduler> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { runCatching { fromJson(it.jsonObject) }.getOrNull() }
    }

    fun toJson(s: AutoPostScheduler): JsonObject = buildJsonObject {
        put("id", s.id)
        put("name", s.name)
        put("enabled", s.enabled)
        put("topics", strings(s.topics))
        put("postType", s.postType.name)
        put("schedule", buildJsonObject {
            val r = s.schedule
            put("frequency", r.frequency.name)
            put("time", r.time.toString())
            put("everyHours", r.everyHours)
            put("windowStart", r.windowStart.toString())
            put("windowEnd", r.windowEnd.toString())
            put("days", strings(r.days.sorted().map { it.name }))
            put("dayOfMonth", r.dayOfMonth)
            put("weekdaysOnly", r.weekdaysOnly)
        })
        put("nextTopic", s.nextTopic)
        put("recentTitles", strings(s.recentTitles))
        s.lastRun?.let { run ->
            put("lastRun", buildJsonObject {
                put("at", run.at)
                put("ok", run.ok)
                put("message", run.message)
                run.postUrl?.let { put("postUrl", it) }
                if (run.awaitingReview) put("awaitingReview", true)
                run.template?.let { put("template", it) }
                run.seriesParts?.let { put("seriesParts", it) }
            })
        }
        put("reviewFirst", s.reviewFirst)
        s.pending?.let { post ->
            put("pending", buildJsonObject {
                put("title", post.title)
                put("text", post.text)
                put("media", strings(post.media))
                put("createdAt", post.createdAt)
                post.template?.let { put("template", it) }
            })
        }
        s.nextRunAt?.let { put("nextRunAt", it) }
        s.imageTemplate?.let { put("imageTemplate", it) }
        s.imageFormat?.let { put("imageFormat", it.key) }
    }

    fun fromJson(json: JsonObject): AutoPostScheduler {
        val defaults = AutoPostScheduler(id = "")
        val rule = json["schedule"] as? JsonObject
        val d = Schedule()
        return AutoPostScheduler(
            id = json.string("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = json.string("name").orEmpty(),
            enabled = json.bool("enabled") ?: defaults.enabled,
            topics = json.strings("topics"),
            postType = enumOr(json.string("postType"), defaults.postType),
            schedule = if (rule == null) d else Schedule(
                frequency = enumOr(rule.string("frequency"), d.frequency),
                time = rule.time("time") ?: d.time,
                everyHours = rule.int("everyHours") ?: d.everyHours,
                windowStart = rule.time("windowStart") ?: d.windowStart,
                windowEnd = rule.time("windowEnd") ?: d.windowEnd,
                days = rule.strings("days").mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }.toSet(),
                dayOfMonth = rule.int("dayOfMonth") ?: d.dayOfMonth,
                weekdaysOnly = rule.bool("weekdaysOnly") ?: d.weekdaysOnly,
            ),
            nextTopic = json.int("nextTopic") ?: 0,
            recentTitles = json.strings("recentTitles"),
            lastRun = (json["lastRun"] as? JsonObject)?.let { run ->
                RunRecord(
                    at = run.long("at") ?: 0L,
                    ok = run.bool("ok") ?: false,
                    message = run.string("message").orEmpty(),
                    postUrl = run.string("postUrl"),
                    awaitingReview = run.bool("awaitingReview") ?: false,
                    template = run.string("template"),
                    seriesParts = (run.long("seriesParts") ?: run.long("carouselPages"))?.toInt(),
                )
            },
            reviewFirst = json.bool("reviewFirst") ?: defaults.reviewFirst,
            pending = (json["pending"] as? JsonObject)?.let { post ->
                PendingPost(
                    title = post.string("title").orEmpty(),
                    text = post.string("text").orEmpty(),
                    media = post.strings("media"),
                    createdAt = post.long("createdAt") ?: 0L,
                    template = post.string("template"),
                )
            },
            nextRunAt = json.long("nextRunAt"),
            imageTemplate = json.string("imageTemplate")?.takeIf { it.isNotBlank() },
            imageFormat = ImageSize.of(json.string("imageFormat")),
        )
    }

    private fun strings(values: Collection<String>) = JsonArray(values.map { JsonPrimitive(it) })

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private fun JsonObject.primitive(key: String) = (this[key] as? JsonPrimitive)
    private fun JsonObject.string(key: String) = primitive(key)?.contentOrNull
    private fun JsonObject.int(key: String) = primitive(key)?.intOrNull
    private fun JsonObject.long(key: String) = primitive(key)?.longOrNull
    private fun JsonObject.bool(key: String) = primitive(key)?.booleanOrNull
    private fun JsonObject.time(key: String) = string(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    private fun JsonObject.strings(key: String) =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
}
