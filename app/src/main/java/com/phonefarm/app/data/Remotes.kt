package com.phonefarm.app.data

import android.app.Application
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RemoteHost(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Desktop",
    val baseUrl: String = "http://192.168.1.20:8787",
    val token: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("token", token)
    }

    companion object {
        fun fromJson(o: JSONObject) = RemoteHost(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", "Desktop"),
            baseUrl = o.optString("baseUrl"),
            token = o.optString("token"),
        )
    }
}

/** Persisted list of Phone Farm Desktop agents this phone can drive over the LAN. */
object Remotes {

    private val _hosts = MutableStateFlow<List<RemoteHost>>(emptyList())
    val hosts: StateFlow<List<RemoteHost>> = _hosts.asStateFlow()

    private lateinit var file: File

    fun init(application: Application) {
        if (::file.isInitialized) return
        val dir = File(application.filesDir, "farm").apply { mkdirs() }
        file = File(dir, "remotes.json")
        _hosts.value = runCatching {
            val raw = file.readText()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { RemoteHost.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun add(host: RemoteHost) {
        _hosts.value = (_hosts.value + host).distinctBy { it.baseUrl }
        persist()
    }

    fun remove(id: String) {
        _hosts.value = _hosts.value.filterNot { it.id == id }
        persist()
    }

    fun update(host: RemoteHost) {
        _hosts.value = _hosts.value.map { if (it.id == host.id) host else it }
        persist()
    }

    private fun persist() {
        if (!::file.isInitialized) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JSONArray().apply { _hosts.value.forEach { put(it.toJson()) } }.toString())
        }
    }
}
