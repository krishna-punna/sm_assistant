package com.smassistant.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** How a theme paints its buttons. */
enum class ButtonStyle(val label: String) {
    FILLED("Solid buttons"),
    GRADIENT("Gradient buttons"),
    BORDERED("Bold outlined buttons"),
    ELEVATED("Raised buttons"),
}

/** The groups the Themes screen lists themes under. */
enum class ThemeCategory(val label: String) {
    ORIGINALS("Originals"),
    FUNKY("Funky"),
    PROFESSIONAL("Professional"),
    FONTS("Unique fonts"),
}

/** The colour of one kind of button and of the text on it. */
@Immutable
data class ButtonColors(val container: Color, val content: Color, val gradientEnd: Color = container)

/**
 * A complete look for the app: colours, font, button shape and style, and icon style.
 * [ThemeId.name] is what [ThemeStore] saves, so never rename an id.
 */
@Immutable
data class SmTheme(
    val id: ThemeId,
    val name: String,
    val tagline: String,
    val category: ThemeCategory,
    val fontName: String,
    val colors: ColorScheme,
    /** Body text, buttons and labels. */
    val fontFamily: FontFamily?,
    val buttonShape: Shape,
    val chipShape: Shape,
    val buttonStyle: ButtonStyle,
    val iconStyle: IconStyle,
    val topBar: ButtonColors,
    /** Menu and navigation buttons (Home, AI Agent list, hub, Post in LinkedIn). */
    val menu: ButtonColors,
    /** Commit actions (SAVE, Submit, Retry). */
    val action: ButtonColors,
    /** Back to Home. */
    val back: ButtonColors,
    val chip: ButtonColors,
    val animation: AnimationStyle,
    /** Titles and headings; the body font unless the theme pairs a display font with it. */
    val headingFontFamily: FontFamily? = fontFamily,
)

/**
 * Removed ids (MONO_BRUTALIST, ROYAL_SERIF) are gone on purpose: [ThemeStore] reads an id it
 * no longer knows as Classic, so people who had one selected fall back to the default.
 */
enum class ThemeId {
    CLASSIC, MIDNIGHT_NEON, SUNSET_POP, FOREST_CALM, OCEAN_BREEZE,
    BUBBLEGUM_BLAST, RETRO_ARCADE, COMIC_BOOM,
    CORPORATE_SLATE, GRAPHITE_PRO, MINIMAL_STUDIO,
    HANDWRITTEN_NOTES, TYPEWRITER, RETRO_DINER,
}

object SmThemes {
    /** The original look from the wireframes (docs/requirements/08-ui-conventions.md). */
    val Classic = SmTheme(
        id = ThemeId.CLASSIC,
        name = "Classic",
        tagline = "The original SM Assistant look",
        category = ThemeCategory.ORIGINALS,
        fontName = "System default",
        colors = lightColorScheme(
            primary = SmBlue,
            onPrimary = Color.White,
            secondary = SmGreen,
            onSecondary = Color.White,
            tertiary = SmAmber,
            onTertiary = Color.Black,
            surfaceVariant = SmLavender,
        ),
        fontFamily = null,
        buttonShape = RoundedCornerShape(6.dp),
        chipShape = RoundedCornerShape(4.dp),
        buttonStyle = ButtonStyle.FILLED,
        iconStyle = IconStyle.NONE,
        topBar = ButtonColors(SmBlue, Color.White),
        menu = ButtonColors(SmBlue, Color.White),
        action = ButtonColors(SmGreen, Color.White),
        back = ButtonColors(SmBlack, Color.White),
        chip = ButtonColors(SmAmber, Color.Black),
        animation = AnimationStyle.NONE,
    )

