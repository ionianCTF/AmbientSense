package com.ambientsense.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Human readable formatting helpers. */

fun formatClock(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

fun formatTimeShort(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m)
    else if (m > 0) String.format(Locale.US, "%dm %02ds", m, sec)
    else String.format(Locale.US, "%ds", sec)
}

fun formatAgo(nowMs: Long, thenMs: Long): String {
    val d = (nowMs - thenMs).coerceAtLeast(0)
    return when {
        d < 5_000 -> "now"
        d < 60_000 -> "${d / 1000}s ago"
        d < 3_600_000 -> "${d / 60_000}m ago"
        else -> "${d / 3_600_000}h ago"
    }
}

fun formatMbps(value: Float?): String = when {
    value == null -> "—"
    value >= 100 -> String.format(Locale.US, "%.0f", value)
    value >= 10 -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.2f", value)
}

fun formatMs(value: Float?): String = when {
    value == null -> "—"
    value >= 100 -> String.format(Locale.US, "%.0f", value)
    else -> String.format(Locale.US, "%.1f", value)
}

fun formatDb(value: Float?): String =
    if (value == null) "—" else String.format(Locale.US, "%.1f", value)

fun formatCount(value: Float): String = when {
    value < 1f -> String.format(Locale.US, "%.1f", value)
    value < 10f -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.0f", value)
}

fun formatMeters(value: Float): String =
    if (value < 10f) String.format(Locale.US, "%.1f m", value)
    else String.format(Locale.US, "%.0f m", value)
