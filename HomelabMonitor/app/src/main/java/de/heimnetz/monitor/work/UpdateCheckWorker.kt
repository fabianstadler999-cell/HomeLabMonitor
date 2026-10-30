package de.heimnetz.monitor.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.heimnetz.monitor.MainActivity
import de.heimnetz.monitor.R
import de.heimnetz.monitor.data.AppConfig
import de.heimnetz.monitor.data.HomelabRepository
import de.heimnetz.monitor.data.SettingsRepository
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    private const val NAME = "homelab-update-check"

    fun apply(context: Context, cfg: AppConfig) {
        val wm = WorkManager.getInstance(context)
        if (!cfg.backgroundCheck) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            cfg.checkIntervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS,
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build(),
        ).build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class UpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val cfg = SettingsRepository(applicationContext).load()
        val list = HomelabRepository().collectAll(cfg)

        // Nichts erreichbar -> vermutlich nicht im Heimnetz, keine Meldung
        if (list.none { it.online }) return Result.success()

        val lines = mutableListOf<String>()
        list.filter { it.hasUpdate }.forEach { s ->
            lines += if (s.updatesAvailable > 0) "${s.name}: ${s.updatesAvailable} Update(s)"
            else "${s.name}: Version ${s.latestVersion} verfügbar"
        }
        list.filter { it.rebootRequired }.forEach { lines += "${it.name}: Neustart erforderlich" }
        list.filter { !it.online && it.error?.contains("nicht konfiguriert") != true }
            .forEach { lines += "${it.name}: nicht erreichbar" }

        if (lines.isNotEmpty()) Notifier.show(applicationContext, "HomeLab: ${lines.size} Hinweis(e)", lines)
        return Result.success()
    }
}

object Notifier {
    private const val CHANNEL = "homelab_updates"

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, title: String, lines: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Updates & Status", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }

        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1001, notification)
    }
}
