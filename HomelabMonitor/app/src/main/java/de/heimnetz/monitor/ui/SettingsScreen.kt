package de.heimnetz.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.heimnetz.monitor.data.AppConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(config: AppConfig, onSave: (AppConfig) -> Unit, onBack: () -> Unit) {
    var c by remember { mutableStateOf(config) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(onClick = { onSave(c) }) { Icon(Icons.Filled.Check, "Speichern") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Ubuntu Server (ThinkCentre)") {
                Field("IP-Adresse / Hostname", c.ubuntuHost) { c = c.copy(ubuntuHost = it) }
                NumberField("SSH-Port", c.ubuntuPort) { c = c.copy(ubuntuPort = it) }
                Field("SSH-Benutzer", c.ubuntuUser) { c = c.copy(ubuntuUser = it) }
                Field("SSH-Passwort (auch für sudo)", c.ubuntuPassword, password = true) { c = c.copy(ubuntuPassword = it) }
                Field("Privater SSH-Key (optional)", c.ubuntuKey, multiline = true) { c = c.copy(ubuntuKey = it) }
            }

            SectionCard("OpenMediaVault 8 (Raspberry Pi)") {
                Field("IP-Adresse / Hostname", c.omvHost) { c = c.copy(omvHost = it) }
                NumberField("SSH-Port", c.omvPort) { c = c.copy(omvPort = it) }
                Field("SSH-Benutzer", c.omvUser) { c = c.copy(omvUser = it) }
                Field("SSH-Passwort (auch für sudo)", c.omvPassword, password = true) { c = c.copy(omvPassword = it) }
                Field("Privater SSH-Key (optional)", c.omvKey, multiline = true) { c = c.copy(omvKey = it) }
                Hint("SSH muss in OMV unter Dienste → SSH aktiviert und der Benutzer in der Gruppe _ssh sein.")
            }

            SectionCard("Immich (Docker)") {
                Field("URL", c.immichUrl) { c = c.copy(immichUrl = it) }
                Field("API-Key (Admin)", c.immichApiKey, password = true) { c = c.copy(immichApiKey = it) }
                Field("Container-Name", c.immichContainer) { c = c.copy(immichContainer = it) }
                Field("docker-compose-Verzeichnis (für Updates)", c.immichComposeDir) { c = c.copy(immichComposeDir = it) }
                Hint("API-Key: Immich → Kontoeinstellungen → API-Schlüssel. Beispiel-Verzeichnis: /opt/immich")
            }

            SectionCard("Pi-hole v6 (Docker)") {
                Field("URL (inkl. Port)", c.piholeUrl) { c = c.copy(piholeUrl = it) }
                Field("Passwort / App-Passwort", c.piholePassword, password = true) { c = c.copy(piholePassword = it) }
                Field("Container-Name", c.piholeContainer) { c = c.copy(piholeContainer = it) }
                Field("docker-compose-Verzeichnis (für Updates)", c.piholeComposeDir) { c = c.copy(piholeComposeDir = it) }
                Hint("App-Passwort: Pi-hole → Settings → Web interface / API → Configure app password.")
            }

            SectionCard("Hintergrundprüfung") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Regelmäßig prüfen & bei Updates benachrichtigen", Modifier.weight(1f))
                    Switch(checked = c.backgroundCheck, onCheckedChange = { c = c.copy(backgroundCheck = it) })
                }
                NumberField("Intervall in Stunden", c.checkIntervalHours) { c = c.copy(checkIntervalHours = it) }
                Hint("Läuft nur im WLAN. Ist kein Gerät erreichbar (z. B. unterwegs), wird nicht benachrichtigt.")
            }

            Button(onClick = { onSave(c) }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    password: Boolean = false,
    multiline: Boolean = false,
    onChange: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        maxLines = if (multiline) 6 else 1,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri),
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Anzeigen")
                }
            }
        } else null,
        textStyle = if (multiline) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        else LocalTextStyle.current,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { t -> onChange(t.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
