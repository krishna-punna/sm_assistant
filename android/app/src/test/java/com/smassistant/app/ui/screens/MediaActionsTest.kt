package com.smassistant.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.smassistant.app.media.MediaCache
import com.smassistant.app.ui.components.MediaActions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Improvement #6: Share hands the files to the share sheet (its intent is in MediaExportTest); both buttons follow [enabled]. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MediaActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shareHandsTheFilesToTheSystemSheet() {
        val file = MediaCache(compose.activity).save(byteArrayOf(1), "png")
        val shared = mutableListOf<List<File>>()
        compose.setContent { MediaActions(files = { listOf(file) }, share = { _, picked -> shared += picked }) }

        compose.onNodeWithTag("shareMedia").performClick()

        assertEquals(listOf(listOf(file)), shared)
    }

    @Test
    fun buttonsAreOffWithNothingSelected() {
        compose.setContent { MediaActions(files = { emptyList() }, enabled = false) }
        compose.onNodeWithTag("shareMedia").assertIsNotEnabled()
        compose.onNodeWithTag("saveMedia").assertIsNotEnabled()
    }
}
