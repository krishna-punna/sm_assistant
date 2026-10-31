package com.smassistant.app.ui.screens

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.smassistant.app.media.MediaCache
import com.smassistant.app.ui.components.MediaActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Improvement #6: Share opens the system sheet for the files; both buttons follow [enabled]. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MediaActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shareOpensTheSystemSheet() {
        val file = MediaCache(compose.activity).save(byteArrayOf(1), "png")
        compose.setContent { MediaActions(files = { listOf(file) }) }

        compose.onNodeWithTag("shareMedia").performClick()

        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, started.action)
    }

    @Test
    fun buttonsAreOffWithNothingSelected() {
        compose.setContent { MediaActions(files = { emptyList() }, enabled = false) }
        compose.onNodeWithTag("shareMedia").assertIsNotEnabled()
        compose.onNodeWithTag("saveMedia").assertIsNotEnabled()
    }
}
