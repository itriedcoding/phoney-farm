package com.phonefarm.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

enum class NodeMode(val label: String, val hint: String) {
    HTTP("LIGHT", "HTTP keep-alive · no WebView"),
    WEBVIEW("SESSION", "WebView session · full page"),
    MONITOR("MONITOR", "Probe only · uptime + latency");

    companion object {
        fun of(value: String?): NodeMode = entries.firstOrNull { it.name == value } ?: HTTP
    }
}

enum class UpdateChannel(val label: String) {
    STABLE("Stable"),
    BETA("Beta"),
}

data class FarmNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    val mode: NodeMode = NodeMode.HTTP,
    val intervalSec: Int = 300,
    val jitterSec: Int = 25,
    val desktopUa: Boolean = false,
    val images: Boolean = true,
    val script: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val host: String
        get() {
            if (url.isBlank()) return "no target"
            return runCatching {
                val parsed = URI(url).host
                if (parsed.isNullOrBlank()) url else parsed.removePrefix("www.")
            }.getOrDefault(url)
        }

    fun sanitized(): FarmNode = copy(
        name = name.trim().ifBlank { host },
        url = url.trim(),
        intervalSec = intervalSec.coerceIn(MIN_INTERVAL, MAX_INTERVAL),
        jitterSec = jitterSec.coerceIn(0, 120),
        script = script.trim(),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("enabled", enabled)
        put("mode", mode.name)
        put("intervalSec", intervalSec)
        put("jitterSec", jitterSec)
        put("desktopUa", desktopUa)
        put("images", images)
        put("script", script)
        put("createdAt", createdAt)
    }

    companion object {
        const val MIN_INTERVAL = 15
        const val MAX_INTERVAL = 3600

        fun fromJson(o: JSONObject) = FarmNode(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            url = o.optString("url"),
            enabled = o.optBoolean("enabled", true),
            mode = NodeMode.of(o.optString("mode")),
            intervalSec = o.optInt("intervalSec", 300),
            jitterSec = o.optInt("jitterSec", 25),
            desktopUa = o.optBoolean("desktopUa", false),
            images = o.optBoolean("images", true),
            script = o.optString("script"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

data class NodeStatsView(
    val cycles: Long = 0,
    val failures: Long = 0,
    val lastCycleAt: Long = 0,
    val lastOkAt: Long = 0,
    val latencyMs: Long = 0,
    val bytes: Long = 0,
    val status: Int = 0,
    val error: String? = null,
    val consecutiveFailures: Int = 0,
    val onlineMs: Long = 0,
    val history: List<Long> = emptyList(),
) {
    val healthy: Boolean get() = error == null && lastOkAt > 0
}

class NodeStats {
    @Volatile var cycles: Long = 0
    @Volatile var failures: Long = 0
    @Volatile var lastCycleAt: Long = 0
    @Volatile var lastOkAt: Long = 0
    @Volatile var latencyMs: Long = 0
    @Volatile var bytes: Long = 0
    @Volatile var status: Int = 0
    @Volatile var error: String? = null
    @Volatile var consecutiveFailures: Int = 0
    @Volatile var onlineMs: Long = 0

    private val history = ArrayDeque<Long>()

    @Synchronized
    fun record(latency: Long, bytesRead: Long, statusCode: Int, errorMessage: String?) {
        val now = System.currentTimeMillis()
        cycles += 1
        lastCycleAt = now
        latencyMs = latency
        bytes += bytesRead
        status = statusCode
        if (errorMessage == null) {
            lastOkAt = now
            error = null
            consecutiveFailures = 0
        } else {
            failures += 1
            error = errorMessage
            consecutiveFailures += 1
        }
        history.addLast(latency)
        while (history.size > 48) history.removeFirst()
    }

    @Synchronized
    fun addOnline(ms: Long) {
        onlineMs += ms
    }

    @Synchronized
    fun reset() {
        cycles = 0
        failures = 0
        lastCycleAt = 0
        lastOkAt = 0
        latencyMs = 0
        bytes = 0
        status = 0
        error = null
        consecutiveFailures = 0
        onlineMs = 0
        history.clear()
    }

    @Synchronized
    fun snapshot(): NodeStatsView = NodeStatsView(
        cycles = cycles,
        failures = failures,
        lastCycleAt = lastCycleAt,
        lastOkAt = lastOkAt,
        latencyMs = latencyMs,
        bytes = bytes,
        status = status,
        error = error,
        consecutiveFailures = consecutiveFailures,
        onlineMs = onlineMs,
        history = history.toList(),
    )

    @Synchronized
    fun toJson(): JSONObject = JSONObject().apply {
        put("cycles", cycles)
        put("failures", failures)
        put("lastCycleAt", lastCycleAt)
        put("lastOkAt", lastOkAt)
        put("latencyMs", latencyMs)
        put("bytes", bytes)
        put("onlineMs", onlineMs)
    }

    companion object {
        fun fromJson(o: JSONObject?): NodeStats {
            val stats = NodeStats()
            if (o != null) {
                stats.cycles = o.optLong("cycles")
                stats.failures = o.optLong("failures")
                stats.lastCycleAt = o.optLong("lastCycleAt")
                stats.lastOkAt = o.optLong("lastOkAt")
                stats.latencyMs = o.optLong("latencyMs")
                stats.bytes = o.optLong("bytes")
                stats.onlineMs = o.optLong("onlineMs")
            }
            return stats
        }
    }
}

data class Settings(
    val resumeOnLaunch: Boolean = true,
    val bootRestore: Boolean = true,
    val maxConcurrent: Int = 3,
    val powerAware: Boolean = true,
    val batteryFloor: Int = 15,
    val wakeLock: Boolean = true,
    val autoUpdate: Boolean = true,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val autoDownload: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("resumeOnLaunch", resumeOnLaunch)
        put("bootRestore", bootRestore)
        put("maxConcurrent", maxConcurrent)
        put("powerAware", powerAware)
        put("batteryFloor", batteryFloor)
        put("wakeLock", wakeLock)
        put("autoUpdate", autoUpdate)
        put("channel", channel.name)
        put("autoDownload", autoDownload)
    }

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            if (o == null) return Settings()
            return Settings(
                resumeOnLaunch = o.optBoolean("resumeOnLaunch", true),
                bootRestore = o.optBoolean("bootRestore", true),
                maxConcurrent = o.optInt("maxConcurrent", 3).coerceIn(1, 8),
                powerAware = o.optBoolean("powerAware", true),
                batteryFloor = o.optInt("batteryFloor", 15).coerceIn(5, 50),
                wakeLock = o.optBoolean("wakeLock", true),
                autoUpdate = o.optBoolean("autoUpdate", true),
                channel = runCatching { UpdateChannel.valueOf(o.optString("channel")) }.getOrDefault(UpdateChannel.STABLE),
                autoDownload = o.optBoolean("autoDownload", true),
            )
        }
    }
}

