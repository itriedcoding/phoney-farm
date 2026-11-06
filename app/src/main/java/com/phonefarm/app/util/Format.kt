package com.phonefarm.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {

    fun uptime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun every(sec: Int): String = when {
        sec < 60 -> "every ${sec}s"
        sec < 3600 -> "every ${sec / 60}m"
        else -> "every ${sec / 3600}h"
    }

    fun countdown(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return when {
            sec < 60 -> "${sec}s"
            sec < 3600 -> "${sec / 60}m"
            else -> "${sec / 3600}h"
        }
    }

    fun bytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        else -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
    }

    fun clock(ts: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ts))

    fun dateStamp(ts: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(ts))
}
