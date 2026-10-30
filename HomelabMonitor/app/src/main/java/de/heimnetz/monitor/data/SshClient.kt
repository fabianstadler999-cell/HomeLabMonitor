package de.heimnetz.monitor.data

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.io.Closeable

data class SshTarget(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val privateKey: String,
)

data class CmdResult(val exitCode: Int, val stdout: String, val stderr: String)

/** Setzt einen String sicher in einfache Anführungszeichen für die Shell. */
fun shellQuote(s: String): String = "'" + s.replace("'", "'\"'\"'") + "'"

class SshSession private constructor(
    private val session: Session,
    private val target: SshTarget,
) : Closeable {

    companion object {
        fun open(t: SshTarget, timeoutMs: Int = 8000): SshSession {
            require(t.host.isNotBlank()) { "Keine Adresse konfiguriert" }
            require(t.user.isNotBlank()) { "SSH-Benutzer für ${t.host} nicht konfiguriert (Einstellungen)" }
            val jsch = JSch()
            if (t.privateKey.isNotBlank()) {
                jsch.addIdentity("homelab-key", t.privateKey.trim().toByteArray(), null, null)
            }
            val s = jsch.getSession(t.user, t.host, t.port)
            if (t.password.isNotBlank()) s.setPassword(t.password)
            // Heimnetz: Host-Key wird nicht gegen known_hosts geprüft
            s.setConfig("StrictHostKeyChecking", "no")
            s.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
            s.setServerAliveInterval(15_000)
            s.connect(timeoutMs)
            return SshSession(s, t)
        }
    }

    /** Führt einen Befehl aus und wartet auf das Ende. */
    fun exec(command: String, stdin: String? = null): CmdResult {
        val ch = session.openChannel("exec") as ChannelExec
        ch.setCommand(command)
        val err = ByteArrayOutputStream()
        ch.setErrStream(err)
        val input = ch.inputStream
        val output = ch.outputStream
        ch.connect(10_000)
        try {
            if (stdin != null) {
                output.write(stdin.toByteArray())
                output.flush()
            }
            output.close()
            val stdout = input.readBytes().toString(Charsets.UTF_8)
            var waited = 0
            while (!ch.isClosed && waited < 5_000) {
                Thread.sleep(50); waited += 50
            }
            return CmdResult(ch.exitStatus, stdout, err.toString(Charsets.UTF_8.name()))
        } finally {
            ch.disconnect()
        }
    }

    /**
     * Führt einen Befehl mit sudo aus. Ist ein Passwort hinterlegt, wird es per stdin
     * übergeben (sudo -S), sonst wird NOPASSWD (sudo -n) erwartet.
     */
    fun sudo(command: String): CmdResult =
        if (target.password.isNotBlank()) {
            exec("sudo -S -p '' sh -c ${shellQuote(command)}", stdin = target.password + "\n")
        } else {
            exec("sudo -n sh -c ${shellQuote(command)}")
        }

    /** Docker-Befehl: erst ohne sudo (docker-Gruppe), bei fehlenden Rechten mit sudo. */
    fun docker(command: String): CmdResult {
        val r = exec(command)
        return if (r.exitCode != 0 && r.stderr.contains("permission denied", ignoreCase = true)) sudo(command) else r
    }

    override fun close() {
        session.disconnect()
    }
}