    val MidnightNeon = SmTheme(
        id = ThemeId.MIDNIGHT_NEON,
        name = "Midnight Neon",
        tagline = "Dark mode with electric violet and cyan glow",
        category = ThemeCategory.ORIGINALS,
        fontName = "Space Grotesk",
        colors = darkColorScheme(
            primary = Color(0xFFA78BFA),
            onPrimary = Color(0xFF1A1033),
            secondary = Color(0xFF22D3EE),
            onSecondary = Color(0xFF00222A),
            tertiary = Color(0xFFF472B6),
            background = Color(0xFF0E0B1E),
            onBackground = Color(0xFFECEAF6),
            surface = Color(0xFF0E0B1E),
            onSurface = Color(0xFFECEAF6),
            surfaceVariant = Color(0xFF221C3D),
            onSurfaceVariant = Color(0xFFC9C3E6),
            outline = Color(0xFF6E64A0),
        ),
        fontFamily = SpaceGrotesk,
        buttonShape = RoundedCornerShape(50),
        chipShape = RoundedCornerShape(50),
        buttonStyle = ButtonStyle.GRADIENT,
        iconStyle = IconStyle.OUTLINED,
        topBar = ButtonColors(Color(0xFF171230), Color(0xFFA78BFA)),
        menu = ButtonColors(Color(0xFF7C3AED), Color.White, gradientEnd = Color(0xFF2563EB)),
        action = ButtonColors(Color(0xFF06B6D4), Color(0xFF00222A), gradientEnd = Color(0xFF22D3EE)),
        back = ButtonColors(Color(0xFF2A2347), Color(0xFFECEAF6)),
        chip = ButtonColors(Color(0xFFF472B6), Color(0xFF2A0518)),
        animation = AnimationStyle.PULSE,
    )

    val SunsetPop = SmTheme(
        id = ThemeId.SUNSET_POP,
        name = "Sunset Pop",
        tagline = "Warm coral and peach gradients, pill buttons",
        category = ThemeCategory.ORIGINALS,
        fontName = "Poppins",
        colors = lightColorScheme(
            primary = Color(0xFFE8505B),
            onPrimary = Color.White,
            secondary = Color(0xFFFF9F43),
            onSecondary = Color(0xFF3A1A00),
            tertiary = Color(0xFF7B2CBF),
            background = Color(0xFFFFF6F0),
            surface = Color(0xFFFFF6F0),
            surfaceVariant = Color(0xFFFFE3D6),
            onSurfaceVariant = Color(0xFF5C3A33),
        ),
        fontFamily = Poppins,
        buttonShape = RoundedCornerShape(50),
        chipShape = RoundedCornerShape(50),
        buttonStyle = ButtonStyle.GRADIENT,
        iconStyle = IconStyle.ROUNDED,
        topBar = ButtonColors(Color(0xFFE8505B), Color.White),
        menu = ButtonColors(Color(0xFFFF6B6B), Color.White, gradientEnd = Color(0xFFFF9F43)),
        action = ButtonColors(Color(0xFF7B2CBF), Color.White, gradientEnd = Color(0xFFC0398E)),
        back = ButtonColors(Color(0xFF3D2C2E), Color.White),
        chip = ButtonColors(Color(0xFFFFD166), Color(0xFF3A2A00)),
        animation = AnimationStyle.BOUNCE,
    )

    val ForestCalm = SmTheme(
        id = ThemeId.FOREST_CALM,
        name = "Forest Calm",
        tagline = "Earthy greens and sage, soft rounded cards",
        category = ThemeCategory.ORIGINALS,
        fontName = "Nunito",
        colors = lightColorScheme(
            primary = Color(0xFF2F6B4F),
            onPrimary = Color.White,
            secondary = Color(0xFF8DB596),
            onSecondary = Color(0xFF0F2A1B),
            tertiary = Color(0xFFD4A373),
            background = Color(0xFFF4F7F1),
            surface = Color(0xFFF4F7F1),
            surfaceVariant = Color(0xFFDCE8D9),
            onSurfaceVariant = Color(0xFF34473A),
        ),
        fontFamily = Nunito,
        buttonShape = RoundedCornerShape(16.dp),
        chipShape = RoundedCornerShape(12.dp),
        buttonStyle = ButtonStyle.ELEVATED,
        iconStyle = IconStyle.TWO_TONE,
        topBar = ButtonColors(Color(0xFF1F4D38), Color(0xFFF4F7F1)),
        menu = ButtonColors(Color(0xFF2F6B4F), Color.White),
        action = ButtonColors(Color(0xFFBC6C25), Color.White),
        back = ButtonColors(Color(0xFF283618), Color.White),
        chip = ButtonColors(Color(0xFFE9C46A), Color(0xFF2B2100)),
        animation = AnimationStyle.SUBTLE,
    )

