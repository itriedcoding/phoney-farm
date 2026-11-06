package com.phonefarm.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(context: Context) {

    private val dir = File(context.filesDir, "farm")
    private val stateFile = File(dir, "state.json")
    private val logFile = File(dir, "activity.log")
    private val lock = Any()

    init {
        runCatching { dir.mkdirs() }
    }

    fun load(): PersistedState = synchronized(lock) {
        val raw = runCatching { stateFile.readText() }.getOrNull() ?: return PersistedState()
        runCatching {
            val root = JSONObject(raw)
            val nodes = (root.optJSONArray("nodes") ?: JSONArray()).toObjectList().map { FarmNode.fromJson(it) }
            val statsJson = root.optObject("stats") ?: JSONObject()
            val stats = HashMap<String, NodeStats>()
            statsJson.keys().forEach { key ->
                stats[key] = NodeStats.fromJson(statsJson.optObject(key))
            }
            PersistedState(
                nodes = nodes,
                settings = Settings.fromJson(root.optObject("settings")),
                stats = stats,
                running = root.optBoolean("running", false),
                startedAt = root.optLong("startedAt", 0L),
            )
        }.getOrElse { PersistedState() }
    }

    fun save(
        nodes: List<FarmNode>,
        settings: Settings,
        stats: Map<String, NodeStats>,
        running: Boolean,
        startedAt: Long,
    ) = synchronized(lock) {
        runCatching {
            dir.mkdirs()
            val root = JSONObject()
            root.put("schema", 1)
            root.put("nodes", JSONArray().apply { nodes.forEach { put(it.toJson()) } })
            root.put("settings", settings.toJson())
            root.put("stats", JSONObject().apply { stats.forEach { (key, value) -> put(key, value.toJson()) } })
            root.put("running", running)
            root.put("startedAt", startedAt)
            val tmp = File(dir, "state.json.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(stateFile)) {
                stateFile.delete()
                tmp.renameTo(stateFile)
            }
        }
    }

    fun appendLog(entry: LogEntry) = synchronized(lock) {
        runCatching {
            dir.mkdirs()
            if (logFile.length() > 384 * 1024) {
                val kept = logFile.readLines().takeLast(800)
                logFile.writeText(kept.joinToString("\n") + "\n")
            }
            logFile.appendText(entry.encode() + "\n")
        }
    }

    fun readLogs(limit: Int = 300): List<LogEntry> = synchronized(lock) {
        runCatching {
            logFile.readLines().takeLast(limit).mapNotNull { LogEntry.decode(it) }
        }.getOrDefault(emptyList())
    }

    fun clearLogs() {
        synchronized(lock) { runCatching { logFile.delete() } }
    }
}
