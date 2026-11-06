package com.phonefarm.app.engine

import android.app.Application
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.data.Preset
import com.phonefarm.app.data.Presets
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class PresetProbe(
    val presetId: String,
    val ok: Boolean,
    val status: Int,
    val latencyMs: Long,
    val checkedAt: Long,
    val error: String?,
)

/**
 * Everything on the Live tab is backed by work that actually happened: preset rows hold the result
 * of a real HTTP request, and screen tiles hold a PNG rendered from a real WebView.
 */
object LiveHub {

    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _probes = MutableStateFlow<Map<String, PresetProbe>>(emptyMap())
    val probes: StateFlow<Map<String, PresetProbe>> = _probes.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    private val _shots = MutableStateFlow<Map<String, ScreenShot>>(emptyMap())
    val shots: StateFlow<Map<String, ScreenShot>> = _shots.asStateFlow()

    private val _capturing = MutableStateFlow<Set<String>>(emptySet())
    val capturing: StateFlow<Set<String>> = _capturing.asStateFlow()

    fun init(application: Application) {
        app = application
    }

    fun probePresets(presets: List<Preset> = Presets.all) {
        if (_probing.value) return
        _probing.value = true
        scope.launch {
            try {
                val gate = Semaphore(4)
                coroutineScope {
                    presets.map { preset ->
                        async {
                            gate.withPermit {
                                val node = FarmNode(
                                    name = preset.name,
                                    url = preset.url,
                                    mode = preset.mode,
                                    intervalSec = preset.intervalSec,
                                )
                                val started = System.currentTimeMillis()
                                val result = runCatching { HttpRunner.visit(node) }.getOrElse {
                                    RunResult(false, 0, 0, it.message ?: "probe failed")
                                }
                                val probe = PresetProbe(
                                    presetId = preset.id,
                                    ok = result.ok,
                                    status = result.status,
                                    latencyMs = System.currentTimeMillis() - started,
                                    checkedAt = System.currentTimeMillis(),
                                    error = result.error,
                                )
                                _probes.value = _probes.value + (preset.id to probe)
                                probe
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _probing.value = false
            }
        }
    }

    fun captureAll(nodes: List<FarmNode>) {
        val targets = nodes.filter { it.url.startsWith("http") }
        if (targets.isEmpty()) return
        scope.launch {
            for (node in targets) {
                if (node.id in _capturing.value) continue
                _capturing.value = _capturing.value + node.id
                try {
                    ScreenCapture.capture(app, node)?.let { shot ->
                        _shots.value = _shots.value + (node.id to shot)
                    }
                } finally {
                    _capturing.value = _capturing.value - node.id
                }
                delay(500)
            }
        }
    }

    fun capture(node: FarmNode) {
        if (node.id in _capturing.value) return
        scope.launch {
            _capturing.value = _capturing.value + node.id
            try {
                ScreenCapture.capture(app, node)?.let { shot ->
                    _shots.value = _shots.value + (node.id to shot)
                }
            } finally {
                _capturing.value = _capturing.value - node.id
            }
        }
    }

    fun forget(nodeId: String) {
        _shots.value = _shots.value - nodeId
        if (::app.isInitialized) {
            runCatching { File(ScreenCapture.directory(app), "$nodeId.png").delete() }
        }
    }
}
