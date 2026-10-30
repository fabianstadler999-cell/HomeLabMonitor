package de.heimnetz.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.heimnetz.monitor.data.ServiceAction
import de.heimnetz.monitor.data.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: UiState,
    id: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAction: (ServiceAction) -> Unit,
    onDismissOutput: () -> Unit,
) {
    val s = state.services.firstOrNull { it.id == id } ?: return
    var pending by remember { mutableStateOf<ServiceAction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            item {
                SectionCard("Status") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HealthIcon(s.health, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(healthText(s.health), fontWeight = FontWeight.Medium)
                            Text(s.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    s.error?.let { Text(it, color = ErrRed) }
                    s.checkedAt?.let { InfoLine("Geprüft", formatTime(it)) }
                }
            }

            item {
                SectionCard("Versionen") {
                    InfoLine("Installiert", s.version ?: "–")
                    s.latestVersion?.let { InfoLine("Neueste", it) }
                    InfoLine(
                        "Updates",
                        when {
                            !s.online -> "–"
                            s.updatesAvailable > 0 -> "${s.updatesAvailable} verfügbar"
                            s.hasUpdate -> "Neue Version verfügbar"
                            else -> "Keine – alles aktuell"
                        },
                    )
                    if (s.rebootRequired) InfoLine("Neustart", "erforderlich")
                }
            }

            if (s.updateDetails.isNotEmpty()) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    SectionCard("Verfügbare Updates (${s.updateDetails.size})") {
                        val shown = if (expanded) s.updateDetails else s.updateDetails.take(8)
                        shown.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        if (s.updateDetails.size > 8) {
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Weniger anzeigen" else "Alle ${s.updateDetails.size} anzeigen")
                            }
                        }
                    }
                }
            }

            if (s.info.isNotEmpty()) {
                item { SectionCard("Details") { s.info.forEach { InfoLine(it.label, it.value) } } }
            }

            if (s.containers.isNotEmpty()) {
                item {
                    SectionCard("Docker-Container (${s.containers.size})") {
                        s.containers.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(10.dp)
                                        .background(if (c.running) OkGreen else ErrRed, CircleShape),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(c.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${c.image} · ${c.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val actions = ServiceAction.forService(s.id)
            if (actions.isNotEmpty()) {
                item {
                    SectionCard("Aktionen") {
                        if (state.actionRunning) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Aktion läuft … (Updates können einige Minuten dauern)", style = MaterialTheme.typography.bodySmall)
                        }
                        actions.forEach { a ->
                            OutlinedButton(
                                onClick = { if (a.confirm != null) pending = a else onAction(a) },
                                enabled = !state.actionRunning,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(a.label) }
                        }
                        if (actions.any { it.viaSsh }) {
                            Text(
                                "SSH-Aktionen benötigen sudo-Rechte bzw. Docker-Rechte des SSH-Benutzers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    pending?.let { a ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(a.label) },
            text = { Text(a.confirm.orEmpty()) },
            confirmButton = { TextButton(onClick = { pending = null; onAction(a) }) { Text("Ausführen") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Abbrechen") } },
        )
    }

    state.actionOutput?.let { out ->
        AlertDialog(
            onDismissRequest = onDismissOutput,
            title = { Text(state.actionTitle ?: "Ergebnis") },
            text = {
                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(out, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = onDismissOutput) { Text("OK") } },
        )
    }
}
