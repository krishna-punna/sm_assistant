package com.smassistant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smassistant.app.ui.theme.SmAmber
import com.smassistant.app.ui.theme.SmBlack
import com.smassistant.app.ui.theme.SmBlue
import com.smassistant.app.ui.theme.SmGreen

/**
 * Common screen frame: blue "SM Assistant" app bar, optional yellow section chip,
 * scrollable body, and an optional black "Back to Home" footer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmScreen(
    sectionTitle: String?,
    onBackToHome: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SM Assistant", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SmBlue,
                    titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            if (onBackToHome != null) {
                Box(Modifier.padding(16.dp)) {
                    SmButton("Back to Home", SmBlack, onBackToHome)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sectionTitle != null) SectionChip(sectionTitle)
            content()
        }
    }
}

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
