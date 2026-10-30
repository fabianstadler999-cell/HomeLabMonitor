package de.heimnetz.monitor.data

enum class Health { OK, WARNING, ERROR, UNKNOWN }

data class InfoRow(val label: String, val value: String)

data class ContainerInfo(val name: String, val image: String, val status: String) {
    val running: Boolean get() = status.startsWith("Up")
}

object ServiceIds {
    const val UBUNTU = "ubuntu"
    const val OMV = "omv"
    const val IMMICH = "immich"
    const val PIHOLE = "pihole"
}

data class ServiceStatus(
    val id: String,
    val name: String,
    val subtitle: String,
    val health: Health = Health.UNKNOWN,
    val online: Boolean = false,
    val version: String? = null,
    val latestVersion: String? = null,
    val updatesAvailable: Int = 0,
    val updateDetails: List<String> = emptyList(),
    val rebootRequired: Boolean = false,
    val info: List<InfoRow> = emptyList(),
    val containers: List<ContainerInfo> = emptyList(),
    val error: String? = null,
    val checkedAt: Long? = null,
) {
    val hasUpdate: Boolean
        get() = updatesAvailable > 0 ||
            (latestVersion != null && version != null && Versions.isNewer(latestVersion, version))
}

enum class ServiceAction(val label: String, val confirm: String?, val viaSsh: Boolean) {
    APT_UPDATE("Paketlisten aktualisieren (apt update)", null, true),
    APT_UPGRADE("Updates installieren (apt upgrade)", "Alle verfügbaren Paket-Updates jetzt installieren?", true),
    OMV_UPGRADE("Updates installieren (omv-upgrade)", "Alle OMV- und Paket-Updates jetzt installieren?", true),
    REBOOT("Neu starten", "Gerät wirklich neu starten? Alle Dienste darauf sind kurz nicht erreichbar.", true),
    DOCKER_UPDATE("Container aktualisieren (compose pull + up -d)", "Neue Images ziehen und Container neu erstellen?", true),
    DOCKER_RESTART("Container neu starten", "Container wirklich neu starten?", true),
    PIHOLE_PAUSE("Blocking 5 Minuten pausieren", null, false),
    PIHOLE_ENABLE("Blocking aktivieren", null, false),
    PIHOLE_GRAVITY("Blocklisten aktualisieren (Gravity)", null, false);

    companion object {
        fun forService(id: String): List<ServiceAction> = when (id) {
            ServiceIds.UBUNTU -> listOf(APT_UPDATE, APT_UPGRADE, REBOOT)
            ServiceIds.OMV -> listOf(APT_UPDATE, OMV_UPGRADE, REBOOT)
            ServiceIds.IMMICH -> listOf(DOCKER_UPDATE, DOCKER_RESTART)
            ServiceIds.PIHOLE -> listOf(PIHOLE_PAUSE, PIHOLE_ENABLE, PIHOLE_GRAVITY, DOCKER_UPDATE, DOCKER_RESTART)
            else -> emptyList()
        }
    }
}

object Versions {
    /** true, wenn [candidate] eine höhere Version als [current] ist (z. B. v1.123.0 > v1.122.3). */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        Regex("\\d+").findAll(v.trim().removePrefix("v")).map { it.value.toIntOrNull() ?: 0 }.toList()
}
