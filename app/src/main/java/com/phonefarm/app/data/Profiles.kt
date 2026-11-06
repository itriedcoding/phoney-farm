package com.phonefarm.app.data

import android.app.Application
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A device identity: its own user agent, cookie jar and proxy. Running many profiles from one phone
 * gives you independent "devices" — each with its own session and, optionally, its own exit IP.
 */
data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Device",
    val userAgent: String = Profiles.UA_MOBILE,
    val proxyId: String = "",
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("userAgent", userAgent)
        put("proxyId", proxyId)
        put("enabled", enabled)
    }

    companion object {
        const val PRIMARY = "primary"

        fun fromJson(o: JSONObject) = DeviceProfile(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", "Device"),
            userAgent = o.optString("userAgent", Profiles.UA_MOBILE),
            proxyId = o.optString("proxyId"),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

object Profiles {

    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    const val UA_IPHONE =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"

    private val _profiles = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val profiles: StateFlow<List<DeviceProfile>> = _profiles.asStateFlow()

    private lateinit var file: File

    fun init(application: Application) {
        if (::file.isInitialized) return
        val dir = File(application.filesDir, "farm").apply { mkdirs() }
        file = File(dir, "devices.json")
        val loaded = runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { DeviceProfile.fromJson(it) }
            }
        }.getOrDefault(emptyList())
        _profiles.value = if (loaded.any { it.id == DeviceProfile.PRIMARY }) {
            loaded
        } else {
            listOf(DeviceProfile(id = DeviceProfile.PRIMARY, name = "Primary device")) + loaded
        }
        persist()
    }

    fun find(id: String): DeviceProfile {
        if (id.isBlank()) return primary()
        return _profiles.value.firstOrNull { it.id == id && it.enabled } ?: primary()
    }

    fun primary(): DeviceProfile =
        _profiles.value.firstOrNull { it.id == DeviceProfile.PRIMARY }
            ?: _profiles.value.firstOrNull()
            ?: DeviceProfile(id = DeviceProfile.PRIMARY, name = "Primary device")

    fun add(profile: DeviceProfile): DeviceProfile {
        val clean = profile.copy(name = profile.name.trim().ifBlank { "Device" }.take(40))
        _profiles.value = _profiles.value + clean
        persist()
        return clean
    }

    fun update(profile: DeviceProfile) {
        _profiles.value = _profiles.value.map { if (it.id == profile.id) profile else it }
        persist()
    }

    fun remove(id: String) {
        if (id == DeviceProfile.PRIMARY) return
        _profiles.value = _profiles.value.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        if (!::file.isInitialized) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JSONArray().apply { _profiles.value.forEach { put(it.toJson()) } }.toString())
        }
    }
}
