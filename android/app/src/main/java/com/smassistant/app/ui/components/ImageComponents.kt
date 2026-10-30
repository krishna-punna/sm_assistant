package com.smassistant.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes [file] off the main thread, scaled down to about [maxSidePx]. */
@Composable
private fun rememberImage(file: File, maxSidePx: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file, maxSidePx) {
        value = withContext(Dispatchers.IO) { decodeScaled(file, maxSidePx)?.asImageBitmap() }
    }
    return image
}

private fun decodeScaled(file: File, maxSidePx: Int) = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

@Composable
fun GeneratedImage(file: File, modifier: Modifier = Modifier, maxSidePx: Int = 512, contentScale: ContentScale = ContentScale.Crop) {
    val image = rememberImage(file, maxSidePx)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image == null) {
            CircularProgressIndicator(
                Modifier
                    .size(24.dp)
                    .semantics { contentDescription = "Loading image" },
                strokeWidth = 2.dp,
            )
        } else {
            // The thumbnail or preview around it names the image ("Image 2"), so this stays silent.
            Image(image, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Thumbnails of a generation (IM-2, slide 7) with a count badge and a checkbox per image
 * for choosing what to post (IM-5, interim for Q-10: all selected to start). Tap opens the preview.
 */
@Composable
fun ImageStrip(
    images: List<File>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    labels: List<String?> = emptyList(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${images.size} images · ${selected.size} selected",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag("imageCount"),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("imageStrip")) {
            itemsIndexed(images) { index, file ->
                Box(
                    Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "Open preview") { onOpen(index) }
                        .semantics { contentDescription = "Image ${index + 1}" },
                ) {
                    GeneratedImage(file, Modifier.fillMaxSize(), maxSidePx = 400)
                    labels.getOrNull(index)?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("imageLabel$index"),
                        )
                    }
                    Checkbox(
                        checked = index in selected,
                        onCheckedChange = { onToggle(index) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .semantics { contentDescription = "Post image ${index + 1}" }
                            .testTag("select$index"),
                    )
                }
            }
        }
    }
}

/**
 * Full image preview (IM-3, slide 8): swipe between images, pinch to zoom, with the
 * actions passed in [actions] (Post in LinkedIn, Back to Home) underneath.
 */
@Composable
fun ImagePreview(
    images: List<File>,
    startIndex: Int,
    onDismiss: () -> Unit,
    actions: @Composable (currentIndex: Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val pager = rememberPagerState(initialPage = startIndex) { images.size }
                Text("Image ${pager.currentPage + 1} of ${images.size}", style = MaterialTheme.typography.titleMedium)
                HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth().testTag("imagePager")) { page ->
                    ZoomableImage(images[page], "Image ${page + 1} of ${images.size}")
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { actions(pager.currentPage) }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File, description: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offsetX = if (scale == 1f) 0f else offsetX + pan.x
        offsetY = if (scale == 1f) 0f else offsetY + pan.y
    }
    GeneratedImage(
        file,
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
        maxSidePx = 2048,
        contentScale = ContentScale.Fit,
    )
}
