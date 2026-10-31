package com.smassistant.app.net

import android.util.Log
import com.smassistant.app.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** One HTTP client for every API the app calls. */
object Http {
    /** Headers that carry API keys or tokens. Never written to the log. */
    val SECRET_HEADERS = listOf("Authorization", "x-api-key", "x-goog-api-key")

    /**
     * Debug builds log request lines and headers (never bodies) with [SECRET_HEADERS] redacted.
     * Release builds don't log.
     */
    val client: OkHttpClient by lazy {
        build(logger = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Logger { Log.d("Http", it) } else null)
    }

    /** For generation calls, which can take a minute or more to answer. */
    val generationClient: OkHttpClient by lazy { client.newBuilder().readTimeout(180, TimeUnit.SECONDS).build() }

    fun build(
        logger: HttpLoggingInterceptor.Logger? = null,
        timeoutSeconds: Long = 10,
        readTimeoutSeconds: Long = timeoutSeconds,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .apply {
            if (logger != null) {
                addInterceptor(
                    HttpLoggingInterceptor(logger).apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                        SECRET_HEADERS.forEach(::redactHeader)
                    }
                )
            }
        }
        .build()
}

/** Runs the call without blocking; cancelling the coroutine cancels the request. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}
