package com.smassistant.app.linkedin

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.media.isDocument
import com.smassistant.app.media.isVideo
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.await
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A published post: its URN and the link that opens it. */
data class LinkedInPost(val urn: String) {
    val url get() = "https://www.linkedin.com/feed/update/$urn"
}

/**
 * LinkedIn Posts API (docs/tasks LP-1-I). The key is the member's OAuth access token, from
 * Sign in with LinkedIn or pasted on the LinkedIn settings screen (Q-03); it needs `w_member_social`
 * to post and `openid profile` for the member id. Settings are read at call time (LI-4-I).
 */
class LinkedInClient(
    private val config: () -> ApiConfig,
    private val client: OkHttpClient = Http.client,
    private val imageChecks: List<Long> = IMAGE_CHECKS_MILLIS,
    private val videoChecks: List<Long> = VIDEO_CHECKS_MILLIS,
    private val documentChecks: List<Long> = VIDEO_CHECKS_MILLIS,
) {
    private var cachedMember: Pair<String, String>? = null

    /**
     * Publishes [text] (plain text, not yet escaped) as a public post. [media] is uploaded first
     * and attached (LP-2-I): one image, one video (an `.mp4`, VI-I3) or one document (a carousel's
     * `.pdf`, SC-I6, titled with its file name) as `content.media`, two or more images as `content.multiImage`.
     */
    suspend fun createPost(text: String, media: List<File> = emptyList()): ApiResult<LinkedInPost> {
        val current = config()
        if (current.api.isBlank() || current.key.isBlank()) return ApiResult.Failed(ApiError.NotConfigured)
        val postsUrl = current.api.toHttpUrlOrNull() ?: return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
        val author = when (val member = memberUrn(postsUrl, current.key)) {
            is ApiResult.Failed -> return member
            is ApiResult.Ok -> member.value
        }
        val mediaUrns = media.map { file ->
            val uploaded = when {
                isVideo(file) -> uploadVideo(postsUrl, current.key, author, file)
                isDocument(file) -> uploadDocument(postsUrl, current.key, author, file)
                else -> uploadImage(postsUrl, current.key, author, file)
            }
            when (uploaded) {
                is ApiResult.Failed -> return uploaded
                is ApiResult.Ok -> uploaded.value
            }
        }
        val body = buildJsonObject {
            put("author", author)
            put("commentary", toLittleText(text))
            put("visibility", "PUBLIC")
            putJsonObject("distribution") {
                put("feedDistribution", "MAIN_FEED")
                putJsonArray("targetEntities") {}
                putJsonArray("thirdPartyDistributionChannels") {}
            }
            when (mediaUrns.size) {
                0 -> Unit
                1 -> putJsonObject("content") {
                    putJsonObject("media") {
                        put("id", mediaUrns.single())
                        // LinkedIn shows a document's title above its pages, and requires one.
                        media.single().takeIf(::isDocument)?.let { put("title", it.nameWithoutExtension) }
                    }
                }
                else -> putJsonObject("content") {
                    putJsonObject("multiImage") {
                        putJsonArray("images") { mediaUrns.forEach { urn -> addJsonObject { put("id", urn) } } }
                    }
                }
            }
            put("lifecycleState", "PUBLISHED")
            put("isReshareDisabledByAuthor", false)
        }
        val request = Request.Builder()
            .url(postsUrl)
            .restliHeaders(current.key)
            .post(body.toString().toRequestBody(JSON))
            .build()
        return call(request) { response, _ ->
            val urn = response.header("x-restli-id") ?: response.header("x-linkedin-id")
            if (urn.isNullOrBlank()) ApiResult.Failed(ApiError.BadResponse("no post id")) else ApiResult.Ok(LinkedInPost(urn))
        }
    }

    /** Images API: initializeUpload gives an upload URL and image URN, then the bytes are PUT there. */
    private suspend fun uploadImage(postsUrl: HttpUrl, token: String, owner: String, file: File): ApiResult<String> =
        uploadFile(postsUrl, token, owner, file, "images", "image", imageChecks)

    /** Documents API: the same steps as an image, under `/rest/documents` (up to 100 MB and 300 pages). */
    private suspend fun uploadDocument(postsUrl: HttpUrl, token: String, owner: String, file: File): ApiResult<String> =
        uploadFile(postsUrl, token, owner, file, "documents", "document", documentChecks)

    /** initializeUpload under `/rest/[resource]` gives an upload URL and the [field] URN, the bytes are PUT there, then it waits until LinkedIn has processed them. */
    private suspend fun uploadFile(
        postsUrl: HttpUrl,
        token: String,
        owner: String,
        file: File,
        resource: String,
        field: String,
        checks: List<Long>,
    ): ApiResult<String> {
        val initialize = Request.Builder()
            .url(postsUrl.resolve("/rest/$resource?action=initializeUpload")!!)
            .restliHeaders(token)
            .post(buildJsonObject { putJsonObject("initializeUploadRequest") { put("owner", owner) } }.toString().toRequestBody(JSON))
            .build()
        val target = call(initialize) { _, json ->
            val uploadUrl = json?.path("value", "uploadUrl")?.textOrNull?.toHttpUrlOrNull()
            val urn = json?.path("value", field)?.textOrNull
            if (uploadUrl == null || urn.isNullOrBlank()) {
                ApiResult.Failed(ApiError.BadResponse("no $field upload URL"))
            } else {
                ApiResult.Ok(uploadUrl to urn)
            }
        }
        val (uploadUrl, urn) = when (target) {
            is ApiResult.Failed -> return target
            is ApiResult.Ok -> target.value
        }
        val upload = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $token")
            .put(file.asRequestBody(OCTET_STREAM))
            .build()
        return when (val sent = call(upload) { _, _ -> ApiResult.Ok(urn) }) {
            is ApiResult.Failed -> sent
            is ApiResult.Ok -> awaitReady(postsUrl, token, resource, urn, checks)
        }
    }

    /**
     * Waits until LinkedIn has processed an uploaded image or video ([urn], under `/rest/[resource]`).
     * A post made while its media is still PROCESSING can go out with the text alone, so the post
     * waits for AVAILABLE, checking after each of [checks]. A token that may not read media (403)
     * can't check, so it goes ahead.
     */
    private suspend fun awaitReady(postsUrl: HttpUrl, token: String, resource: String, urn: String, checks: List<Long>): ApiResult<String> {
        val noun = resource.removeSuffix("s")
        val statusUrl = postsUrl.newBuilder()
            .encodedPath("/rest/$resource/")
            .addEncodedPathSegment(URLEncoder.encode(urn, "UTF-8"))
            .query(null)
            .build()
        for (wait in checks) {
            delay(wait)
            val request = Request.Builder().url(statusUrl).restliHeaders(token).get().build()
            val status = call(request) { _, json -> ApiResult.Ok(json?.path("status")?.textOrNull.orEmpty()) }
            when {
                status is ApiResult.Failed && (status.error == ApiError.NoAccess || status.error == ApiError.InvalidKey) -> return ApiResult.Ok(urn)
                status is ApiResult.Ok && status.value == "AVAILABLE" -> return ApiResult.Ok(urn)
                status is ApiResult.Ok && status.value == "PROCESSING_FAILED" ->
                    return ApiResult.Failed(ApiError.BadResponse("LinkedIn couldn't process the $noun"))
            }
        }
        return ApiResult.Failed(ApiError.BadResponse("LinkedIn is still processing the $noun; try posting again"))
    }

    /**
     * Videos API: initializeUpload with the file size gives the video URN and one upload URL per
     * byte range; each part is PUT there and its ETag kept; finalizeUpload with the ETags in order.
     */
    private suspend fun uploadVideo(postsUrl: HttpUrl, token: String, owner: String, file: File): ApiResult<String> {
        val initialize = Request.Builder()
            .url(postsUrl.resolve("/rest/videos?action=initializeUpload")!!)
            .restliHeaders(token)
            .post(
                buildJsonObject {
                    putJsonObject("initializeUploadRequest") {
                        put("owner", owner)
                        put("fileSizeBytes", file.length())
                        put("uploadCaptions", false)
                        put("uploadThumbnail", false)
                    }
                }.toString().toRequestBody(JSON),
            )
            .build()
        val plan = call(initialize) { _, json -> parseVideoUpload(json) }
        val upload = when (plan) {
            is ApiResult.Failed -> return plan
            is ApiResult.Ok -> plan.value
        }
        val bytes = file.readBytes()
        val etags = upload.parts.map { part ->
            val request = Request.Builder()
                .url(part.url)
                .header("Authorization", "Bearer $token")
                .put(bytes.copyOfRange(part.firstByte.toInt(), part.lastByte.toInt() + 1).toRequestBody(OCTET_STREAM))
                .build()
            val sent = call(request) { response, _ ->
                response.header("ETag")?.let { ApiResult.Ok(it) } ?: ApiResult.Failed(ApiError.BadResponse("no ETag for a video part"))
            }
            when (sent) {
                is ApiResult.Failed -> return sent
                is ApiResult.Ok -> sent.value
            }
        }
        val finalize = Request.Builder()
            .url(postsUrl.resolve("/rest/videos?action=finalizeUpload")!!)
            .restliHeaders(token)
            .post(
                buildJsonObject {
                    putJsonObject("finalizeUploadRequest") {
                        put("video", upload.video)
                        put("uploadToken", upload.token)
                        putJsonArray("uploadedPartIds") { etags.forEach { add(it) } }
                    }
                }.toString().toRequestBody(JSON),
            )
            .build()
        return when (val done = call(finalize) { _, _ -> ApiResult.Ok(upload.video) }) {
            is ApiResult.Failed -> done
            is ApiResult.Ok -> awaitReady(postsUrl, token, "videos", upload.video, videoChecks)
        }
    }

    private class VideoPart(val url: HttpUrl, val firstByte: Long, val lastByte: Long)
    private class VideoUpload(val video: String, val token: String, val parts: List<VideoPart>)

    private fun parseVideoUpload(json: JsonObject?): ApiResult<VideoUpload> {
        val value = json?.path("value") as? JsonObject
        val video = value?.path("video")?.textOrNull
        val token = value?.path("uploadToken")?.textOrNull.orEmpty()
        val parts = (value?.path("uploadInstructions") as? JsonArray)?.map { instruction ->
            VideoPart(
                url = instruction.path("uploadUrl")?.textOrNull?.toHttpUrlOrNull() ?: return ApiResult.Failed(ApiError.BadResponse("bad video upload URL")),
                firstByte = instruction.path("firstByte")?.textOrNull?.toLongOrNull() ?: return ApiResult.Failed(ApiError.BadResponse("bad video byte range")),
                lastByte = instruction.path("lastByte")?.textOrNull?.toLongOrNull() ?: return ApiResult.Failed(ApiError.BadResponse("bad video byte range")),
            )
        }
        return if (video.isNullOrBlank() || parts.isNullOrEmpty()) {
            ApiResult.Failed(ApiError.BadResponse("no video upload URL"))
        } else {
            ApiResult.Ok(VideoUpload(video, token, parts))
        }
    }

    /** `urn:li:person:{sub}` from OpenID userinfo on the same host, cached per token. */
    private suspend fun memberUrn(postsUrl: HttpUrl, token: String): ApiResult<String> {
        cachedMember?.let { (cachedToken, urn) -> if (cachedToken == token) return ApiResult.Ok(urn) }
        val request = Request.Builder()
            .url(postsUrl.resolve("/v2/userinfo")!!)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return call(request) { _, json ->
            val sub = json?.path("sub")?.textOrNull
            if (sub.isNullOrBlank()) {
                ApiResult.Failed(ApiError.BadResponse("no member id"))
            } else {
                ApiResult.Ok("urn:li:person:$sub").also { cachedMember = token to it.value }
            }
        }
    }

    private suspend fun <T> call(request: Request, onSuccess: (Response, JsonObject?) -> ApiResult<T>): ApiResult<T> = try {
        client.newCall(request).await().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                if (text.isBlank()) null else Json.parseToJsonElement(text) as? JsonObject
            } catch (e: SerializationException) {
                null
            }
            if (response.isSuccessful) {
                onSuccess(response, json)
            } else {
                ApiResult.Failed(ApiError.fromHttp(response.code, json?.path("message")?.textOrNull))
            }
        }
    } catch (e: IOException) {
        ApiResult.Failed(ApiError.fromException(e))
    }

    private fun Request.Builder.restliHeaders(token: String) = this
        .header("Authorization", "Bearer $token")
        .header("LinkedIn-Version", API_VERSION)
        .header("X-Restli-Protocol-Version", "2.0.0")

    companion object {
        /** LinkedIn's monthly API version (YYYYMM). Each is supported for about a year; bump when it nears sunset. */
        const val API_VERSION = "202606"
        private val JSON = "application/json".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        /** Waits before each image status check: about a minute in all. */
        val IMAGE_CHECKS_MILLIS = listOf(1_000L, 1_000L, 2_000L, 2_000L, 3_000L, 3_000L, 5_000L, 5_000L, 5_000L, 10_000L, 10_000L, 15_000L)

        /** Waits before each video status check: about three minutes in all, as videos take longer. */
        val VIDEO_CHECKS_MILLIS = listOf(2_000L, 3_000L, 5_000L, 5_000L) + List(17) { 10_000L }
    }
}
