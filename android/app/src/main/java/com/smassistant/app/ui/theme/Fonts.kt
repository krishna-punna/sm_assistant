package com.smassistant.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.smassistant.app.R

// Google Fonts bundled in res/font (OFL; licences in assets/licenses/fonts), so themes work offline.

private val Weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** One family from a variable-weight font file, pinned at the weights the app uses. */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int) = FontFamily(
    Weights.map { weight ->
        Font(resId = resId, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

val SpaceGrotesk = variableFamily(R.font.space_grotesk)
val Nunito = variableFamily(R.font.nunito)
val PlayfairDisplay = variableFamily(R.font.playfair_display)
val Outfit = variableFamily(R.font.outfit)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_semibold, FontWeight.Bold),
)

val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.SemiBold),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)
