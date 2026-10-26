package com.smassistant.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Build as FilledBuild
import androidx.compose.material.icons.filled.CheckCircle as FilledCheckCircle
import androidx.compose.material.icons.filled.Edit as FilledEdit
import androidx.compose.material.icons.filled.Face as FilledFace
import androidx.compose.material.icons.filled.Home as FilledHome
import androidx.compose.material.icons.filled.Share as FilledShare
import androidx.compose.material.icons.filled.Star as FilledStar
import androidx.compose.material.icons.outlined.Build as OutlinedBuild
import androidx.compose.material.icons.outlined.CheckCircle as OutlinedCheckCircle
import androidx.compose.material.icons.outlined.Edit as OutlinedEdit
import androidx.compose.material.icons.outlined.Face as OutlinedFace
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.Share as OutlinedShare
import androidx.compose.material.icons.outlined.Star as OutlinedStar
import androidx.compose.material.icons.rounded.Build as RoundedBuild
import androidx.compose.material.icons.rounded.CheckCircle as RoundedCheckCircle
import androidx.compose.material.icons.rounded.Edit as RoundedEdit
import androidx.compose.material.icons.rounded.Face as RoundedFace
import androidx.compose.material.icons.rounded.Home as RoundedHome
import androidx.compose.material.icons.rounded.Share as RoundedShare
import androidx.compose.material.icons.rounded.Star as RoundedStar
import androidx.compose.material.icons.sharp.Build as SharpBuild
import androidx.compose.material.icons.sharp.CheckCircle as SharpCheckCircle
import androidx.compose.material.icons.sharp.Edit as SharpEdit
import androidx.compose.material.icons.sharp.Face as SharpFace
import androidx.compose.material.icons.sharp.Home as SharpHome
import androidx.compose.material.icons.sharp.Share as SharpShare
import androidx.compose.material.icons.sharp.Star as SharpStar
import androidx.compose.material.icons.twotone.Build as TwoToneBuild
import androidx.compose.material.icons.twotone.CheckCircle as TwoToneCheckCircle
import androidx.compose.material.icons.twotone.Edit as TwoToneEdit
import androidx.compose.material.icons.twotone.Face as TwoToneFace
import androidx.compose.material.icons.twotone.Home as TwoToneHome
import androidx.compose.material.icons.twotone.Share as TwoToneShare
import androidx.compose.material.icons.twotone.Star as TwoToneStar

/** How a theme draws button icons. The Classic theme keeps the original text-only buttons. */
enum class IconStyle(val label: String) {
    NONE("No icons"),
    FILLED("Filled icons"),
    OUTLINED("Outlined icons"),
    ROUNDED("Rounded icons"),
    SHARP("Sharp icons"),
    TWO_TONE("Two-tone icons"),
}

/** Icons used on buttons, each in every [IconStyle]. */
enum class SmIcon(
    private val filled: ImageVector,
    private val outlined: ImageVector,
    private val rounded: ImageVector,
    private val sharp: ImageVector,
    private val twoTone: ImageVector,
) {
    LINKEDIN(Icons.Filled.FilledShare, Icons.Outlined.OutlinedShare, Icons.Rounded.RoundedShare, Icons.Sharp.SharpShare, Icons.TwoTone.TwoToneShare),
    AGENT(Icons.Filled.FilledBuild, Icons.Outlined.OutlinedBuild, Icons.Rounded.RoundedBuild, Icons.Sharp.SharpBuild, Icons.TwoTone.TwoToneBuild),
    ASSISTANT(Icons.Filled.FilledFace, Icons.Outlined.OutlinedFace, Icons.Rounded.RoundedFace, Icons.Sharp.SharpFace, Icons.TwoTone.TwoToneFace),
    HASHTAGS(Icons.Filled.FilledEdit, Icons.Outlined.OutlinedEdit, Icons.Rounded.RoundedEdit, Icons.Sharp.SharpEdit, Icons.TwoTone.TwoToneEdit),
    STATUS(
        Icons.Filled.FilledCheckCircle, Icons.Outlined.OutlinedCheckCircle, Icons.Rounded.RoundedCheckCircle,
        Icons.Sharp.SharpCheckCircle, Icons.TwoTone.TwoToneCheckCircle,
    ),
    THEMES(Icons.Filled.FilledStar, Icons.Outlined.OutlinedStar, Icons.Rounded.RoundedStar, Icons.Sharp.SharpStar, Icons.TwoTone.TwoToneStar),
    HOME(Icons.Filled.FilledHome, Icons.Outlined.OutlinedHome, Icons.Rounded.RoundedHome, Icons.Sharp.SharpHome, Icons.TwoTone.TwoToneHome);

    /** The vector for [style], or null when the theme shows no icons. */
    fun vector(style: IconStyle): ImageVector? = when (style) {
        IconStyle.NONE -> null
        IconStyle.FILLED -> filled
        IconStyle.OUTLINED -> outlined
        IconStyle.ROUNDED -> rounded
        IconStyle.SHARP -> sharp
        IconStyle.TWO_TONE -> twoTone
    }
}
