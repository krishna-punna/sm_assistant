package com.smassistant.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smassistant.app.media.GALLERY_FOLDER
import com.smassistant.app.media.MediaExport
import com.smassistant.app.media.gallerySaveNeedsPermission
import com.smassistant.app.media.isVideo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share and Save to Gallery for generated media (improvement #6): the files live in the cache
 * and expire after a week, so these let the person post them elsewhere or keep them.
 * [files] is read when a button is tapped, so it can follow a selection.
 */
@Composable
fun MediaActions(
    files: () -> List<File>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    share: (Context, List<File>) -> Unit = { context, picked -> context.startActivity(MediaExport.shareIntent(context, picked)) },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    fun save() {
        val picked = files()
        if (picked.isEmpty()) return
        saving = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { MediaExport.saveToGallery(context.applicationContext, picked) }
            saving = false
            val where = if (picked.all(::isVideo)) "Movies/$GALLERY_FOLDER" else if (picked.none(::isVideo)) "Pictures/$GALLERY_FOLDER" else GALLERY_FOLDER
            val message = when {
                saved == 0 -> "Couldn't save to the gallery"
                saved < picked.size -> "Saved $saved of ${picked.size} to $where"
                else -> "Saved to $where"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else Toast.makeText(context, "Storage permission is needed to save", Toast.LENGTH_SHORT).show()
    }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                val picked = files()
                if (picked.isNotEmpty()) {
                    runCatching { share(context, picked) }
                        .onFailure { Toast.makeText(context, "No app can share this", Toast.LENGTH_SHORT).show() }
                }
            },
            enabled = enabled,
            modifier = Modifier.weight(1f).testTag("shareMedia"),
        ) { Text("Share") }
        OutlinedButton(
            onClick = {
                val write = Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (gallerySaveNeedsPermission() &&
                    ContextCompat.checkSelfPermission(context, write) != PackageManager.PERMISSION_GRANTED
                ) {
                    askPermission.launch(write)
                } else {
                    save()
                }
            },
            enabled = enabled && !saving,
            modifier = Modifier.weight(1f).testTag("saveMedia"),
        ) { Text(if (saving) "Saving…" else "Save to Gallery") }
    }
}