data class Governor(
    val batteryPct: Int = 100,
    val charging: Boolean = false,
    val thermal: Int = 0,
    val throttled: Boolean = false,
    val reason: String? = null,
) {
    val thermalLabel: String
        get() = when (thermal) {
            0 -> "Nominal"
            1 -> "Light"
            2 -> "Moderate"
            3 -> "Severe"
            4, 5, 6 -> "Critical"
            else -> "Unknown"
        }
}

data class FarmState(
    val running: Boolean = false,
    val nodes: List<FarmNode> = emptyList(),
    val stats: Map<String, NodeStatsView> = emptyMap(),
    val settings: Settings = Settings(),
    val governor: Governor = Governor(),
    val startedAt: Long = 0L,
    val busy: Set<String> = emptySet(),
    val nextRunInMs: Map<String, Long> = emptyMap(),
) {
    val enabledCount: Int get() = nodes.count { it.enabled }
    val totalCycles: Long get() = stats.values.sumOf { it.cycles }
    val totalFailures: Long get() = stats.values.sumOf { it.failures }
    val totalBytes: Long get() = stats.values.sumOf { it.bytes }
    val uptimeMs: Long get() = if (running && startedAt > 0) System.currentTimeMillis() - startedAt else 0L

    fun statsOf(id: String): NodeStatsView = stats[id] ?: NodeStatsView()
}

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val at: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
) {
    fun encode(): String = "$at\t${level.name}\t${source.replace('\t', ' ')}\t${message.replace('\t', ' ').replace('\n', ' ')}"

    companion object {
        fun decode(line: String): LogEntry? {
            val parts = line.split('\t')
            if (parts.size < 4) return null
            val at = parts[0].toLongOrNull() ?: return null
            val level = runCatching { LogLevel.valueOf(parts[1]) }.getOrDefault(LogLevel.INFO)
            return LogEntry(at, level, parts[2], parts.drop(3).joinToString("\t"))
        }
    }
}

data class PersistedState(
    val nodes: List<FarmNode> = emptyList(),
    val settings: Settings = Settings(),
    val stats: Map<String, NodeStats> = emptyMap(),
    val running: Boolean = false,
    val startedAt: Long = 0L,
)

internal fun JSONObject.optObject(key: String): JSONObject? =
    if (has(key) && !isNull(key)) optJSONObject(key) else null

internal fun JSONArray.toObjectList(): List<JSONObject> {
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { out.add(it) }
    }
    return out
}