    val OceanBreeze = SmTheme(
        id = ThemeId.OCEAN_BREEZE,
        name = "Ocean Breeze",
        tagline = "Fresh navy and teal, clean geometric type",
        category = ThemeCategory.ORIGINALS,
        fontName = "Outfit",
        colors = lightColorScheme(
            primary = Color(0xFF0B4F6C),
            onPrimary = Color.White,
            secondary = Color(0xFF01BAEF),
            onSecondary = Color(0xFF00222E),
            tertiary = Color(0xFFFF7F50),
            background = Color(0xFFF2F9FC),
            surface = Color(0xFFF2F9FC),
            surfaceVariant = Color(0xFFD6EEF6),
            onSurfaceVariant = Color(0xFF1F4452),
        ),
        fontFamily = Outfit,
        buttonShape = RoundedCornerShape(12.dp),
        chipShape = RoundedCornerShape(8.dp),
        buttonStyle = ButtonStyle.GRADIENT,
        iconStyle = IconStyle.FILLED,
        topBar = ButtonColors(Color(0xFF0B4F6C), Color.White),
        menu = ButtonColors(Color(0xFF0B4F6C), Color.White, gradientEnd = Color(0xFF1B8AAE)),
        action = ButtonColors(Color(0xFF00A6A6), Color.White, gradientEnd = Color(0xFF20C997)),
        back = ButtonColors(Color(0xFF16323D), Color.White),
        chip = ButtonColors(Color(0xFFFF7F50), Color.White),
        animation = AnimationStyle.SUBTLE,
    )

    // Funky

    val BubblegumBlast = SmTheme(
        id = ThemeId.BUBBLEGUM_BLAST,
        name = "Bubblegum Blast",
        tagline = "Hot pink, mint and lilac with bouncy bubble buttons",
        category = ThemeCategory.FUNKY,
        fontName = "Fredoka",
        colors = lightColorScheme(
            primary = Color(0xFFE0218A),
            onPrimary = Color.White,
            secondary = Color(0xFF2EC4B6),
            onSecondary = Color(0xFF00302B),
            tertiary = Color(0xFF9B5DE5),
            background = Color(0xFFFFF0F7),
            surface = Color(0xFFFFF0F7),
            surfaceVariant = Color(0xFFFFD6EC),
            onSurfaceVariant = Color(0xFF5A2346),
        ),
        fontFamily = Fredoka,
        buttonShape = RoundedCornerShape(50),
        chipShape = RoundedCornerShape(50),
        buttonStyle = ButtonStyle.GRADIENT,
        iconStyle = IconStyle.ROUNDED,
        topBar = ButtonColors(Color(0xFFE0218A), Color.White),
        menu = ButtonColors(Color(0xFFF15BB5), Color.White, gradientEnd = Color(0xFF9B5DE5)),
        action = ButtonColors(Color(0xFF00BBA0), Color(0xFF00302B), gradientEnd = Color(0xFF00F5D4)),
        back = ButtonColors(Color(0xFF3A1030), Color.White),
        chip = ButtonColors(Color(0xFFFEE440), Color(0xFF3A2A00)),
        animation = AnimationStyle.BOUNCE,
    )

