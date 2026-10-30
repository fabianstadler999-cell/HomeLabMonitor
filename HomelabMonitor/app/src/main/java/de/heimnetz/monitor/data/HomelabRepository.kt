package de.heimnetz.monitor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Sammelt Status, Versionen und Updates aller Geräte/Dienste und führt Aktionen aus.
 *  - Ubuntu & OMV: per SSH (Shell-Skript mit Abschnittsmarkern)
 *  - Immich: REST-API (/api/server/...) + GitHub-Releases für die neueste Version
 *  - Pi-hole v6: REST-API (/api/auth, /api/info/version, /api/stats/summary ...)
 */
class HomelabRepository {

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val slowHttp = http.newBuilder().readTimeout(5, TimeUnit.MINUTES).build()

    fun placeholders(cfg: AppConfig): List<ServiceStatus> = listOf(
        ServiceStatus(ServiceIds.UBUNTU, "Ubuntu Server", "Lenovo ThinkCentre · ${cfg.ubuntuHost}"),
        ServiceStatus(ServiceIds.OMV, "OpenMediaVault NAS", "Raspberry Pi · ${cfg.omvHost}"),
        ServiceStatus(ServiceIds.IMMICH, "Immich", "Docker · ${cfg.immichUrl}"),
        ServiceStatus(ServiceIds.PIHOLE, "Pi-hole", "Docker · ${cfg.piholeUrl}"),
    )

    suspend fun collectAll(cfg: AppConfig): List<ServiceStatus> = coroutineScope {
        val base = placeholders(cfg)
        val ubuntu = async(Dispatchers.IO) { collectLinux(base[0], cfg.ubuntuSsh, UBUNTU_EXTRA) }
        val omv = async(Dispatchers.IO) { collectLinux(base[1], cfg.omvSsh, OMV_EXTRA) }
        val immich = async(Dispatchers.IO) { collectImmich(base[2], cfg) }
        val pihole = async(Dispatchers.IO) { collectPihole(base[3], cfg) }
        val u = ubuntu.await()
        listOf(
            u,
            omv.await(),
            attachContainer(immich.await(), u, cfg.immichContainer),
            attachContainer(pihole.await(), u, cfg.piholeContainer),
        )
    }

    // ------------------------------------------------------------------ Linux-Hosts (SSH)

