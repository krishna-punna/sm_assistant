package com.smassistant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smassistant.app.ui.theme.SmAmber
import com.smassistant.app.ui.theme.SmBlue
import com.smassistant.app.ui.theme.SmGreen

/**
 * Common screen frame: blue "SM Assistant" app bar, optional yellow section chip and a
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
                    containerColor = SmBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
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
    Surface(color = SmAmber, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = text,
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun SmButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(label)
    }
}

/** Full-width blue button used for menus (Home, AI Agent list, hub). */
@Composable
fun MenuButton(label: String, onClick: () -> Unit) = SmButton(label, SmBlue, onClick)

/** Full-width green button for commit actions (SAVE, Submit). */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    SmButton(label, SmGreen, onClick, modifier, enabled)
