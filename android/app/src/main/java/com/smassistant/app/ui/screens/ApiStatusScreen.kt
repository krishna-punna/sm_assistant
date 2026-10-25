package com.smassistant.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smassistant.app.data.ApiConnectionCheck
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.ConnectionStatus
import com.smassistant.app.ui.components.PrimaryButton
import com.smassistant.app.ui.components.SmScreen
import com.smassistant.app.ui.theme.SmGreen
import kotlinx.coroutines.launch

/** S15 API Status: tests every API with its current settings (docs/requirements/11-api-status.md). */
@Composable
fun ApiStatusScreen(onBackToHome: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ApiSettingsStore(context) }
    val statuses = remember { mutableStateMapOf<ApiService, ConnectionStatus>() }
    val scope = rememberCoroutineScope()

    fun testAll() {
        ApiService.entries.forEach { service ->
            statuses[service] = ConnectionStatus.Checking
            scope.launch { statuses[service] = ApiConnectionCheck.check(service, store.load(service)) }
        }
    }

    LaunchedEffect(Unit) { testAll() }

    SmScreen(sectionTitle = "API Status", onBackToHome = onBackToHome) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ApiService.entries.forEach { service ->
                StatusCard(service, statuses[service] ?: ConnectionStatus.Checking)
            }
        }
        PrimaryButton(
            "Test again",
            onClick = { testAll() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusCard(service: ApiService, status: ConnectionStatus) {
    val (label, detail, color) = describe(service, status)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (status == ConnectionStatus.Checking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(color, CircleShape)
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(service.displayName, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Text(label, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val Red = Color(0xFFC62828)
private val Amber = Color(0xFFEF8F00)
private val Grey = Color(0xFF757575)

private fun describe(service: ApiService, status: ConnectionStatus): Triple<String, String, Color> =
    when (status) {
        ConnectionStatus.Checking -> Triple("Checking", "Contacting ${service.displayName}…", Grey)
        ConnectionStatus.Connected -> Triple("Connected", "API key is valid", SmGreen)
        ConnectionStatus.NotConfigured -> Triple("Not set up", "Add the API and key in settings", Grey)
        ConnectionStatus.InvalidKey -> Triple("Invalid key", "The API rejected the key", Red)
        ConnectionStatus.NoPermission ->
            Triple("No access", "The key works but lacks permission for this check", Amber)
        is ConnectionStatus.Unreachable -> Triple("Unreachable", status.reason, Red)
        is ConnectionStatus.Error -> Triple("Error", "Unexpected response (HTTP ${status.httpCode})", Red)
    }
