package de.heimnetz.monitor.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmt1(d: Double): String = String.format(Locale.GERMANY, "%.1f", d)

fun formatTime(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(ms))

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var v = bytes.toDouble()
    var i = -1
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "${fmt1(v)} ${units[i]}"
}