    private fun collectLinux(base: ServiceStatus, target: SshTarget, extra: String): ServiceStatus {
        val now = System.currentTimeMillis()
        return try {
            val out = SshSession.open(target).use { it.exec(BASE_SCRIPT + "\n" + extra).stdout }
            val sec = parseSections(out)
            fun first(key: String): String = sec[key]?.firstOrNull()?.trim().orEmpty()

            val info = mutableListOf<InfoRow>()
            first("HOST").takeIf { it.isNotEmpty() }?.let { info += InfoRow("Hostname", it) }
            first("MODEL").takeIf { it.isNotEmpty() }?.let { info += InfoRow("Hardware", it) }
            info += InfoRow("Betriebssystem", first("OS"))
            info += InfoRow("Kernel", first("KERNEL"))
            info += InfoRow("Laufzeit", first("UPTIME").removePrefix("up "))
            info += InfoRow("Load (1/5/15 min)", first("LOAD"))
            info += InfoRow("RAM", first("MEM"))
            info += InfoRow("Root-Dateisystem", first("DISK"))
            first("TEMP").toDoubleOrNull()?.let {
                info += InfoRow("CPU-Temperatur", String.format(Locale.GERMANY, "%.1f °C", it / 1000.0))
            }
            sec["FS"]?.forEach { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 4) info += InfoRow("Datenträger ${p[0].substringAfterLast('/').take(28)}", "${p[1]} / ${p[2]} (${p[3]})")
            }
            sec["RAID"]?.forEach { info += InfoRow("RAID", it.trim()) }

            val reboot = first("REBOOT") == "yes"
            if (reboot) info += InfoRow("Neustart", "erforderlich")

            val updates = sec["UPGRADABLE"].orEmpty().map(::formatAptLine)
            val containers = sec["DOCKER"].orEmpty().mapNotNull { line ->
                val p = line.split('|')
                if (p.size >= 3) ContainerInfo(p[0].trim(), p[1].trim(), p[2].trim()) else null
            }
            val omvVersion = first("OMV")
            val version = if (omvVersion.isNotEmpty()) "OMV $omvVersion" else first("OS")
            val health = if (updates.isNotEmpty() || reboot) Health.WARNING else Health.OK

            base.copy(
                health = health, online = true, version = version,
                updatesAvailable = updates.size, updateDetails = updates,
                rebootRequired = reboot, info = info, containers = containers,
                error = null, checkedAt = now,
            )
        } catch (e: Exception) {
            base.copy(health = Health.ERROR, online = false, error = e.message ?: e.javaClass.simpleName, checkedAt = now)
        }
    }

    private fun parseSections(out: String): Map<String, List<String>> {
        val map = LinkedHashMap<String, MutableList<String>>()
        var cur: MutableList<String>? = null
        for (line in out.lines()) {
            if (line.startsWith("##")) {
                val list = mutableListOf<String>()
                map[line.removePrefix("##").trim()] = list
                cur = list
            } else if (line.isNotBlank()) {
                cur?.add(line.trimEnd())
            }
        }
        return map
    }

    /** "curl/noble-updates 8.5.0-2ubuntu10.6 amd64 [upgradable from: 8.5.0-2ubuntu10.5]" */
    private fun formatAptLine(line: String): String {
        val name = line.substringBefore('/')
        val newV = line.split(' ').getOrNull(1).orEmpty()
        val oldV = line.substringAfter("from: ", "").substringBefore(']')
        return if (oldV.isNotEmpty()) "$name: $oldV → $newV" else "$name → $newV"
    }

    private fun attachContainer(s: ServiceStatus, host: ServiceStatus, name: String): ServiceStatus {
        if (!host.online || name.isBlank()) return s
        val c = host.containers.firstOrNull { it.name == name }
            ?: host.containers.firstOrNull { it.name.contains(name, ignoreCase = true) }
            ?: return s.copy(info = s.info + InfoRow("Container", "„$name“ nicht gefunden"))
        val info = s.info + listOf(
            InfoRow("Container", c.name),
            InfoRow("Container-Status", c.status),
            InfoRow("Image", c.image),
        )
        return s.copy(info = info, health = if (!c.running) Health.ERROR else s.health)
    }

    // ------------------------------------------------------------------ Immich

    private fun collectImmich(base: ServiceStatus, cfg: AppConfig): ServiceStatus {
        val now = System.currentTimeMillis()
        val url = cfg.immichUrl.trimEnd('/')
        return try {
            val v = getJson("$url/api/server/version")
            val version = "v${v.path("major").long() ?: 0}.${v.path("minor").long() ?: 0}.${v.path("patch").long() ?: 0}"
            val info = mutableListOf(InfoRow("URL", url))

            if (cfg.immichApiKey.isNotBlank()) {
                val h = mapOf("x-api-key" to cfg.immichApiKey.trim())
                try {
                    val st = getJson("$url/api/server/storage", h)
                    val pct = st.path("diskUsagePercentage").dbl()?.let { fmt1(it) } ?: "?"
                    info += InfoRow("Speicher", "${st.path("diskUse").str()} / ${st.path("diskSize").str()} ($pct %)")
                    info += InfoRow("Frei", st.path("diskAvailable").str() ?: "?")
                } catch (e: Exception) {
                    info += InfoRow("Speicher", "nicht verfügbar (${e.message})")
                }
                try {
                    val stats = getJson("$url/api/server/statistics", h)
                    info += InfoRow("Fotos", stats.path("photos").long()?.toString() ?: "?")
                    info += InfoRow("Videos", stats.path("videos").long()?.toString() ?: "?")
                    stats.path("usage").long()?.let { info += InfoRow("Belegt durch Medien", formatBytes(it)) }
                    (stats.path("usageByUser") as? JsonArray)?.let { info += InfoRow("Benutzer", it.size.toString()) }
                } catch (e: Exception) {
                    info += InfoRow("Statistik", "nicht verfügbar (Admin-API-Key nötig)")
                }
            } else {
                info += InfoRow("Hinweis", "API-Key in den Einstellungen hinterlegen für Speicher & Statistik")
            }

            val latest = latestGithubRelease("immich-app/immich")
            val hasUpdate = latest != null && Versions.isNewer(latest, version)
            base.copy(
                health = if (hasUpdate) Health.WARNING else Health.OK,
                online = true, version = version, latestVersion = latest,
                updateDetails = if (hasUpdate) listOf("Immich: $version → $latest") else emptyList(),
                info = info, error = null, checkedAt = now,
            )
        } catch (e: Exception) {
            base.copy(health = Health.ERROR, online = false, error = e.message ?: e.javaClass.simpleName, checkedAt = now)
        }
    }

    private fun latestGithubRelease(repo: String): String? = runCatching {
        getJson("https://api.github.com/repos/$repo/releases/latest").path("tag_name").str()
    }.getOrNull()

    // ------------------------------------------------------------------ Pi-hole v6

    private fun collectPihole(base: ServiceStatus, cfg: AppConfig): ServiceStatus {
        val now = System.currentTimeMillis()
        val url = cfg.piholeUrl.trimEnd('/')
        return try {
            withPihole(url, cfg.piholePassword) { h ->
                val ver = getJson("$url/api/info/version", h).path("version")
                val comps = mutableListOf<String>()
                val updates = mutableListOf<String>()
                for ((key, label) in listOf("core" to "Core", "web" to "Web", "ftl" to "FTL")) {
                    val local = ver.path(key, "local", "version").str() ?: continue
                    val remote = ver.path(key, "remote", "version").str()
                    comps += "$label $local"
                    if (remote != null && Versions.isNewer(remote, local)) updates += "$label: $local → $remote"
                }
                val dLocal = ver.path("docker", "local").str()
                val dRemote = ver.path("docker", "remote").str()
                if (dLocal != null && dRemote != null && Versions.isNewer(dRemote, dLocal)) {
                    updates += "Docker-Image: $dLocal → $dRemote"
                }

                val info = mutableListOf(InfoRow("URL", url))
                dLocal?.let { info += InfoRow("Docker-Tag", it) }

                var blockingOff = false
                runCatching { getJson("$url/api/dns/blocking", h) }.getOrNull()?.let { b ->
                    val raw = b.path("blocking").str()
                    blockingOff = raw == "disabled"
                    info += InfoRow("Blocking", when (raw) {
                        "enabled" -> "aktiv"
                        "disabled" -> "pausiert"
                        else -> raw ?: "?"
                    })
                }
                runCatching { getJson("$url/api/stats/summary", h) }.getOrNull()?.let { s ->
                    val pct = s.path("queries", "percent_blocked").dbl()?.let { fmt1(it) } ?: "?"
                    info += InfoRow("Anfragen gesamt", s.path("queries", "total").long()?.toString() ?: "?")
                    info += InfoRow("Geblockt", "${s.path("queries", "blocked").long() ?: "?"} ($pct %)")
                    info += InfoRow("Aktive Clients", s.path("clients", "active").long()?.toString() ?: "?")
                    info += InfoRow("Domains auf Blocklisten", s.path("gravity", "domains_being_blocked").long()?.toString() ?: "?")
                    s.path("gravity", "last_update").long()?.let { ts ->
                        info += InfoRow("Gravity zuletzt", formatTime(ts * 1000))
                    }
                }

                base.copy(
                    health = if (updates.isNotEmpty() || blockingOff) Health.WARNING else Health.OK,
                    online = true,
                    version = comps.joinToString(" · ").ifEmpty { null },
                    updatesAvailable = updates.size, updateDetails = updates,
                    info = info, error = null, checkedAt = now,
                )
            }
        } catch (e: Exception) {
            base.copy(health = Health.ERROR, online = false, error = e.message ?: e.javaClass.simpleName, checkedAt = now)
        }
    }

    /** Login -> Aktion -> Logout (Pi-hole hat nur eine begrenzte Anzahl API-Sessions). */
    private fun <T> withPihole(url: String, password: String, block: (Map<String, String>) -> T): T {
        val sid = piholeLogin(url, password)
        val h = if (sid != null) mapOf("X-FTL-SID" to sid) else emptyMap()
        try {
            return block(h)
        } finally {
            if (sid != null) runCatching { requestRaw("DELETE", "$url/api/auth", h, null) }
        }
    }

    private fun piholeLogin(url: String, password: String): String? {
        if (password.isBlank()) return null
        val body = buildJsonObject { put("password", password) }.toString()
        val session = request("POST", "$url/api/auth", emptyMap(), body).path("session")
        if (session.path("valid").bool() != true) throw IOException("Pi-hole-Login fehlgeschlagen")
        return session.path("sid").str()
    }

    private fun piholeBlocking(cfg: AppConfig, enable: Boolean, timerSec: Int?): String {
        val url = cfg.piholeUrl.trimEnd('/')
        return withPihole(url, cfg.piholePassword) { h ->
            val body = buildJsonObject {
                put("blocking", enable)
                if (timerSec != null) put("timer", timerSec) else put("timer", JsonNull)
            }.toString()
            val r = request("POST", "$url/api/dns/blocking", h, body)
            val timer = r.path("timer").long()
            "Blocking: ${r.path("blocking").str()}" + (if (timer != null) " (noch $timer s)" else "")
        }
    }

    // ------------------------------------------------------------------ Aktionen

    suspend fun runAction(cfg: AppConfig, serviceId: String, action: ServiceAction): String =
        withContext(Dispatchers.IO) {
            try {
                when (action) {
                    ServiceAction.PIHOLE_PAUSE -> piholeBlocking(cfg, false, 300)
                    ServiceAction.PIHOLE_ENABLE -> piholeBlocking(cfg, true, null)
                    ServiceAction.PIHOLE_GRAVITY -> {
                        val url = cfg.piholeUrl.trimEnd('/')
                        withPihole(url, cfg.piholePassword) { h ->
                            requestRaw("POST", "$url/api/action/gravity", h, "", slow = true)
                        }.takeLast(6000).ifBlank { "Gravity-Update gestartet." }
                    }
                    else -> {
                        val target = if (serviceId == ServiceIds.OMV) cfg.omvSsh else cfg.ubuntuSsh
                        SshSession.open(target).use { ssh -> formatResult(sshAction(ssh, cfg, serviceId, action)) }
                    }
                }
            } catch (e: Exception) {
                "Fehler: ${e.message ?: e.javaClass.simpleName}"
            }
        }

    private fun sshAction(ssh: SshSession, cfg: AppConfig, id: String, action: ServiceAction): CmdResult =
        when (action) {
            ServiceAction.APT_UPDATE -> ssh.sudo("apt-get update")
            ServiceAction.APT_UPGRADE -> ssh.sudo(
                "DEBIAN_FRONTEND=noninteractive apt-get -y " +
                    "-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold " +
                    "upgrade --with-new-pkgs"
            )
            ServiceAction.OMV_UPGRADE -> ssh.sudo("omv-upgrade")
            ServiceAction.REBOOT -> ssh.sudo("nohup sh -c 'sleep 2; systemctl reboot' >/dev/null 2>&1 &")
            ServiceAction.DOCKER_UPDATE -> {
                val dir = if (id == ServiceIds.IMMICH) cfg.immichComposeDir else cfg.piholeComposeDir
                require(dir.isNotBlank()) { "Bitte das docker-compose-Verzeichnis in den Einstellungen hinterlegen." }
                ssh.docker("cd ${shellQuote(dir.trim())} && docker compose pull && docker compose up -d")
            }
            ServiceAction.DOCKER_RESTART -> {
                val name = if (id == ServiceIds.IMMICH) cfg.immichContainer else cfg.piholeContainer
                ssh.docker("docker restart ${shellQuote(name.trim())}")
            }
            else -> throw IllegalArgumentException("Aktion wird nicht unterstützt")
        }

    private fun formatResult(r: CmdResult): String = buildString {
        append(if (r.exitCode == 0) "✔ Erfolgreich" else "✖ Exit-Code ${r.exitCode}")
        append("\n\n")
        append(r.stdout.trim())
        if (r.stderr.isNotBlank()) {
            append("\n\n--- stderr ---\n")
            append(r.stderr.trim())
        }
    }.takeLast(8000)

    // ------------------------------------------------------------------ HTTP

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        request("GET", url, headers, null)

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?): JsonElement {
        val text = requestRaw(method, url, headers, body)
        return if (text.isBlank()) JsonNull else Json.parseToJsonElement(text)
    }

    private fun requestRaw(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        slow: Boolean = false,
    ): String {
        val b = Request.Builder().url(url).header("Accept", "application/json")
        headers.forEach { (k, v) -> b.header(k, v) }
        val rb = body?.toRequestBody(JSON_MEDIA)
        when (method) {
            "GET" -> b.get()
            "DELETE" -> b.delete(rb)
            else -> b.method(method, rb ?: "".toRequestBody(JSON_MEDIA))
        }
        (if (slow) slowHttp else http).newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val hint = when (resp.code) {
                    401, 403 -> " – Zugangsdaten/API-Key prüfen"
                    404 -> " – Endpunkt nicht gefunden (URL/Version prüfen)"
                    else -> ""
                }
                throw IOException("HTTP ${resp.code}$hint")
            }
            return text
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Läuft auf beiden Linux-Hosts. LC_ALL=C sorgt für englische, parsebare Ausgaben. */
        private val BASE_SCRIPT = """
            export LC_ALL=C
            echo '##HOST'; hostname
            echo '##OS'; grep '^PRETTY_NAME=' /etc/os-release | cut -d= -f2- | tr -d '"'
            echo '##KERNEL'; uname -r
            echo '##UPTIME'; uptime -p
            echo '##LOAD'; cut -d' ' -f1-3 /proc/loadavg
            echo '##MEM'; free -m | awk '/^Mem:/{printf "%d / %d MB (%.0f %%)\n", $3, $2, $3*100/$2}'
            echo '##DISK'; df -h / | awk 'NR==2{print $3" / "$2" ("$5")"}'
            echo '##TEMP'; cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null
            echo '##REBOOT'; if [ -f /var/run/reboot-required ]; then echo yes; else echo no; fi
            echo '##UPGRADABLE'; apt list --upgradable 2>/dev/null | grep '/'
        """.trimIndent()

        /** Zusätzlich auf dem Ubuntu-Server: Docker-Container (Immich, Pi-hole, ...). */
        private val UBUNTU_EXTRA = """
            echo '##DOCKER'; docker ps -a --format '{{.Names}}|{{.Image}}|{{.Status}}' 2>/dev/null || sudo -n docker ps -a --format '{{.Names}}|{{.Image}}|{{.Status}}' 2>/dev/null
        """.trimIndent()

        /** Zusätzlich auf dem Raspberry Pi: OMV-Version, Modell, Datenträger, RAID. */
        private val OMV_EXTRA = """
            echo '##OMV'; dpkg-query -W -f='${'$'}{Version}' openmediavault 2>/dev/null; echo
            echo '##MODEL'; tr -d '\0' < /proc/device-tree/model 2>/dev/null; echo
            echo '##FS'; df -h --output=target,used,size,pcent 2>/dev/null | awk 'NR>1 && $1 ~ /^\/srv\//'
            echo '##RAID'; grep -E '^md' /proc/mdstat 2>/dev/null
        """.trimIndent()
    }
}