    val RetroArcade = SmTheme(
        id = ThemeId.RETRO_ARCADE,
        name = "Retro Arcade",
        tagline = "Pixel type and neon on a dark arcade screen",
        category = ThemeCategory.FUNKY,
        fontName = "Pixelify Sans",
        colors = darkColorScheme(
            primary = Color(0xFFFF2A6D),
            onPrimary = Color.White,
            secondary = Color(0xFF05D9E8),
            onSecondary = Color(0xFF001E21),
            tertiary = Color(0xFFF9C80E),
            background = Color(0xFF0D0221),
            onBackground = Color(0xFFF2F2F2),
            surface = Color(0xFF0D0221),
            onSurface = Color(0xFFF2F2F2),
            surfaceVariant = Color(0xFF1F1147),
            onSurfaceVariant = Color(0xFFD1C7FF),
            outline = Color(0xFF7A5CFF),
        ),
        fontFamily = PixelifySans,
        buttonShape = CutCornerShape(8.dp),
        chipShape = CutCornerShape(4.dp),
        buttonStyle = ButtonStyle.BORDERED,
        iconStyle = IconStyle.SHARP,
        topBar = ButtonColors(Color(0xFF1A0B3D), Color(0xFF05D9E8)),
        menu = ButtonColors(Color(0xFFFF2A6D), Color.White),
        action = ButtonColors(Color(0xFF39FF14), Color(0xFF0D0221)),
        back = ButtonColors(Color(0xFF2B1A5E), Color.White),
        chip = ButtonColors(Color(0xFFF9C80E), Color(0xFF0D0221)),
        animation = AnimationStyle.POP,
    )

    val ComicBoom = SmTheme(
        id = ThemeId.COMIC_BOOM,
        name = "Comic Boom",
        tagline = "Comic book panels, bold ink outlines and wiggly icons",
        category = ThemeCategory.FUNKY,
        fontName = "Bangers + Comic Neue",
        colors = lightColorScheme(
            primary = Color(0xFFE63946),
            onPrimary = Color.White,
            secondary = Color(0xFF1D4ED8),
            onSecondary = Color.White,
            tertiary = Color(0xFFFFD60A),
            onTertiary = Color.Black,
            background = Color(0xFFFFFBEA),
            surface = Color(0xFFFFFBEA),
            surfaceVariant = Color(0xFFFFF1B8),
            onSurfaceVariant = Color(0xFF2B2B2B),
            outline = Color.Black,
        ),
        fontFamily = ComicNeue,
        headingFontFamily = Bangers,
        buttonShape = RoundedCornerShape(4.dp),
        chipShape = RoundedCornerShape(2.dp),
        buttonStyle = ButtonStyle.BORDERED,
        iconStyle = IconStyle.FILLED,
        topBar = ButtonColors(Color(0xFFFFD60A), Color.Black),
        menu = ButtonColors(Color(0xFF1D4ED8), Color.White),
        action = ButtonColors(Color(0xFFE63946), Color.White),
        back = ButtonColors(Color(0xFF2B2B2B), Color.White),
        chip = ButtonColors(Color.Black, Color(0xFFFFD60A)),
        animation = AnimationStyle.WIGGLE,
    )

    // Professional

