package ru.steno.presentation.common.format

import java.text.DateFormat
import java.util.*
import ru.steno.domain.PlaybackSpeed

fun formatClock(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(Locale.ROOT,s/3600,s/60%60,s%60)
}
fun formatTimestamp(ms: Long, withHours: Boolean): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (withHours) "%d:%02d:%02d".format(Locale.ROOT,s/3600,s/60%60,s%60)
    else "%02d:%02d".format(Locale.ROOT,s/60,s%60)
}
fun formatTimeRange(startMs: Long, endMs: Long, withHours: Boolean) =
    formatTimestamp(startMs,withHours) + " - " + formatTimestamp(endMs,withHours)
fun formatDate(epochMs: Long, locale: Locale) = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,locale).format(Date(epochMs))
fun formatFileSize(bytes: Long) = if (bytes >= 1048576) "%.1f МБ".format(Locale.ROOT,bytes/1048576.0) else "${bytes/1024} КБ"
val PlaybackSpeed.label get() = rate.toString().removeSuffix(".0") + "x"
