package de.heimnetz.monitor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class AppConfig(
    // Ubuntu Server (Lenovo ThinkCentre)
    val ubuntuHost: String = "192.168.19.5",
    val ubuntuPort: Int = 22,
    val ubuntuUser: String = "",
    val ubuntuPassword: String = "",
    val ubuntuKey: String = "",
    // Raspberry Pi mit OpenMediaVault 8
    val omvHost: String = "192.168.19.50",
    val omvPort: Int = 22,
    val omvUser: String = "",
    val omvPassword: String = "",
    val omvKey: String = "",
    // Immich (Docker auf Ubuntu)
    val immichUrl: String = "http://192.168.19.5:2283",
    val immichApiKey: String = "",
    val immichContainer: String = "immich_server",
    val immichComposeDir: String = "",
    // Pi-hole v6 (Docker auf Ubuntu)
    val piholeUrl: String = "http://192.168.19.5",
    val piholePassword: String = "",
    val piholeContainer: String = "pihole",
    val piholeComposeDir: String = "",
    // Hintergrundprüfung
    val backgroundCheck: Boolean = true,
    val checkIntervalHours: Int = 6,
) {
    val ubuntuSsh: SshTarget get() = SshTarget(ubuntuHost.trim(), ubuntuPort, ubuntuUser.trim(), ubuntuPassword, ubuntuKey)
    val omvSsh: SshTarget get() = SshTarget(omvHost.trim(), omvPort, omvUser.trim(), omvPassword, omvKey)

    fun sanitized(): AppConfig = copy(
        ubuntuPort = ubuntuPort.takeIf { it in 1..65535 } ?: 22,
        omvPort = omvPort.takeIf { it in 1..65535 } ?: 22,
        checkIntervalHours = checkIntervalHours.coerceIn(1, 168),
        immichUrl = immichUrl.trim().trimEnd('/'),
        piholeUrl = piholeUrl.trim().trimEnd('/'),
    )
}

/** Speichert die Konfiguration inkl. Passwörtern verschlüsselt (Android Keystore). */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = create(context.applicationContext)

    private fun create(ctx: Context): SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "homelab_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Fallback, falls der Keystore defekt ist (selten, z. B. nach Backup-Restore)
        ctx.getSharedPreferences("homelab_fallback", Context.MODE_PRIVATE)
    }

    fun load(): AppConfig {
        val d = AppConfig()
        return AppConfig(
            ubuntuHost = s("ubuntuHost", d.ubuntuHost),
            ubuntuPort = prefs.getInt("ubuntuPort", d.ubuntuPort),
            ubuntuUser = s("ubuntuUser", d.ubuntuUser),
            ubuntuPassword = s("ubuntuPassword", d.ubuntuPassword),
            ubuntuKey = s("ubuntuKey", d.ubuntuKey),
            omvHost = s("omvHost", d.omvHost),
            omvPort = prefs.getInt("omvPort", d.omvPort),
            omvUser = s("omvUser", d.omvUser),
            omvPassword = s("omvPassword", d.omvPassword),
            omvKey = s("omvKey", d.omvKey),
            immichUrl = s("immichUrl", d.immichUrl),
            immichApiKey = s("immichApiKey", d.immichApiKey),
            immichContainer = s("immichContainer", d.immichContainer),
            immichComposeDir = s("immichComposeDir", d.immichComposeDir),
            piholeUrl = s("piholeUrl", d.piholeUrl),
            piholePassword = s("piholePassword", d.piholePassword),
            piholeContainer = s("piholeContainer", d.piholeContainer),
            piholeComposeDir = s("piholeComposeDir", d.piholeComposeDir),
            backgroundCheck = prefs.getBoolean("backgroundCheck", d.backgroundCheck),
            checkIntervalHours = prefs.getInt("checkIntervalHours", d.checkIntervalHours),
        )
    }

    fun save(c: AppConfig) {
        prefs.edit()
            .putString("ubuntuHost", c.ubuntuHost)
            .putInt("ubuntuPort", c.ubuntuPort)
            .putString("ubuntuUser", c.ubuntuUser)
            .putString("ubuntuPassword", c.ubuntuPassword)
            .putString("ubuntuKey", c.ubuntuKey)
            .putString("omvHost", c.omvHost)
            .putInt("omvPort", c.omvPort)
            .putString("omvUser", c.omvUser)
            .putString("omvPassword", c.omvPassword)
            .putString("omvKey", c.omvKey)
            .putString("immichUrl", c.immichUrl)
            .putString("immichApiKey", c.immichApiKey)
            .putString("immichContainer", c.immichContainer)
            .putString("immichComposeDir", c.immichComposeDir)
            .putString("piholeUrl", c.piholeUrl)
            .putString("piholePassword", c.piholePassword)
            .putString("piholeContainer", c.piholeContainer)
            .putString("piholeComposeDir", c.piholeComposeDir)
            .putBoolean("backgroundCheck", c.backgroundCheck)
            .putInt("checkIntervalHours", c.checkIntervalHours)
            .apply()
    }

    private fun s(key: String, def: String): String = prefs.getString(key, def) ?: def
}
