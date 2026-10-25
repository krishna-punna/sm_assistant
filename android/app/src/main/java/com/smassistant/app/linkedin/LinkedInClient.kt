package com.smassistant.app.linkedin

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.media.isVideo
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.await
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import java.io.File
import java.io.IOException
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

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
) {
    private var cachedMember: Pair<String, String>? = null

    /**
     * Publishes [text] (plain text, not yet escaped) as a public post. [media] is uploaded first
     * and attached (LP-2-I): one image or one video (an `.mp4`, VI-I3) as `content.media`, two or
     * more images as `content.multiImage`.
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
            val uploaded = if (isVideo(file)) {
                uploadVideo(postsUrl, current.key, author, file)
            } else {
                uploadImage(postsUrl, current.key, author, file)
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
                1 -> putJsonObject("content") { putJsonObject("media") { put("id", mediaUrns.single()) } }
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
    private suspend fun uploadImage(postsUrl: HttpUrl, token: String, owner: String, file: File): ApiResult<String> {
        val initialize = Request.Builder()
            .url(postsUrl.resolve("/rest/images?action=initializeUpload")!!)
            .restliHeaders(token)
            .post(buildJsonObject { putJsonObject("initializeUploadRequest") { put("owner", owner) } }.toString().toRequestBody(JSON))
            .build()
        val target = call(initialize) { _, json ->
            val uploadUrl = json?.path("value", "uploadUrl")?.textOrNull?.toHttpUrlOrNull()
            val image = json?.path("value", "image")?.textOrNull
            if (uploadUrl == null || image.isNullOrBlank()) {
                ApiResult.Failed(ApiError.BadResponse("no image upload URL"))
            } else {
                ApiResult.Ok(uploadUrl to image)
            }
        }
        val (uploadUrl, image) = when (target) {
            is ApiResult.Failed -> return target
            is ApiResult.Ok -> target.value
        }
        val upload = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $token")
            .put(file.asRequestBody(OCTET_STREAM))
            .build()
        return call(upload) { _, _ -> ApiResult.Ok(image) }
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
        return call(finalize) { _, _ -> ApiResult.Ok(upload.video) }
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
    }
}
