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
    val fontName: String,
    val colors: ColorScheme,
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
)

enum class ThemeId { CLASSIC, MIDNIGHT_NEON, SUNSET_POP, FOREST_CALM, MONO_BRUTALIST, ROYAL_SERIF, OCEAN_BREEZE }

object SmThemes {
    /** The original look from the wireframes (docs/requirements/08-ui-conventions.md). */
    val Classic = SmTheme(
        id = ThemeId.CLASSIC,
        name = "Classic",
        tagline = "The original SM Assistant look",
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
    )

    val MidnightNeon = SmTheme(
        id = ThemeId.MIDNIGHT_NEON,
        name = "Midnight Neon",
        tagline = "Dark mode with electric violet and cyan glow",
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
    )

    val SunsetPop = SmTheme(
        id = ThemeId.SUNSET_POP,
        name = "Sunset Pop",
        tagline = "Warm coral and peach gradients, pill buttons",
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
    )

    val ForestCalm = SmTheme(
        id = ThemeId.FOREST_CALM,
        name = "Forest Calm",
        tagline = "Earthy greens and sage, soft rounded cards",
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
    )

    val MonoBrutalist = SmTheme(
        id = ThemeId.MONO_BRUTALIST,
        name = "Mono Brutalist",
        tagline = "Black, white and signal yellow with hard edges",
        fontName = "Space Mono",
        colors = lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            secondary = Color(0xFFFFE600),
            onSecondary = Color.Black,
            tertiary = Color(0xFFFF3B30),
            background = Color(0xFFFAFAF7),
            surface = Color(0xFFFAFAF7),
            surfaceVariant = Color(0xFFEDEDE8),
            onSurfaceVariant = Color(0xFF222222),
            outline = Color.Black,
        ),
        fontFamily = SpaceMono,
        buttonShape = RoundedCornerShape(0.dp),
        chipShape = RoundedCornerShape(0.dp),
        buttonStyle = ButtonStyle.BORDERED,
        iconStyle = IconStyle.SHARP,
        topBar = ButtonColors(Color.Black, Color(0xFFFFE600)),
        menu = ButtonColors(Color.White, Color.Black),
        action = ButtonColors(Color(0xFFFFE600), Color.Black),
        back = ButtonColors(Color.Black, Color.White),
        chip = ButtonColors(Color(0xFFFF3B30), Color.White),
    )

    val RoyalSerif = SmTheme(
        id = ThemeId.ROYAL_SERIF,
        name = "Royal Serif",
        tagline = "Editorial plum and gold with an elegant serif",
        fontName = "Playfair Display",
        colors = lightColorScheme(
            primary = Color(0xFF4A1D6E),
            onPrimary = Color.White,
            secondary = Color(0xFFC9A227),
            onSecondary = Color(0xFF2A1F00),
            tertiary = Color(0xFF8E3B5E),
            background = Color(0xFFFBF8F3),
            surface = Color(0xFFFBF8F3),
            surfaceVariant = Color(0xFFEDE4F2),
            onSurfaceVariant = Color(0xFF3F2E4A),
        ),
        fontFamily = PlayfairDisplay,
        buttonShape = CutCornerShape(10.dp),
        chipShape = CutCornerShape(6.dp),
        buttonStyle = ButtonStyle.FILLED,
        iconStyle = IconStyle.OUTLINED,
        topBar = ButtonColors(Color(0xFF2E0F47), Color(0xFFE9C46A)),
        menu = ButtonColors(Color(0xFF4A1D6E), Color(0xFFFBF3DC)),
        action = ButtonColors(Color(0xFFC9A227), Color(0xFF2A1F00)),
        back = ButtonColors(Color(0xFF1E1226), Color(0xFFE9C46A)),
        chip = ButtonColors(Color(0xFFE9C46A), Color(0xFF2E0F47)),
    )

    val OceanBreeze = SmTheme(
        id = ThemeId.OCEAN_BREEZE,
        name = "Ocean Breeze",
        tagline = "Fresh navy and teal, clean geometric type",
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
    )

    /** Every theme in the order the Themes screen lists them, Classic first. */
    val all = listOf(Classic, MidnightNeon, SunsetPop, ForestCalm, MonoBrutalist, RoyalSerif, OceanBreeze)

    fun byId(id: ThemeId): SmTheme = all.first { it.id == id }
}
