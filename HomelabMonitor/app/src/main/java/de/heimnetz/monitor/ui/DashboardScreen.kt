package de.heimnetz.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heimnetz.monitor.data.ServiceStatus
import de.heimnetz.monitor.data.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HomeLab Monitor") },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Einstellungen") }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SummaryCard(state) }
                if (!state.configured) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Einrichtung erforderlich", fontWeight = FontWeight.SemiBold)
                                Text("Hinterlege SSH-Zugangsdaten, Immich-API-Key und Pi-hole-Passwort in den Einstellungen.")
                                TextButton(onClick = onSettings) { Text("Zu den Einstellungen") }
                            }
                        }
                    }
                }
                items(state.services, key = { it.id }) { s ->
                    ServiceCard(s) { onOpen(s.id) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: UiState) {
    val online = state.services.count { it.online }
    val withUpdates = state.services.count { it.hasUpdate }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Heimnetzwerk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("$online von ${state.services.size} erreichbar · $withUpdates mit Updates")
            val sub = when {
                state.loading -> "Wird geprüft …"
                state.lastRefresh != null -> "Zuletzt geprüft: ${formatTime(state.lastRefresh)}"
                else -> "Noch nicht geprüft"
            }
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ServiceCard(s: ServiceStatus, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                serviceIcon(s.id), contentDescription = null,
                modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    s.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                s.version?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val err = s.error
                when {
                    err != null -> Text(err, color = ErrRed, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    s.updatesAvailable > 0 -> Text("${s.updatesAvailable} Update(s) verfügbar", color = WarnAmber, fontWeight = FontWeight.Medium)
                    s.hasUpdate -> Text("Neue Version: ${s.latestVersion}", color = WarnAmber, fontWeight = FontWeight.Medium)
                    s.online -> Text("Aktuell", color = OkGreen, style = MaterialTheme.typography.bodySmall)
                }
                if (s.rebootRequired) Text("Neustart erforderlich", color = WarnAmber, style = MaterialTheme.typography.bodySmall)
            }
            HealthIcon(s.health)
        }
    }
}
