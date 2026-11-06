package com.phonefarm.desktop

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
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalTime
import java.util.Properties
import java.util.UUID
import kotlin.random.Random

data class DesktopNode(
    val id: String,
    val name: String,
    val url: String,
    val intervalSec: Int,
    val enabled: Boolean,
)

/**
 * The real desktop farm: a jittered scheduler that performs genuine HTTP visits and records
 * status, latency and failures. Nothing here is simulated.
 */
class FarmCore(private val storeFile: File) {

    data class NodeStat(
        val cycles: Long = 0,
        val failures: Long = 0,
        val latencyMs: Long = 0,
        val status: Int = 0,
        val lastAt: Long = 0,
        val error: String? = null,
    )

    data class Snapshot(
        val running: Boolean = false,
        val startedAt: Long = 0,
        val nodes: List<DesktopNode> = emptyList(),
        val stats: Map<String, NodeStat> = emptyMap(),
        val log: List<String> = emptyList(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val nodes = LinkedHashMap<String, DesktopNode>()
    private val stats = HashMap<String, NodeStat>()
    private val due = HashMap<String, Long>()
    private val busy = HashSet<String>()
    private val logLines = ArrayDeque<String>()

    @Volatile
    private var running = false
    private var startedAt = 0L
    private var loop: Job? = null

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    init {
        load()
        publish()
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        startedAt = System.currentTimeMillis()
        nodes.values.forEach { due[it.id] = 0L }
        log("farm started on ${System.getProperty("os.name")}")
        loop = scope.launch { schedule() }
        publish()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        loop?.cancel()
        loop = null
        busy.clear()
        log("farm stopped")
        publish()
    }

    @Synchronized
    fun addNode(rawName: String, rawUrl: String, intervalSec: Int): DesktopNode {
        val url = normalizeUrl(rawUrl)
        require(url.isNotEmpty()) { "url required" }
        val name = rawName.trim().ifEmpty {
            runCatching { URI.create(url).host ?: url }.getOrDefault(url)
        }
        val node = DesktopNode(
            id = UUID.randomUUID().toString(),
            name = name.take(60),
            url = url,
            intervalSec = intervalSec.coerceIn(30, 86_400),
            enabled = true,
        )
        nodes[node.id] = node
        due[node.id] = 0L
        log("added ${node.name} → ${node.url}")
        save()
        publish()
        return node
    }

    @Synchronized
    fun removeNode(id: String) {
        val node = nodes.remove(id) ?: return
        stats.remove(id)
        due.remove(id)
        log("removed ${node.name}")
        save()
        publish()
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        val node = nodes[id] ?: return
        nodes[id] = node.copy(enabled = enabled)
        if (enabled) due[id] = 0L else busy.remove(id)
        log("${node.name} ${if (enabled) "enabled" else "paused"}")
        save()
        publish()
    }

    @Synchronized
    fun setInterval(id: String, seconds: Int) {
        val node = nodes[id] ?: return
        nodes[id] = node.copy(intervalSec = seconds.coerceIn(30, 86_400))
        log("${node.name} interval ${seconds.coerceIn(30, 86_400)}s")
        save()
        publish()
    }

    fun runNow(id: String) {
        val node = synchronized(this) { nodes[id] } ?: return
        fire(node, force = true)
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        running = running,
        startedAt = startedAt,
        nodes = nodes.values.toList(),
        stats = HashMap(stats),
        log = logLines.toList(),
    )

    /** Flat, stable JSON contract shared with the Android client and any other tooling. */
    fun statusJson(): String {
        val snap = snapshot()
        val sb = StringBuilder(1024)
        sb.append("{\"ok\":true,\"running\":").append(snap.running)
        sb.append(",\"startedAt\":").append(snap.startedAt)
        sb.append(",\"platform\":\"").append(esc(System.getProperty("os.name"))).append('"')
        sb.append(",\"version\":\"").append(VERSION).append('"')
        sb.append(",\"nodes\":[")
        snap.nodes.forEachIndexed { index, node ->
            if (index > 0) sb.append(',')
            val st = snap.stats[node.id] ?: NodeStat()
            sb.append("{\"id\":\"").append(esc(node.id)).append('"')
            sb.append(",\"name\":\"").append(esc(node.name)).append('"')
            sb.append(",\"url\":\"").append(esc(node.url)).append('"')
            sb.append(",\"intervalSec\":").append(node.intervalSec)
            sb.append(",\"enabled\":").append(node.enabled)
            sb.append(",\"cycles\":").append(st.cycles)
            sb.append(",\"failures\":").append(st.failures)
            sb.append(",\"status\":").append(st.status)
            sb.append(",\"latencyMs\":").append(st.latencyMs)
            sb.append(",\"lastAt\":").append(st.lastAt)
            sb.append(",\"error\":")
            if (st.error == null) sb.append("null") else sb.append('"').append(esc(st.error)).append('"')
            sb.append('}')
        }
        sb.append("],\"log\":[")
        snap.log.takeLast(20).forEachIndexed { index, line ->
            if (index > 0) sb.append(',')
            sb.append('"').append(esc(line)).append('"')
        }
        sb.append("]}")
        return sb.toString()
    }

    private suspend fun schedule() {
        while (running && scope.isActive) {
            val now = System.currentTimeMillis()
            val ready = synchronized(this) {
                nodes.values.filter { it.enabled && (due[it.id] ?: 0L) <= now && !busy.contains(it.id) }
            }
            ready.forEach { fire(it) }
            delay(1_000)
        }
    }

    private fun fire(node: DesktopNode, force: Boolean = false) {
        synchronized(this) {
            if (!running && !force) return
            if (!busy.add(node.id)) return
            due[node.id] = System.currentTimeMillis() + jitterMillis(node.intervalSec)
        }
        scope.launch {
            val startedNs = System.nanoTime()
            try {
                val request = HttpRequest.newBuilder(URI.create(node.url))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build()
                val response = http.send(request, HttpResponse.BodyHandlers.discarding())
                val ms = (System.nanoTime() - startedNs) / 1_000_000
                val ok = response.statusCode() in 200..399
                record(node, response.statusCode(), ms, if (ok) null else "HTTP ${response.statusCode()}", ok)
            } catch (t: Throwable) {
                val ms = (System.nanoTime() - startedNs) / 1_000_000
                record(node, 0, ms, (t.message ?: t.javaClass.simpleName).take(160), false)
            } finally {
                synchronized(this@FarmCore) { busy.remove(node.id) }
                publish()
            }
        }
    }

    @Synchronized
    private fun record(node: DesktopNode, status: Int, ms: Long, error: String?, ok: Boolean) {
        val previous = stats[node.id] ?: NodeStat()
        stats[node.id] = previous.copy(
            cycles = previous.cycles + 1,
            failures = previous.failures + if (ok) 0 else 1,
            latencyMs = ms,
            status = status,
            lastAt = System.currentTimeMillis(),
            error = error,
        )
        val outcome = if (status == 0) (error ?: "error") else status.toString()
        log("${if (ok) "ok  " else "fail"} ${node.name} · $outcome · ${ms}ms")
    }

    @Synchronized
    private fun log(line: String) {
        val now = LocalTime.now()
        val stamp = "%02d:%02d:%02d".format(now.hour, now.minute, now.second)
        logLines.addLast("$stamp  $line")
        while (logLines.size > 200) logLines.removeFirst()
    }

    private fun publish() {
        _state.value = snapshot()
    }

    private fun jitterMillis(intervalSec: Int): Long {
        val base = intervalSec.coerceAtLeast(30) * 1000L
        val factor = 0.9 + Random.nextDouble() * 0.2
        return (base * factor).toLong()
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    @Synchronized
    private fun save() {
        val props = Properties()
        nodes.values.forEachIndexed { index, node ->
            props["n$index.id"] = node.id
            props["n$index.name"] = node.name
            props["n$index.url"] = node.url
            props["n$index.interval"] = node.intervalSec.toString()
            props["n$index.enabled"] = node.enabled.toString()
        }
        props["count"] = nodes.size.toString()
        storeFile.parentFile?.mkdirs()
        val temp = File(storeFile.parentFile, storeFile.name + ".tmp")
        temp.outputStream().use { props.store(it, "Phone Farm Desktop nodes") }
        if (!temp.renameTo(storeFile)) {
            storeFile.delete()
            temp.renameTo(storeFile)
        }
    }

    private fun load() {
        if (!storeFile.exists()) return
        runCatching {
            val props = Properties()
            storeFile.inputStream().use { props.load(it) }
            val count = props.getProperty("count")?.toIntOrNull() ?: 0
            for (index in 0 until count) {
                val id = props.getProperty("n$index.id") ?: continue
                val url = props.getProperty("n$index.url") ?: continue
                nodes[id] = DesktopNode(
                    id = id,
                    name = props.getProperty("n$index.name") ?: url,
                    url = url,
                    intervalSec = props.getProperty("n$index.interval")?.toIntOrNull() ?: 300,
                    enabled = props.getProperty("n$index.enabled")?.toBoolean() ?: true,
                )
            }
        }
    }

    companion object {
        const val VERSION = "1.2.2"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Safari/537.36 PhoneFarmDesktop/$VERSION"

        fun esc(value: String): String {
            val sb = StringBuilder(value.length + 8)
            value.forEach { c ->
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (c.code < 0x20) sb.append("\\u").append("%04x".format(c.code)) else sb.append(c)
                }
            }
            return sb.toString()
        }
    }
}
