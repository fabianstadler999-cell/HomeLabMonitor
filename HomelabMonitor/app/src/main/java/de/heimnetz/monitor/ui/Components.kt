package de.heimnetz.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.heimnetz.monitor.data.Health
import de.heimnetz.monitor.data.ServiceIds

val OkGreen = Color(0xFF2E7D32)
val WarnAmber = Color(0xFFF9A825)
val ErrRed = Color(0xFFC62828)

@Composable
fun HealthIcon(health: Health, size: Dp = 28.dp) {
    val (icon, tint) = when (health) {
        Health.OK -> Icons.Filled.CheckCircle to OkGreen
        Health.WARNING -> Icons.Filled.Warning to WarnAmber
        Health.ERROR -> Icons.Filled.Error to ErrRed
        Health.UNKNOWN -> Icons.Filled.Schedule to Color.Gray
    }
    Icon(icon, contentDescription = healthText(health), tint = tint, modifier = Modifier.size(size))
}

fun healthText(h: Health): String = when (h) {
    Health.OK -> "Alles in Ordnung"
    Health.WARNING -> "Aufmerksamkeit nötig"
    Health.ERROR -> "Fehler / nicht erreichbar"
    Health.UNKNOWN -> "Noch nicht geprüft"
}

fun serviceIcon(id: String): ImageVector = when (id) {
    ServiceIds.UBUNTU -> Icons.Filled.Dns
    ServiceIds.OMV -> Icons.Filled.Storage
    ServiceIds.IMMICH -> Icons.Filled.PhotoLibrary
    ServiceIds.PIHOLE -> Icons.Filled.Shield
    else -> Icons.Filled.Devices
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label, Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium)
    }
}
