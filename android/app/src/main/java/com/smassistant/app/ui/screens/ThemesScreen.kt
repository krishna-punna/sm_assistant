package com.smassistant.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smassistant.app.ui.components.PrimaryButton
import com.smassistant.app.ui.components.SectionChip
import com.smassistant.app.ui.components.SmButton
import com.smassistant.app.ui.components.SmScreen
import com.smassistant.app.ui.theme.LocalSmTheme
import com.smassistant.app.ui.theme.SmAssistantTheme
import com.smassistant.app.ui.theme.SmIcon
import com.smassistant.app.ui.theme.SmTheme
import com.smassistant.app.ui.theme.SmThemes
import com.smassistant.app.ui.theme.ThemeId

/**
 * Themes: every theme, grouped by [com.smassistant.app.ui.theme.ThemeCategory], drawn as a live
 * preview in its own colours, fonts, buttons, icons and animations.
 * Tap one to select it, then Apply theme to switch the whole app and save the choice.
 */
@Composable
fun ThemesScreen(onApply: (ThemeId) -> Unit, onBackToHome: () -> Unit) {
    val current = LocalSmTheme.current.id
    var selected by rememberSaveable { mutableStateOf(current) }
    SmScreen(sectionTitle = "Themes", onBackToHome = onBackToHome) {
        Text("Pick a look for the app. Each card is a live preview of that theme; tap its buttons to see them move.")
        SmThemes.all.groupBy { it.category }.forEach { (category, themes) ->
            Text(
                category.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp).testTag("themeCategory_${category.name}"),
            )
            themes.forEach { theme ->
                ThemePreviewCard(
                    theme = theme,
                    selected = theme.id == selected,
                    inUse = theme.id == current,
                    onSelect = { selected = theme.id },
                )
            }
        }
        PrimaryButton(
            if (selected == current) "${SmThemes.byId(current).name} is in use" else "Apply ${SmThemes.byId(selected).name}",
            onClick = { onApply(selected) },
            enabled = selected != current,
            modifier = Modifier.testTag("applyTheme"),
        )
    }
}

@Composable
fun ThemePreviewCard(theme: SmTheme, selected: Boolean, inUse: Boolean, onSelect: () -> Unit) {
    // The outer theme draws the selection ring, so it stands out on every preview.
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    SmAssistantTheme(theme) {
        Surface(
            color = theme.colors.background,
            contentColor = theme.colors.onBackground,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(if (selected) 3.dp else 1.dp, ring),
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .testTag("theme_${theme.id.name}"),
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(theme.topBar.container)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "SM Assistant",
                        color = theme.topBar.content,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            theme.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (inUse) SectionChip("✓ In use") else if (selected) SectionChip("Selected")
                    }
                    Text(theme.tagline, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${theme.fontName} · ${theme.buttonStyle.label} · ${theme.iconStyle.label} · ${theme.animation.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.colors.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { SmButton("Menu", theme.menu, onSelect, icon = SmIcon.ASSISTANT) }
                        Box(Modifier.weight(1f)) { SmButton("Save", theme.action, onSelect) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            theme.topBar.container, theme.menu.container, theme.menu.gradientEnd,
                            theme.action.container, theme.chip.container, theme.back.container,
                        ).distinct().forEach { Swatch(it, theme.colors.outline) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, outline: Color) {
    Surface(color = color, shape = CircleShape, border = BorderStroke(1.dp, outline), modifier = Modifier.size(22.dp)) {}
}
