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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.data.ApiConnectionCheck
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.ConnectionStatus
import com.smassistant.app.status.ApiStatusViewModel
import com.smassistant.app.ui.components.PrimaryButton
import com.smassistant.app.ui.components.SmScreen
import com.smassistant.app.ui.theme.SmGreen

/** S15 API Status: tests every API with its current settings (docs/requirements/11-api-status.md). */
@Composable
fun ApiStatusScreen(onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val store = ApiSettingsStore(appContext)
        val checker = ApiConnectionCheck()
        // Each result is remembered for the warning by the agent dropdowns (GEN-6).
        ApiStatusViewModel(store::load) { service, config -> checker.check(service, config).also { store.saveLastStatus(service, it) } }
    }
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    ApiStatusContent(statuses, onTestAgain = viewModel::testAll, onBackToHome = onBackToHome)
}

@Composable
fun ApiStatusContent(
    statuses: Map<ApiService, ConnectionStatus>,
    onTestAgain: () -> Unit,
    onBackToHome: () -> Unit,
) {
    SmScreen(sectionTitle = "API Status", onBackToHome = onBackToHome, scrollable = false) {
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
            onClick = onTestAgain,
            modifier = Modifier.fillMaxWidth(),
            enabled = statuses.values.none { it == ConnectionStatus.Checking },
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
        ConnectionStatus.NotConfigured -> Triple(
            "Not set up",
            if (service == ApiService.CLOUDFLARE) "Add the account ID and API token in settings" else "Add the API and key in settings",
            Grey,
        )
        ConnectionStatus.InvalidKey -> Triple("Invalid key", "The API rejected the key", Red)
        ConnectionStatus.NoPermission ->
            Triple("No access", "The key works but lacks permission for this check", Amber)
        is ConnectionStatus.Unreachable -> Triple("Unreachable", status.reason, Red)
        is ConnectionStatus.Error -> Triple("Error", "Unexpected response (HTTP ${status.httpCode})", Red)
    }
