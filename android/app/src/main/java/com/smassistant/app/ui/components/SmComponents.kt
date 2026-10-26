package com.smassistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smassistant.app.ui.theme.ButtonColors
import com.smassistant.app.ui.theme.ButtonStyle
import com.smassistant.app.ui.theme.LocalSmTheme
import com.smassistant.app.ui.theme.SmIcon

/**
 * Common screen frame: themed "SM Assistant" app bar, optional yellow section chip,
 * scrollable body, and an optional black "Back to Home" footer (UI-1, UI-3). Screens that
 * scroll a part of the body themselves (a chat list) pass [scrollable] = false. The body
 * stays above the keyboard.
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = LocalSmTheme.current.topBar.container,
                    titleContentColor = LocalSmTheme.current.topBar.content,
                ),
            )
        },
        bottomBar = {
            if (onBackToHome != null) {
                Box(Modifier.padding(16.dp)) {
                    BackToHomeButton(onBackToHome)
                }
            }
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

@Composable
fun SectionChip(text: String) {
    val theme = LocalSmTheme.current
    Surface(color = theme.chip.container, shape = theme.chipShape) {
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
 * icon style (none in Classic).
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
    Button(
        onClick = onClick,
        enabled = enabled,
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
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
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

/** "Back to Home" footer button. */
@Composable
fun BackToHomeButton(onClick: () -> Unit) =
    SmButton("Back to Home", LocalSmTheme.current.back, onClick, icon = SmIcon.HOME)
