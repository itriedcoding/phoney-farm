package com.phonefarm.app.engine

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.phonefarm.app.BuildConfig
import com.phonefarm.app.ai.AiCopilot
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.data.FarmState
import com.phonefarm.app.data.Governor
import com.phonefarm.app.data.LogEntry
import com.phonefarm.app.data.LogLevel
import com.phonefarm.app.data.NodeMode
import com.phonefarm.app.data.NodeStats
import com.phonefarm.app.data.Profiles
import com.phonefarm.app.data.Proxies
import com.phonefarm.app.data.Settings
import com.phonefarm.app.data.Store
import com.phonefarm.app.service.FarmService
import com.phonefarm.app.update.UpdateManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for the farm. Owns the node registry, the scheduler loop, the statistics
 * and everything that survives a process death.
 */
object FarmEngine {

    private lateinit var app: Application
    private lateinit var store: Store
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(FarmState())
    val state: StateFlow<FarmState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val nodes = ConcurrentHashMap<String, FarmNode>()
    private val stats = ConcurrentHashMap<String, NodeStats>()
    private val nextDue = ConcurrentHashMap<String, Long>()
    private val busy = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var settings = Settings()

    @Volatile
    private var running = false

    @Volatile
    private var startedAt = 0L

    @Volatile
    private var governor = Governor()

    @Volatile
    private var lastAutoUpdate = 0L

    private var ticker: Job? = null
    private var saver: Job? = null

    val isRunning: Boolean get() = running

    fun init(application: Application) {
        if (::app.isInitialized) return
        app = application
        store = Store(application)

        val persisted = store.load()
        persisted.nodes.forEach { nodes[it.id] = it }
        persisted.stats.forEach { (id, value) -> stats[id] = value }
        settings = persisted.settings
        running = persisted.running
        startedAt = persisted.startedAt
        nodes.keys.forEach { stats.getOrPut(it) { NodeStats() } }
        _logs.value = store.readLogs()

        governor = PowerGovernor.read(application, settings)
        publish()
        log(
            LogLevel.INFO,
            "core",
            "Phone Farm ${BuildConfig.VERSION_NAME} ready · ${nodes.size} node(s) on disk",
        )
        if (running) ensureTicker()
    }

    // ---------------------------------------------------------------- node registry

    fun upsert(node: FarmNode) {
        val clean = node.sanitized()
        nodes[clean.id] = clean
        stats.getOrPut(clean.id) { NodeStats() }
        nextDue[clean.id] = SystemClock.elapsedRealtime()
        log(LogLevel.INFO, "node", "saved “${clean.name}” · ${clean.mode.label} · ${clean.intervalSec}s")
        persistSoon()
        publish()
    }

    fun delete(id: String) {
        val removed = nodes.remove(id) ?: return
        stats.remove(id)
        nextDue.remove(id)
        busy.remove(id)
        HttpRunner.forget(id)
        log(LogLevel.WARN, "node", "removed “${removed.name}”")
        persistSoon()
        publish()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val node = nodes[id] ?: return
        nodes[id] = node.copy(enabled = enabled)
        nextDue[id] = SystemClock.elapsedRealtime()
        log(LogLevel.INFO, "node", "${if (enabled) "armed" else "disarmed"} “${node.name}”")
        persistSoon()
        publish()
    }

    fun runNow(id: String) {
        if (nodes[id] == null) return
        nextDue[id] = SystemClock.elapsedRealtime() - 1
        log(LogLevel.INFO, "node", "manual cycle requested for “${nodes[id]?.name}”")
    }

    fun resetStats(id: String) {
        stats[id]?.reset()
        log(LogLevel.INFO, "node", "statistics reset for “${nodes[id]?.name}”")
        persistSoon()
        publish()
    }

    // ---------------------------------------------------------------- farm control

