package com.smassistant.app.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Turns slide images into a video file (docs/tasks VI-I1, Q-08 = slideshow). */
fun interface SlideshowMaker {
    suspend fun make(slides: List<File>, output: File): Result<File>
}

/** Seconds each slide stays on screen. Four slides make a 16 s video, well inside LinkedIn's 3 s–10 min range. */
const val SECONDS_PER_SLIDE = 4

/**
 * Encodes the slides on the device with Media3 Transformer: each image becomes a still
 * [SECONDS_PER_SLIDE] long, scaled to fit a [SIZE_PX] square, into an H.264 MP4 without audio.
 */
@OptIn(UnstableApi::class) // Transformer, Composition and Presentation are all marked unstable in Media3 1.8.
class Media3SlideshowMaker(private val context: Context) : SlideshowMaker {
    override suspend fun make(slides: List<File>, output: File): Result<File> = withContext(Dispatchers.Main) {
        // Transformer must be created and started on a thread with a Looper; its callbacks come back here.
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(Result.success(output))
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        output.delete()
                        continuation.resume(Result.failure(exportException))
                    }
                })
                .build()
            continuation.invokeOnCancellation {
                transformer.cancel()
                output.delete()
            }
            transformer.start(composition(slides), output.absolutePath)
        }
    }

    private fun composition(slides: List<File>): Composition {
        val fit = Presentation.createForWidthAndHeight(SIZE_PX, SIZE_PX, Presentation.LAYOUT_SCALE_TO_FIT)
        val items = slides.map { slide ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(slide))
                .setMimeType(imageMimeType(slide))
                .setImageDurationMs(SECONDS_PER_SLIDE * 1000L)
                .build()
            EditedMediaItem.Builder(mediaItem)
                .setFrameRate(FRAME_RATE)
                .setEffects(Effects(emptyList(), listOf(fit)))
                .build()
        }
        return Composition.Builder(EditedMediaItemSequence.Builder(items).build()).build()
    }

    private companion object {
        const val SIZE_PX = 1080
        const val FRAME_RATE = 30
    }
}
