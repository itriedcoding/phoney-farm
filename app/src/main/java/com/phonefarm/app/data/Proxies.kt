package com.phonefarm.app.data

import android.app.Application
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ProxyKind(val label: String, val defaultPort: Int) {
    SOCKS5("SOCKS5", 1080),
    HTTP("HTTP", 8080),
}

/**
 * A user-supplied proxy. SOCKS5 entries are tunnelled by [com.phonefarm.app.engine.net.Socks5] with
 * the real handshake, including RFC 1929 username/password auth.
 */
data class ProxyEntry(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val kind: ProxyKind = ProxyKind.SOCKS5,
    val host: String = "",
    val port: Int = ProxyKind.SOCKS5.defaultPort,
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true,
) {
    val display: String get() = "${kind.label} ${host}:$port"
    val title: String get() = label.ifBlank { display }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("kind", kind.name)
        put("host", host)
        put("port", port)
        put("username", username)
        put("password", password)
        put("enabled", enabled)
    }

    fun sanitized(): ProxyEntry = copy(
        host = host.trim(),
        port = port.coerceIn(1, 65_535),
        username = username.trim(),
        label = label.trim().take(40),
    )

    companion object {
        val EMPTY = ProxyEntry(label = "SOCKS5")

        fun fromJson(o: JSONObject) = ProxyEntry(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            label = o.optString("label"),
            kind = runCatching { ProxyKind.valueOf(o.optString("kind", "SOCKS5")) }.getOrDefault(ProxyKind.SOCKS5),
            host = o.optString("host"),
            port = o.optInt("port", ProxyKind.SOCKS5.defaultPort),
            username = o.optString("username"),
            password = o.optString("password"),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

/** Persisted list of SOCKS5 / HTTP proxies stored on this device only. */
object Proxies {

    private val _entries = MutableStateFlow<List<ProxyEntry>>(emptyList())
    val entries: StateFlow<List<ProxyEntry>> = _entries.asStateFlow()

    private lateinit var file: File

    fun init(application: Application) {
        if (::file.isInitialized) return
        val dir = File(application.filesDir, "farm").apply { mkdirs() }
        file = File(dir, "proxies.json")
        _entries.value = runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { ProxyEntry.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun find(id: String): ProxyEntry? {
        if (id.isBlank()) return null
        return _entries.value.firstOrNull { it.id == id && it.enabled && it.host.isNotBlank() }
    }

    fun add(entry: ProxyEntry): ProxyEntry {
        val clean = entry.sanitized()
        _entries.value = (_entries.value + clean).distinctBy { "${it.host}:${it.port}" }
        persist()
        return clean
    }

    fun update(entry: ProxyEntry) {
        _entries.value = _entries.value.map { if (it.id == entry.id) entry.sanitized() else it }
        persist()
    }

    fun remove(id: String) {
        _entries.value = _entries.value.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        if (!::file.isInitialized) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JSONArray().apply { _entries.value.forEach { put(it.toJson()) } }.toString())
        }
    }
}