    fun startFarm(origin: String = "app") {
        if (running) {
            runCatching {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, FarmService::class.java).setAction(FarmService.ACTION_START),
                )
            }
            return
        }
        running = true
        startedAt = System.currentTimeMillis()
        val now = SystemClock.elapsedRealtime()
        nodes.keys.forEach { nextDue[it] = now + Random.nextLong(0L, 5_000L) }
        log(
            LogLevel.INFO,
            "core",
            "farm armed via $origin · ${nodes.values.count { it.enabled }} node(s)",
        )
        runCatching {
            ContextCompat.startForegroundService(
                app,
                Intent(app, FarmService::class.java).setAction(FarmService.ACTION_START),
            )
        }
        ensureTicker()
        persistSoon()
        publish()
    }

    fun stopFarm(origin: String = "app") {
        val wasRunning = running
        running = false
        startedAt = 0L
        busy.clear()
        if (wasRunning) log(LogLevel.INFO, "core", "farm stopped via $origin")
        runCatching { app.stopService(Intent(app, FarmService::class.java)) }
        persistSoon()
        publish()
    }

    fun updateSettings(next: Settings) {
        val before = settings
        settings = next
        if (before.powerAware != next.powerAware ||
            before.batteryFloor != next.batteryFloor ||
            before.maxConcurrent != next.maxConcurrent
        ) {
            log(
                LogLevel.INFO,
                "settings",
                "governor · cap ${next.maxConcurrent} · floor ${next.batteryFloor}% · ${if (next.powerAware) "power aware" else "full power"}",
            )
        }
        if (before.autoUpdate != next.autoUpdate) {
            log(LogLevel.INFO, "settings", "auto update ${if (next.autoUpdate) "enabled" else "disabled"}")
        }
        persistSoon()
        publish()
    }

    fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                runCatching { tick() }.onFailure {
                    log(LogLevel.ERROR, "core", "scheduler fault · ${it.message ?: it.javaClass.simpleName}")
                }
                delay(1_000L)
                publish()
            }
        }
    }

    // ---------------------------------------------------------------- scheduler

    private fun tick() {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        governor = PowerGovernor.read(app, settings)
        val state = governor

        if (settings.powerAware && !state.charging && state.batteryPct in 1..3) {
            log(LogLevel.WARN, "power", "battery critical (${state.batteryPct}%) · farm stopped")
            stopFarm("battery guard")
            return
        }

        val cap = if (state.throttled) 1 else settings.maxConcurrent.coerceIn(1, 8)
        val multiplier = if (state.throttled) 3 else 1

        nodes.values
            .filter { it.enabled }
            .sortedBy { nextDue[it.id] ?: 0L }
            .forEach { node ->
                if (busy.size >= cap) return@forEach
                if (node.id in busy) return@forEach
                val dueAt = nextDue[node.id]
                if (dueAt == null) {
                    nextDue[node.id] = now
                    return@forEach
                }
                if (now < dueAt) return@forEach
                dispatch(node, multiplier)
            }

        autoUpdateTick()
        runCatching { AiCopilot.superviseIfDue(app) }
    }

    private fun dispatch(node: FarmNode, multiplier: Int) {
        busy.add(node.id)
        publish()
        scope.launch {
            val profile = Profiles.find(node.profileId)
            val proxy = Proxies.find(profile.proxyId)
            val started = SystemClock.elapsedRealtime()
            val result = try {
                when (node.mode) {
                    NodeMode.HTTP -> HttpRunner.visit(node, profile, proxy)
                    NodeMode.MONITOR -> HttpRunner.probe(node, profile, proxy)
                    NodeMode.WEBVIEW -> withContext(Dispatchers.Main.immediate) { WebRunner.render(app, node) }
                }
            } catch (t: Throwable) {
                RunResult(false, 0, 0, t.message ?: t.javaClass.simpleName)
            }
            val latency = SystemClock.elapsedRealtime() - started

            val nodeStats = stats.getOrPut(node.id) { NodeStats() }
            nodeStats.record(latency, result.bytes, result.status, result.error)
            nodeStats.addOnline(latency)

            val failures = nodeStats.consecutiveFailures
            val backoff = if (failures > 0) min(8, 1 shl min(failures, 3)) else 1
            val base = node.intervalSec.toLong() * 1_000L * multiplier * backoff
            val jitter = if (node.jitterSec > 0) Random.nextLong(0L, node.jitterSec * 1_000L) else 0L
            nextDue[node.id] = SystemClock.elapsedRealtime() + base + jitter
            busy.remove(node.id)

            if (result.error != null && (failures == 1 || failures % 10 == 0)) {
                val suffix = if (result.status > 0) " · ${result.status}" else ""
                log(LogLevel.WARN, node.name, "${result.error}$suffix")
            } else if (result.error == null && nodeStats.cycles % 25L == 1L) {
                log(LogLevel.INFO, node.name, "cycle ${nodeStats.cycles} ok · ${latency}ms")
            }

            persistSoon()
            publish()
        }
    }

    private fun autoUpdateTick() {
        if (!settings.autoUpdate) return
        val now = System.currentTimeMillis()
        if (now - lastAutoUpdate < 6L * 60L * 60L * 1_000L) return
        lastAutoUpdate = now
        runCatching { UpdateManager.checkForUpdates(manual = false) }
    }

    // ---------------------------------------------------------------- config transfer

    fun exportConfig(): String {
        val root = JSONObject()
        root.put("app", "Phone Farm")
        root.put("schema", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("nodes", JSONArray().apply {
            nodes.values.sortedBy { it.createdAt }.forEach { node ->
                put(JSONObject(node.toJson().toString()).apply { remove("id") })
            }
        })
        root.put("settings", settings.toJson())
        return root.toString(2)
    }

    fun importConfig(raw: String): Int {
        val root = JSONObject(raw)
        val array = root.optJSONArray("nodes") ?: throw IllegalArgumentException("no nodes array")

        var added = 0
        for (i in 0 until array.length()) {
            val source = array.optJSONObject(i) ?: continue
            val url = source.optString("url").trim()
            if (url.isEmpty()) continue
            if (nodes.values.any { it.url.equals(url, ignoreCase = true) }) continue
            val copy = JSONObject(source.toString()).apply { remove("id") }
            val node = FarmNode.fromJson(copy).sanitized()
            nodes[node.id] = node
            stats[node.id] = NodeStats()
            added++
        }

        root.optJSONObject("settings")?.let { settings = Settings.fromJson(it) }
        log(LogLevel.INFO, "config", "imported $added node(s)")
        persistSoon()
        publish()
        return added
    }

    // ---------------------------------------------------------------- logs + state

    fun log(level: LogLevel, source: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, source, message)
        _logs.value = (_logs.value + entry).takeLast(400)
        if (::store.isInitialized) store.appendLog(entry)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        if (::store.isInitialized) store.clearLogs()
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val snapshots = HashMap<String, com.phonefarm.app.data.NodeStatsView>(nodes.size)
        nodes.values.forEach { node ->
            snapshots[node.id] = stats.getOrPut(node.id) { NodeStats() }.snapshot()
        }
        val next = HashMap<String, Long>(nodes.size)
        nodes.values.forEach { node ->
            next[node.id] = ((nextDue[node.id] ?: now) - now).coerceAtLeast(0L)
        }
        _state.value = FarmState(
            running = running,
            nodes = nodes.values.sortedBy { it.createdAt },
            stats = snapshots,
            settings = settings,
            governor = governor,
            startedAt = startedAt,
            busy = busy.toSet(),
            nextRunInMs = next,
        )
    }

    private fun persistSoon() {
        saver?.cancel()
        saver = scope.launch {
            delay(1_200L)
            persist()
        }
    }

    fun persist() {
        if (!::store.isInitialized) return
        runCatching {
            store.save(nodes.values.toList(), settings, stats, running, startedAt)
        }
    }
}