    val CorporateSlate = SmTheme(
        id = ThemeId.CORPORATE_SLATE,
        name = "Corporate Slate",
        tagline = "Navy and slate grey, crisp and businesslike",
        category = ThemeCategory.PROFESSIONAL,
        fontName = "Public Sans",
        colors = lightColorScheme(
            primary = Color(0xFF1E3A5F),
            onPrimary = Color.White,
            secondary = Color(0xFF2563EB),
            onSecondary = Color.White,
            tertiary = Color(0xFF64748B),
            background = Color(0xFFF7F9FC),
            surface = Color(0xFFF7F9FC),
            surfaceVariant = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF334155),
            outline = Color(0xFF94A3B8),
        ),
        fontFamily = PublicSans,
        buttonShape = RoundedCornerShape(8.dp),
        chipShape = RoundedCornerShape(6.dp),
        buttonStyle = ButtonStyle.FILLED,
        iconStyle = IconStyle.OUTLINED,
        topBar = ButtonColors(Color(0xFF1E3A5F), Color.White),
        menu = ButtonColors(Color(0xFF1E3A5F), Color.White),
        action = ButtonColors(Color(0xFF2563EB), Color.White),
        back = ButtonColors(Color(0xFF0F172A), Color.White),
        chip = ButtonColors(Color(0xFFDBEAFE), Color(0xFF1E3A8A)),
        animation = AnimationStyle.SUBTLE,
    )

    val GraphitePro = SmTheme(
        id = ThemeId.GRAPHITE_PRO,
        name = "Graphite Pro",
        tagline = "Dark graphite with an emerald accent, made for focus",
        category = ThemeCategory.PROFESSIONAL,
        fontName = "Manrope",
        colors = darkColorScheme(
            primary = Color(0xFF34D399),
            onPrimary = Color(0xFF00281A),
            secondary = Color(0xFF60A5FA),
            onSecondary = Color(0xFF00213F),
            tertiary = Color(0xFFFBBF24),
            background = Color(0xFF121417),
            onBackground = Color(0xFFE6E8EB),
            surface = Color(0xFF121417),
            onSurface = Color(0xFFE6E8EB),
            surfaceVariant = Color(0xFF22262B),
            onSurfaceVariant = Color(0xFFB8BEC6),
            outline = Color(0xFF4B525B),
        ),
        fontFamily = Manrope,
        buttonShape = RoundedCornerShape(10.dp),
        chipShape = RoundedCornerShape(6.dp),
        buttonStyle = ButtonStyle.ELEVATED,
        iconStyle = IconStyle.SHARP,
        topBar = ButtonColors(Color(0xFF1A1D21), Color(0xFFE6E8EB)),
        menu = ButtonColors(Color(0xFF2A2F35), Color(0xFFE6E8EB)),
        action = ButtonColors(Color(0xFF10B981), Color(0xFF00281A)),
        back = ButtonColors(Color(0xFF3A4048), Color.White),
        chip = ButtonColors(Color(0xFF34D399), Color(0xFF00281A)),
        animation = AnimationStyle.PULSE,
    )

    val MinimalStudio = SmTheme(
        id = ThemeId.MINIMAL_STUDIO,
        name = "Minimal Studio",
        tagline = "Clean white space, ink black and one indigo accent",
        category = ThemeCategory.PROFESSIONAL,
        fontName = "Figtree",
        colors = lightColorScheme(
            primary = Color(0xFF111111),
            onPrimary = Color.White,
            secondary = Color(0xFF4F46E5),
            onSecondary = Color.White,
            tertiary = Color(0xFFF43F5E),
            background = Color.White,
            onBackground = Color(0xFF111111),
            surface = Color.White,
            onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFF3F4F6),
            onSurfaceVariant = Color(0xFF4B5563),
            outline = Color(0xFFD1D5DB),
        ),
        fontFamily = Figtree,
        buttonShape = RoundedCornerShape(10.dp),
        chipShape = RoundedCornerShape(50),
        buttonStyle = ButtonStyle.FILLED,
        iconStyle = IconStyle.FILLED,
        topBar = ButtonColors(Color.White, Color(0xFF111111)),
        menu = ButtonColors(Color(0xFF111111), Color.White),
        action = ButtonColors(Color(0xFF4F46E5), Color.White),
        back = ButtonColors(Color(0xFFF3F4F6), Color(0xFF111111)),
        chip = ButtonColors(Color(0xFFEEF2FF), Color(0xFF3730A3)),
        animation = AnimationStyle.SUBTLE,
    )

    // Unique fonts

    val HandwrittenNotes = SmTheme(
        id = ThemeId.HANDWRITTEN_NOTES,
        name = "Handwritten Notes",
        tagline = "Blue ink on notebook paper with sticky-note chips",
        category = ThemeCategory.FONTS,
        fontName = "Caveat + Patrick Hand",
        colors = lightColorScheme(
            primary = Color(0xFF2B4C9B),
            onPrimary = Color.White,
            secondary = Color(0xFFE76F51),
            onSecondary = Color.White,
            tertiary = Color(0xFFF4D35E),
            background = Color(0xFFFFFDF5),
            onBackground = Color(0xFF1F2A5A),
            surface = Color(0xFFFFFDF5),
            onSurface = Color(0xFF1F2A5A),
            surfaceVariant = Color(0xFFFFF4C2),
            onSurfaceVariant = Color(0xFF3D3A2A),
            outline = Color(0xFF9AA9D6),
        ),
        fontFamily = PatrickHand,
        headingFontFamily = Caveat,
        // Uneven corners, like a shape drawn by hand.
        buttonShape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 16.dp, bottomStart = 8.dp),
        chipShape = RoundedCornerShape(topStart = 4.dp, topEnd = 10.dp, bottomEnd = 3.dp, bottomStart = 8.dp),
        buttonStyle = ButtonStyle.FILLED,
        iconStyle = IconStyle.TWO_TONE,
        topBar = ButtonColors(Color(0xFF2B4C9B), Color(0xFFFFFDF5)),
        menu = ButtonColors(Color(0xFF2B4C9B), Color.White),
        action = ButtonColors(Color(0xFFE76F51), Color.White),
        back = ButtonColors(Color(0xFF3D3A2A), Color.White),
        chip = ButtonColors(Color(0xFFF4D35E), Color(0xFF3D3A2A)),
        animation = AnimationStyle.WIGGLE,
    )

    val Typewriter = SmTheme(
        id = ThemeId.TYPEWRITER,
        name = "Typewriter",
        tagline = "Sepia paper, inked type and keys that press down",
        category = ThemeCategory.FONTS,
        fontName = "Special Elite + Courier Prime",
        colors = lightColorScheme(
            primary = Color(0xFF2B2622),
            onPrimary = Color(0xFFF4ECD8),
            secondary = Color(0xFFB23A48),
            onSecondary = Color.White,
            tertiary = Color(0xFF6B705C),
            background = Color(0xFFF4ECD8),
            onBackground = Color(0xFF2B2622),
            surface = Color(0xFFF4ECD8),
            onSurface = Color(0xFF2B2622),
            surfaceVariant = Color(0xFFE8DCC0),
            onSurfaceVariant = Color(0xFF4A4038),
            outline = Color(0xFF2B2622),
        ),
        fontFamily = CourierPrime,
        headingFontFamily = SpecialElite,
        buttonShape = RoundedCornerShape(2.dp),
        chipShape = RoundedCornerShape(2.dp),
        buttonStyle = ButtonStyle.BORDERED,
        iconStyle = IconStyle.SHARP,
        topBar = ButtonColors(Color(0xFF2B2622), Color(0xFFF4ECD8)),
        menu = ButtonColors(Color(0xFFFFFAF0), Color(0xFF2B2622)),
        action = ButtonColors(Color(0xFFB23A48), Color.White),
        back = ButtonColors(Color(0xFF2B2622), Color(0xFFF4ECD8)),
        chip = ButtonColors(Color(0xFF6B705C), Color.White),
        animation = AnimationStyle.KEYPRESS,
    )

    val RetroDiner = SmTheme(
        id = ThemeId.RETRO_DINER,
        name = "Retro Diner",
        tagline = "Fifties script titles, cherry red and teal, spinning icons",
        category = ThemeCategory.FONTS,
        fontName = "Lobster + Quicksand",
        colors = lightColorScheme(
            primary = Color(0xFFD62828),
            onPrimary = Color.White,
            secondary = Color(0xFF2A9D8F),
            onSecondary = Color.White,
            tertiary = Color(0xFFF4A261),
            background = Color(0xFFFFF8F0),
            surface = Color(0xFFFFF8F0),
            surfaceVariant = Color(0xFFFDE2D2),
            onSurfaceVariant = Color(0xFF4A2C2A),
        ),
        fontFamily = Quicksand,
        headingFontFamily = Lobster,
        buttonShape = RoundedCornerShape(50),
        chipShape = RoundedCornerShape(50),
        buttonStyle = ButtonStyle.ELEVATED,
        iconStyle = IconStyle.ROUNDED,
        topBar = ButtonColors(Color(0xFF2A9D8F), Color.White),
        menu = ButtonColors(Color(0xFFD62828), Color.White),
        action = ButtonColors(Color(0xFF2A9D8F), Color.White),
        back = ButtonColors(Color(0xFF264653), Color.White),
        chip = ButtonColors(Color(0xFFF4A261), Color(0xFF3A1F00)),
        animation = AnimationStyle.SPIN,
    )

    /** Every theme in the order the Themes screen lists them, Classic first. */
    val all = listOf(
        Classic, MidnightNeon, SunsetPop, ForestCalm, OceanBreeze,
        BubblegumBlast, RetroArcade, ComicBoom,
        CorporateSlate, GraphitePro, MinimalStudio,
        HandwrittenNotes, Typewriter, RetroDiner,
    )

    fun byId(id: ThemeId): SmTheme = all.first { it.id == id }
}
