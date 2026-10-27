package com.smassistant.app.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `fresh install uses the classic theme`() {
        assertEquals(ThemeId.CLASSIC, ThemeStore(context).themeId)
    }

    @Test
    fun `saved theme survives a new store`() {
        ThemeStore(context).save(ThemeId.MIDNIGHT_NEON)
        assertEquals(ThemeId.MIDNIGHT_NEON, ThemeStore(context).themeId)
    }

    @Test
    fun `unknown saved id falls back to classic`() {
        prefs.edit().putString("theme_id", "GONE").commit()
        assertEquals(ThemeId.CLASSIC, ThemeStore(context).themeId)
    }

    @Test
    fun `removed themes fall back to classic`() {
        listOf("MONO_BRUTALIST", "ROYAL_SERIF").forEach { removed ->
            prefs.edit().putString("theme_id", removed).commit()
            assertEquals(removed, ThemeId.CLASSIC, ThemeStore(context).themeId)
        }
    }

    @Test
    fun `every id has one theme and there are at least five besides classic`() {
        assertEquals(ThemeId.entries.toList(), SmThemes.all.map { it.id })
        assertTrue(SmThemes.all.size >= 6)
        assertEquals(SmThemes.Classic, SmThemes.all.first())
    }

    @Test
    fun `every new theme has its own font and icons`() {
        SmThemes.all.drop(1).forEach {
            assertNotNull(it.name, it.fontFamily)
            assertTrue(it.name, it.iconStyle != IconStyle.NONE)
        }
        assertEquals(SmThemes.all.size, SmThemes.all.map { it.name }.distinct().size)
    }

    @Test
    fun `every new theme animates and classic stays still`() {
        assertEquals(AnimationStyle.NONE, SmThemes.Classic.animation)
        SmThemes.all.drop(1).forEach { assertTrue(it.name, it.animation != AnimationStyle.NONE) }
    }

    @Test
    fun `there are funky, professional and font themes`() {
        listOf(ThemeCategory.FUNKY, ThemeCategory.PROFESSIONAL, ThemeCategory.FONTS).forEach { category ->
            val themes = SmThemes.all.filter { it.category == category }
            assertTrue(category.name, themes.size >= 3)
        }
        SmThemes.all.filter { it.category == ThemeCategory.FONTS }.forEach {
            assertTrue(it.name, it.headingFontFamily != it.fontFamily)
        }
    }
}
