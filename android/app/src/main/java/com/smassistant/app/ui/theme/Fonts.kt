package com.smassistant.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.smassistant.app.R

// Google Fonts bundled in res/font (OFL, Special Elite Apache 2.0; licences in assets/licenses/fonts),
// so themes work offline.

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
val Outfit = variableFamily(R.font.outfit)
val Fredoka = variableFamily(R.font.fredoka)
val PixelifySans = variableFamily(R.font.pixelify_sans)
val PublicSans = variableFamily(R.font.public_sans)
val Manrope = variableFamily(R.font.manrope)
val Figtree = variableFamily(R.font.figtree)
val Caveat = variableFamily(R.font.caveat)
val Quicksand = variableFamily(R.font.quicksand)

/**
 * A display font that ships in one weight, listed at every weight the app uses so bold
 * titles use the real letterforms instead of a smeared synthetic bold.
 */
private fun singleWeightFamily(resId: Int) = FontFamily(Weights.map { Font(resId, it) })

val Bangers = singleWeightFamily(R.font.bangers)
val PatrickHand = singleWeightFamily(R.font.patrick_hand)
val SpecialElite = singleWeightFamily(R.font.special_elite)
val Lobster = singleWeightFamily(R.font.lobster)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_semibold, FontWeight.Bold),
)

val ComicNeue = FontFamily(
    Font(R.font.comic_neue_regular, FontWeight.Normal),
    Font(R.font.comic_neue_bold, FontWeight.SemiBold),
    Font(R.font.comic_neue_bold, FontWeight.Bold),
)

val CourierPrime = FontFamily(
    Font(R.font.courier_prime_regular, FontWeight.Normal),
    Font(R.font.courier_prime_bold, FontWeight.SemiBold),
    Font(R.font.courier_prime_bold, FontWeight.Bold),
)
