package com.smassistant.app.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * How a theme moves its buttons, chips and icons. Buttons react to presses and some styles
 * animate in when they appear; icons play their move when they appear and on every press.
 */
enum class AnimationStyle(
    val label: String,
    /** How small a button gets while held down. */
    internal val pressScale: Float,
    /** Whether buttons and chips animate in when a screen opens. */
    internal val entrance: Boolean,
) {
    NONE("No animations", 1f, false),
    SUBTLE("Smooth, subtle motion", 0.97f, true),
    BOUNCE("Bouncy buttons", 0.9f, true),
    WIGGLE("Wiggly icons", 0.95f, false),
    PULSE("Pulsing icons", 0.95f, false),
    SPIN("Spinning icons", 0.96f, false),
    POP("Pop-in buttons", 0.92f, true),
    KEYPRESS("Typewriter key presses", 0.98f, false),
}

/** True when the user turned animations off (Accessibility > Remove animations, or animator scale 0). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** The system "remove animations" setting, updated while the app is open. */
@Composable
fun systemReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduce by remember(resolver) { mutableStateOf(animationsOff(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = animationsOff(resolver)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

private fun animationsOff(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** The theme's animation style, or [AnimationStyle.NONE] when the user asked for less motion. */
@Composable
fun currentMotion(): AnimationStyle =
    if (LocalReduceMotion.current) AnimationStyle.NONE else LocalSmTheme.current.animation

/** Transform for a themed button or chip; every value rests at scale 1, no drop, fully opaque. */
class ButtonMotion(
    pressState: State<Float>,
    entranceState: State<Float>,
    private val style: AnimationStyle,
    dropState: State<Float>,
) {
    private val press by pressState
    private val enter by entranceState

    /** How far the button sinks while held, in dp (typewriter keys). */
    val dropDp by dropState

    val scale: Float get() = press * (if (style == AnimationStyle.SUBTLE) 1f else 0.85f + 0.15f * enter)
    val alpha: Float get() = enter.coerceIn(0f, 1f)
}

@Composable
fun rememberButtonMotion(pressed: Boolean, style: AnimationStyle): ButtonMotion {
    val scale = animateFloatAsState(
        targetValue = if (pressed) style.pressScale else 1f,
        animationSpec = if (style == AnimationStyle.BOUNCE || style == AnimationStyle.POP) {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            tween(durationMillis = 120)
        },
        label = "pressScale",
    )
    val drop = animateFloatAsState(
        targetValue = if (pressed && style == AnimationStyle.KEYPRESS) 3f else 0f,
        animationSpec = tween(durationMillis = 60),
        label = "pressDrop",
    )
    val entrance = remember(style) { Animatable(if (style.entrance) 0f else 1f) }
    LaunchedEffect(style) {
        if (entrance.value < 1f) {
            entrance.animateTo(
                1f,
                if (style == AnimationStyle.SUBTLE) {
                    tween(durationMillis = 260, easing = FastOutSlowInEasing)
                } else {
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                },
            )
        }
    }
    return remember(style) { ButtonMotion(scale, entrance.asState(), style, drop) }
}

/** Transform for an icon on a themed button. Read the values inside a graphicsLayer. */
class IconMotion {
    val rotation = Animatable(0f)
    val scale = Animatable(1f)
    /** Vertical offset in dp. */
    val offsetY = Animatable(0f)
    val alpha = Animatable(1f)

    suspend fun play(style: AnimationStyle, appearing: Boolean) {
        when (style) {
            AnimationStyle.NONE, AnimationStyle.KEYPRESS -> Unit
            AnimationStyle.SUBTLE -> if (appearing) {
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(durationMillis = 320))
            }
            AnimationStyle.BOUNCE -> {
                offsetY.animateTo(-6f, tween(durationMillis = 120))
                offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
            }
            AnimationStyle.WIGGLE -> rotation.animateTo(
                0f,
                keyframes {
                    durationMillis = 480
                    -14f at 80
                    12f at 180
                    -8f at 280
                    5f at 380
                },
            )
            AnimationStyle.PULSE -> scale.animateTo(
                1f,
                keyframes {
                    durationMillis = 520
                    1.35f at 200
                },
            )
            AnimationStyle.SPIN -> {
                rotation.snapTo(0f)
                rotation.animateTo(360f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
                rotation.snapTo(0f)
            }
            AnimationStyle.POP -> if (appearing) {
                scale.snapTo(0f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            } else {
                scale.animateTo(
                    1f,
                    keyframes {
                        durationMillis = 300
                        1.3f at 120
                    },
                )
            }
        }
    }
}

@Composable
fun rememberIconMotion(pressed: Boolean, style: AnimationStyle): IconMotion {
    val motion = remember { IconMotion() }
    LaunchedEffect(style) { motion.play(style, appearing = true) }
    LaunchedEffect(style, pressed) { if (pressed) motion.play(style, appearing = false) }
    return motion
}
