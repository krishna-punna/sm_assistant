package com.smassistant.app.linkedin

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LinkedInClientTest {
    private val server = MockWebServer()
    private var key = "token-1"
    private lateinit var linkedIn: LinkedInClient

    @Before fun start() {
        server.start()
        linkedIn = LinkedInClient({ ApiConfig(server.url("/rest/posts").toString(), key) }, Http.build(timeoutSeconds = 2), imageChecks = listOf(0L, 0L, 0L), videoChecks = listOf(0L, 0L, 0L))
    }

    @After fun stop() = server.shutdown()

    private fun userinfo() = server.enqueue(MockResponse().setBody("""{"sub":"abc123","name":"Punna Krishna"}"""))
    private fun created(urn: String = "urn:li:share:7400") =
        server.enqueue(MockResponse().setResponseCode(201).setHeader("x-restli-id", urn))

    private fun post(text: String) = runBlocking { linkedIn.createPost(text) }

    @Test
    fun `creates a text post as the member with restli headers`() {
        userinfo()
        created()

        val result = post("Hello (world) #java")

        assertEquals(ApiResult.Ok(LinkedInPost("urn:li:share:7400")), result)
        assertEquals("https://www.linkedin.com/feed/update/urn:li:share:7400", (result as ApiResult.Ok).value.url)

        val me = server.takeRequest()
        assertEquals("GET", me.method)
        assertEquals("/v2/userinfo", me.path)
        assertEquals("Bearer token-1", me.getHeader("Authorization"))

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/rest/posts", create.path)
        assertEquals("Bearer token-1", create.getHeader("Authorization"))
        assertEquals(LinkedInClient.API_VERSION, create.getHeader("LinkedIn-Version"))
        assertEquals("2.0.0", create.getHeader("X-Restli-Protocol-Version"))
        val body = Json.parseToJsonElement(create.body.readUtf8()) as JsonObject
        assertEquals("urn:li:person:abc123", body.path("author")?.textOrNull)
        assertEquals("Hello \\(world\\) {hashtag|\\#|java}", body.path("commentary")?.textOrNull)
        assertEquals("PUBLIC", body.path("visibility")?.textOrNull)
        assertEquals("MAIN_FEED", body.path("distribution", "feedDistribution")?.textOrNull)
        assertEquals("PUBLISHED", body.path("lifecycleState")?.textOrNull)
    }

    @Test
    fun `member id is fetched once per token`() {
        userinfo()
        created("urn:li:share:1")
        created("urn:li:share:2")
        post("one")
        post("two")
        assertEquals(3, server.requestCount)

        key = "token-2"
        userinfo()
        created("urn:li:share:3")
        post("three")
        assertEquals("/v2/userinfo", server.run { takeRequest(); takeRequest(); takeRequest(); takeRequest() }.path)
    }

    @Test
    fun `missing token sends nothing`() {
        key = ""
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), post("hi"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `errors map to ApiError with LinkedIn's message`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Expired token","status":401}"""))
        assertEquals(ApiResult.Failed(ApiError.InvalidKey), post("hi"))

        userinfo()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Not enough permissions","status":403}"""))
        assertEquals(ApiResult.Failed(ApiError.NoAccess), post("hi"))

        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"commentary too long","status":422}"""))
        assertEquals(ApiResult.Failed(ApiError.Server(422, "commentary too long")), post("hi"))

        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(ApiResult.Failed(ApiError.RateLimited), post("hi"))
    }

    @Test
    fun `a created post without an id is a bad response`() {
        userinfo()
        server.enqueue(MockResponse().setResponseCode(201))
        assertEquals(ApiResult.Failed(ApiError.BadResponse("no post id")), post("hi"))
    }

    private fun initUpload(image: String) = server.enqueue(
        MockResponse().setBody("""{"value":{"uploadUrl":"${server.url("/upload/$image")}","image":"urn:li:image:$image"}}"""),
    )

    private fun mediaStatus(status: String) = server.enqueue(MockResponse().setBody("""{"status":"$status"}"""))

    @Test
    fun `uploads images then attaches one as media and several as multiImage`() {
        val dir = Files.createTempDirectory("post").toFile()
        val first = File(dir, "a.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val second = File(dir, "b.png").apply { writeBytes(byteArrayOf(4)) }

        userinfo()
        initUpload("A")
        server.enqueue(MockResponse().setResponseCode(201))
        mediaStatus("AVAILABLE")
        created()
        assertEquals(ApiResult.Ok(LinkedInPost("urn:li:share:7400")), runBlocking { linkedIn.createPost("One", listOf(first)) })

        server.takeRequest() // userinfo
        val init = server.takeRequest()
        assertEquals("/rest/images?action=initializeUpload", init.path)
        assertEquals(LinkedInClient.API_VERSION, init.getHeader("LinkedIn-Version"))
        val initBody = Json.parseToJsonElement(init.body.readUtf8()) as JsonObject
        assertEquals("urn:li:person:abc123", initBody.path("initializeUploadRequest", "owner")?.textOrNull)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/upload/A", put.path)
        assertEquals("Bearer token-1", put.getHeader("Authorization"))
        assertEquals(listOf<Byte>(1, 2, 3), put.body.readByteArray().toList())
        val status = server.takeRequest()
        assertEquals("GET", status.method)
        assertEquals("/rest/images/urn%3Ali%3Aimage%3AA", status.path)
        val single = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("urn:li:image:A", single.path("content", "media", "id")?.textOrNull)

        initUpload("B")
        server.enqueue(MockResponse().setResponseCode(201))
        mediaStatus("AVAILABLE")
        initUpload("C")
        server.enqueue(MockResponse().setResponseCode(201))
        mediaStatus("AVAILABLE")
        created()
        runBlocking { linkedIn.createPost("", listOf(first, second)) }
        repeat(6) { server.takeRequest() }
        val multi = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("urn:li:image:B", multi.path("content", "multiImage", "images", 0, "id")?.textOrNull)
        assertEquals("urn:li:image:C", multi.path("content", "multiImage", "images", 1, "id")?.textOrNull)
    }

    @Test
    fun `uploads a carousel PDF as a document and attaches it with its title`() {
        linkedIn = LinkedInClient({ ApiConfig(server.url("/rest/posts").toString(), key) }, Http.build(timeoutSeconds = 2), documentChecks = listOf(0L, 0L))
        val pdf = File(Files.createTempDirectory("post").toFile(), "Caching in 5 steps.pdf").apply { writeBytes(byteArrayOf(9, 8)) }

        userinfo()
        server.enqueue(MockResponse().setBody("""{"value":{"uploadUrl":"${server.url("/upload/D")}","document":"urn:li:document:D"}}"""))
        server.enqueue(MockResponse().setResponseCode(201))
        mediaStatus("PROCESSING")
        mediaStatus("AVAILABLE")
        created()
        assertEquals(ApiResult.Ok(LinkedInPost("urn:li:share:7400")), runBlocking { linkedIn.createPost("Caching", listOf(pdf)) })

        server.takeRequest() // userinfo
        assertEquals("/rest/documents?action=initializeUpload", server.takeRequest().path)
        val put = server.takeRequest()
        assertEquals("/upload/D", put.path)
        assertEquals(listOf<Byte>(9, 8), put.body.readByteArray().toList())
        assertEquals("/rest/documents/urn%3Ali%3Adocument%3AD", server.takeRequest().path)
        server.takeRequest()
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("urn:li:document:D", body.path("content", "media", "id")?.textOrNull)
        assertEquals("Caching in 5 steps", body.path("content", "media", "title")?.textOrNull)
    }

    @Test
    fun `waits for LinkedIn to finish processing an image before posting`() {
        val file = Files.createTempFile("img", ".png").toFile().apply { writeBytes(byteArrayOf(1)) }
        userinfo()
        initUpload("A")
        server.enqueue(MockResponse().setResponseCode(201))
        mediaStatus("WAITING_UPLOAD")
        mediaStatus("PROCESSING")
        mediaStatus("AVAILABLE")
        created()
        assertEquals(ApiResult.Ok(LinkedInPost("urn:li:share:7400")), runBlocking { linkedIn.createPost("Post", listOf(file)) })

        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(
            listOf("/v2/userinfo", "/rest/images?action=initializeUpload", "/upload/A") +
                List(3) { "/rest/images/urn%3Ali%3Aimage%3AA" } + "/rest/posts",
            paths,
        )
    }

    @Test
    fun `an image LinkedIn can't process or never finishes is not posted without it`() {
        val file = Files.createTempFile("img", ".png").toFile().apply { writeBytes(byteArrayOf(1)) }
        userinfo()
        initUpload("A")
        server.enqueue(MockResponse().setResponseCode(201))
        mediaStatus("PROCESSING_FAILED")
        assertEquals(
            ApiResult.Failed(ApiError.BadResponse("LinkedIn couldn't process the image")),
            runBlocking { linkedIn.createPost("x", listOf(file)) },
        )
        assertEquals(4, server.requestCount)

        initUpload("B")
        server.enqueue(MockResponse().setResponseCode(201))
        repeat(3) { mediaStatus("PROCESSING") }
        assertEquals(
            ApiResult.Failed(ApiError.BadResponse("LinkedIn is still processing the image; try posting again")),
            runBlocking { linkedIn.createPost("x", listOf(file)) },
        )
        assertEquals(9, server.requestCount)
    }

    @Test
    fun `a token that can't read image status still posts`() {
        val file = Files.createTempFile("img", ".png").toFile().apply { writeBytes(byteArrayOf(1)) }
        userinfo()
        initUpload("A")
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(403))
        created()
        assertEquals(ApiResult.Ok(LinkedInPost("urn:li:share:7400")), runBlocking { linkedIn.createPost("x", listOf(file)) })
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `a failed upload stops before the post`() {
        val file = Files.createTempFile("img", ".png").toFile().apply { writeBytes(byteArrayOf(1)) }
        userinfo()
        initUpload("A")
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(ApiResult.Failed(ApiError.Server(500, null)), runBlocking { linkedIn.createPost("x", listOf(file)) })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `uploads a video in its parts, finalizes it and attaches it as media`() {
        val video = File(Files.createTempDirectory("post").toFile(), "v.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        userinfo()
        server.enqueue(
            MockResponse().setBody(
                """{"value":{"video":"urn:li:video:V","uploadToken":"tok","uploadInstructions":[""" +
                    """{"uploadUrl":"${server.url("/up/1")}","firstByte":0,"lastByte":2},""" +
                    """{"uploadUrl":"${server.url("/up/2")}","firstByte":3,"lastByte":4}]}}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("ETag", "e1"))
        server.enqueue(MockResponse().setHeader("ETag", "e2"))
        server.enqueue(MockResponse())
        mediaStatus("PROCESSING")
        mediaStatus("AVAILABLE")
        created()

        assertEquals(ApiResult.Ok(LinkedInPost("urn:li:share:7400")), runBlocking { linkedIn.createPost("Watch", listOf(video)) })

        server.takeRequest() // userinfo
        val init = server.takeRequest()
        assertEquals("/rest/videos?action=initializeUpload", init.path)
        val initBody = Json.parseToJsonElement(init.body.readUtf8()) as JsonObject
        assertEquals("5", initBody.path("initializeUploadRequest", "fileSizeBytes")?.textOrNull)
        val first = server.takeRequest()
        assertEquals("/up/1", first.path)
        assertEquals(listOf<Byte>(1, 2, 3), first.body.readByteArray().toList())
        assertEquals(listOf<Byte>(4, 5), server.takeRequest().body.readByteArray().toList())
        val finalize = server.takeRequest()
        assertEquals("/rest/videos?action=finalizeUpload", finalize.path)
        val finalizeBody = Json.parseToJsonElement(finalize.body.readUtf8()) as JsonObject
        assertEquals("urn:li:video:V", finalizeBody.path("finalizeUploadRequest", "video")?.textOrNull)
        assertEquals("tok", finalizeBody.path("finalizeUploadRequest", "uploadToken")?.textOrNull)
        assertEquals("e2", finalizeBody.path("finalizeUploadRequest", "uploadedPartIds", 1)?.textOrNull)
        repeat(2) {
            val status = server.takeRequest()
            assertEquals("GET", status.method)
            assertEquals("/rest/videos/urn%3Ali%3Avideo%3AV", status.path)
        }
        val post = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("urn:li:video:V", post.path("content", "media", "id")?.textOrNull)
    }

    @Test
    fun `a video part without an ETag stops before finalizing`() {
        val video = Files.createTempFile("v", ".mp4").toFile().apply { writeBytes(byteArrayOf(1)) }
        userinfo()
        server.enqueue(
            MockResponse().setBody("""{"value":{"video":"urn:li:video:V","uploadInstructions":[{"uploadUrl":"${server.url("/up/1")}","firstByte":0,"lastByte":0}]}}"""),
        )
        server.enqueue(MockResponse())
        assertEquals(ApiResult.Failed(ApiError.BadResponse("no ETag for a video part")), runBlocking { linkedIn.createPost("x", listOf(video)) })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a video LinkedIn can't process is not posted without it`() {
        val video = Files.createTempFile("v", ".mp4").toFile().apply { writeBytes(byteArrayOf(1)) }
        userinfo()
        server.enqueue(
            MockResponse().setBody("""{"value":{"video":"urn:li:video:V","uploadInstructions":[{"uploadUrl":"${server.url("/up/1")}","firstByte":0,"lastByte":0}]}}"""),
        )
        server.enqueue(MockResponse().setHeader("ETag", "e1"))
        server.enqueue(MockResponse())
        mediaStatus("PROCESSING_FAILED")
        assertEquals(
            ApiResult.Failed(ApiError.BadResponse("LinkedIn couldn't process the video")),
            runBlocking { linkedIn.createPost("x", listOf(video)) },
        )
        assertEquals(5, server.requestCount)
    }
}
