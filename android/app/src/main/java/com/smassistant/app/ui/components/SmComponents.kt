package com.smassistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smassistant.app.ui.theme.ButtonColors
import com.smassistant.app.ui.theme.ButtonStyle
import com.smassistant.app.ui.theme.LocalSmTheme
import com.smassistant.app.ui.theme.SmIcon
import com.smassistant.app.ui.theme.currentMotion
import com.smassistant.app.ui.theme.rememberButtonMotion
import com.smassistant.app.ui.theme.rememberIconMotion

/**
 * Common screen frame: themed "SM Assistant" app bar, optional yellow section chip and a
 * scrollable body (UI-1, UI-3). When [onBackToHome] is set, the app bar shows a back arrow
 * on its left ("Back to Home" for TalkBack) instead of a footer button, so nothing sits on
 * top of the system navigation bar; the Scaffold pads the body for the status bar, the
 * navigation bar and the keyboard. The system back gesture still goes back one screen.
 * Screens that scroll a part of the body themselves (a chat list) pass [scrollable] = false.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmScreen(
    sectionTitle: String?,
    onBackToHome: (() -> Unit)?,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SM Assistant", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBackToHome != null) {
                        IconButton(onClick = onBackToHome, modifier = Modifier.testTag("backToHome")) {
                            Icon(BackArrow, contentDescription = "Back to Home")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = LocalSmTheme.current.topBar.container,
                    titleContentColor = LocalSmTheme.current.topBar.content,
                    navigationIconContentColor = LocalSmTheme.current.topBar.content,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("screenBody"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sectionTitle != null) SectionChip(sectionTitle)
            content()
        }
    }
}

/** Material "arrow back" (mirrored for right-to-left), drawn here to avoid the icons library. */
private val BackArrow: ImageVector = ImageVector.Builder(
    name = "BackArrow",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = true,
).path(fill = SolidColor(Color.Black)) {
    moveTo(20f, 11f)
    horizontalLineTo(7.83f)
    lineToRelative(5.59f, -5.59f)
    lineTo(12f, 4f)
    lineToRelative(-8f, 8f)
    lineToRelative(8f, 8f)
    lineToRelative(1.41f, -1.41f)
    lineTo(7.83f, 13f)
    horizontalLineTo(20f)
    close()
}.build()

@Composable
fun SectionChip(text: String) {
    val theme = LocalSmTheme.current
    val motion = rememberButtonMotion(pressed = false, style = currentMotion())
    Surface(
        color = theme.chip.container,
        shape = theme.chipShape,
        modifier = Modifier.graphicsLayer {
            scaleX = motion.scale
            scaleY = motion.scale
            alpha = motion.alpha
        },
    ) {
        Text(
            text = text,
            color = theme.chip.content,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * Full-width button drawn in the theme's shape and [ButtonStyle], with [icon] in the theme's
 * icon style (none in Classic). It moves in the theme's [AnimationStyle][com.smassistant.app.ui.theme.AnimationStyle]
 * on presses, and not at all when the system asks for reduced motion.
 */
@Composable
fun SmButton(
    label: String,
    colors: ButtonColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: SmIcon? = null,
) {
    val theme = LocalSmTheme.current
    val shape = theme.buttonShape
    val gradient = theme.buttonStyle == ButtonStyle.GRADIENT && enabled
    val style = currentMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = rememberButtonMotion(pressed, style)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (gradient) Color.Transparent else colors.container,
            contentColor = colors.content,
        ),
        shape = shape,
        border = if (theme.buttonStyle == ButtonStyle.BORDERED) BorderStroke(2.dp, theme.colors.outline) else null,
        elevation = if (theme.buttonStyle == ButtonStyle.ELEVATED) {
            ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
        } else {
            ButtonDefaults.buttonElevation()
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = motion.scale
                scaleY = motion.scale
                alpha = motion.alpha
                translationY = motion.dropDp.dp.toPx()
            }
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (gradient) {
                    Modifier.background(Brush.horizontalGradient(listOf(colors.container, colors.gradientEnd)), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        icon?.vector(theme.iconStyle)?.let {
            val iconMotion = rememberIconMotion(pressed, style)
            Icon(
                it,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        rotationZ = iconMotion.rotation.value
                        scaleX = iconMotion.scale.value
                        scaleY = iconMotion.scale.value
                        translationY = iconMotion.offsetY.value.dp.toPx()
                        alpha = iconMotion.alpha.value
                    },
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(label)
    }
}

/** Full-width menu button (Home, AI Agent list, hub). */
@Composable
fun MenuButton(label: String, onClick: () -> Unit) = MenuButton(label, icon = null, onClick)

/** Menu button with a leading [icon], drawn in the theme's icon style. */
@Composable
fun MenuButton(label: String, icon: SmIcon?, onClick: () -> Unit) =
    SmButton(label, LocalSmTheme.current.menu, onClick, icon = icon)

/** Full-width button for commit actions (SAVE, Submit). */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    SmButton(label, LocalSmTheme.current.action, onClick, modifier, enabled)

/** Full-width "Back to Home" button, for dialogs that cover the app bar (image preview). */
@Composable
fun BackToHomeButton(onClick: () -> Unit) =
    SmButton("Back to Home", LocalSmTheme.current.back, onClick, icon = SmIcon.HOME)
