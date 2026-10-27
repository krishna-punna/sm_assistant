package com.smassistant.app.ui.theme

import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MotionTest {
    @get:Rule val compose = createComposeRule()

    private val resolver get() = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

    @After
    fun restore() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun themeAnimationIsUsedWhenMotionIsOn() {
        var motion: AnimationStyle? = null
        compose.setContent { SmAssistantTheme(SmThemes.BubblegumBlast) { motion = currentMotion() } }
        compose.waitForIdle()
        assertEquals(AnimationStyle.BOUNCE, motion)
    }

    @Test
    fun removeAnimationsTurnsThemeMotionOff() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var motion: AnimationStyle? = null
        compose.setContent { SmAssistantTheme(SmThemes.BubblegumBlast) { motion = currentMotion() } }
        compose.waitForIdle()
        assertEquals(AnimationStyle.NONE, motion)
    }
}
