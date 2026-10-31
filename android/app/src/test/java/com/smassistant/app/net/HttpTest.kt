package com.smassistant.app.net

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTest {
    @Test
    fun `logging redacts every secret header`() {
        val lines = mutableListOf<String>()
        val client = Http.build(logger = { lines += it })
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            val request = Request.Builder()
                .url(server.url("/"))
                .header("Authorization", "Bearer li-token")
                .header("x-api-key", "sk-ant")
                .header("x-goog-api-key", "AIza")
                .build()
            client.newCall(request).execute().close()
        }

        val log = lines.joinToString("\n")
        listOf("li-token", "sk-ant", "AIza").forEach { assertFalse(it, log.contains(it)) }
        assertTrue(log.contains("x-api-key: ██"))
    }

    @Test
    fun `api errors from http codes and exceptions`() {
        assertEquals(ApiError.InvalidKey, ApiError.fromHttp(401))
        assertEquals(ApiError.NoAccess, ApiError.fromHttp(403))
        assertEquals(ApiError.RateLimited, ApiError.fromHttp(429))
        assertEquals(ApiError.Server(500, "boom"), ApiError.fromHttp(500, "boom"))
        assertEquals(ApiError.Unreachable("Timed out"), ApiError.fromException(SocketTimeoutException()))
        assertEquals(ApiError.Unreachable("no route"), ApiError.fromException(IOException("no route")))
    }

    @Test
    fun `user messages name the service`() {
        assertEquals("Gemini rejected the API key. Check it in settings.", ApiError.InvalidKey.userMessage("Gemini"))
        assertEquals("Groq returned an error (HTTP 500)", ApiError.Server(500).userMessage("Groq"))
    }
}
