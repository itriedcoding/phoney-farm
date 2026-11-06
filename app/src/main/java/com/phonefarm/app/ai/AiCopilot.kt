package com.phonefarm.app.ai

import android.content.Context
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.data.LogLevel
import com.phonefarm.app.data.NodeMode
import com.phonefarm.app.engine.FarmEngine
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Stored AI settings. The key never leaves the device except to the endpoint you configure. */
object AiPrefs {
    private const val PREF = "phonefarm_ai"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_MODEL = "model"
    private const val KEY_TOKEN = "api_key"
    private const val KEY_SUPERVISOR = "supervisor"

    const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
    const val DEFAULT_MODEL = "gpt-4o-mini"

    fun endpoint(context: Context): String =
        prefs(context).getString(KEY_ENDPOINT, DEFAULT_ENDPOINT).orEmpty().trimEnd('/')

    fun model(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }

    fun apiKey(context: Context): String = prefs(context).getString(KEY_TOKEN, "").orEmpty()

    fun supervisor(context: Context): Boolean = prefs(context).getBoolean(KEY_SUPERVISOR, false)

    fun configured(context: Context): Boolean = apiKey(context).isNotBlank()

    fun save(context: Context, endpoint: String, model: String, apiKey: String, supervisor: Boolean) {
        prefs(context).edit()
            .putString(KEY_ENDPOINT, endpoint.trim().ifBlank { DEFAULT_ENDPOINT })
            .putString(KEY_MODEL, model.trim().ifBlank { DEFAULT_MODEL })
            .putString(KEY_TOKEN, apiKey.trim())
            .putBoolean(KEY_SUPERVISOR, supervisor)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}

/**
 * A real operator loop: it calls an OpenAI-compatible chat completions endpoint with tool
 * definitions, executes whatever tools the model asks for against [FarmEngine], feeds the results
 * back, and repeats until the model answers. No canned responses, no simulated actions.
 */
object AiCopilot {

    private const val MAX_STEPS = 6
    private const val LOG_LIMIT = 80
    private const val MAX_NODES = 16
    private const val SUPERVISE_INTERVAL_MS = 30L * 60L * 1000L

    data class Entry(val kind: String, val text: String, val at: Long = System.currentTimeMillis())

    sealed interface AiState {
        data object Idle : AiState
        data class Thinking(val step: Int) : AiState
        data class Answer(val text: String) : AiState
        data class Failed(val message: String) : AiState
    }

    private val _state = MutableStateFlow<AiState>(AiState.Idle)
    val state: StateFlow<AiState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<Entry>>(emptyList())
    val log: StateFlow<List<Entry>> = _log.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var busy = false

    @Volatile
    private var lastSupervision = 0L

    fun clear() {
        _log.value = emptyList()
        if (_state.value !is AiState.Thinking) _state.value = AiState.Idle
    }

    /** Called from the scheduler; runs a supervision pass at most every 30 minutes. */
    fun superviseIfDue(context: Context) {
        if (!AiPrefs.supervisor(context) || !AiPrefs.configured(context)) return
        val now = System.currentTimeMillis()
        if (now - lastSupervision < SUPERVISE_INTERVAL_MS) return
        lastSupervision = now
        val appContext = context.applicationContext
        scope.launch {
            operate(
                appContext,
                "Supervise the farm now. Re-arm healthy disabled nodes, soften intervals that are too " +
                    "aggressive, fix failing nodes, and start the farm if it is idle with healthy nodes. " +
                    "Use tools only, then reply with one short sentence.",
            )
        }
    }

    suspend fun operate(context: Context, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (!AiPrefs.configured(context)) {
            _state.value = AiState.Failed("Add an API key in AI settings first")
            return@withContext Result.failure(IllegalStateException("no api key configured"))
        }
        if (busy) {
            return@withContext Result.failure(IllegalStateException("the operator is already working"))
        }

        busy = true
        push("you", prompt)
        try {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt()))
                .put(JSONObject().put("role", "user").put("content", prompt))
            val tools = toolSchema()

            var answer = ""
            for (step in 1..MAX_STEPS) {
                _state.value = AiState.Thinking(step)
                val message = request(context, messages, tools)
                val calls = message.optJSONArray("tool_calls")

                if (calls != null && calls.length() > 0) {
                    messages.put(
                        JSONObject(message.toString()).apply {
                            if (!has("content")) put("content", JSONObject.NULL)
                        },
                    )
                    for (index in 0 until calls.length()) {
                        val call = calls.optJSONObject(index) ?: continue
                        val function = call.optJSONObject("function")
                        val name = function?.optString("name").orEmpty()
                        val arguments = runCatching {
                            JSONObject(function?.optString("arguments").orEmpty().ifBlank { "{}" })
                        }.getOrDefault(JSONObject())
                        val result = execute(context, name, arguments)
                        push("tool", "$name → ${result.take(200)}")
                        messages.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", call.optString("id"))
                                .put("content", result),
                        )
                    }
                    continue
                }

                answer = message.optString("content").trim()
                break
            }

            if (answer.isBlank()) answer = "Stopped after $MAX_STEPS steps without a summary."
            push("ai", answer)
            _state.value = AiState.Answer(answer)
            Result.success(answer)
        } catch (t: Throwable) {
            val reason = t.message ?: t.javaClass.simpleName
            push("error", reason)
            _state.value = AiState.Failed(reason)
            Result.failure(t)
        } finally {
            busy = false
        }
    }

    // ---------------------------------------------------------------- transport

    private fun request(context: Context, messages: JSONArray, tools: JSONArray): JSONObject {
        val endpoint = AiPrefs.endpoint(context)
        val payload = JSONObject()
            .put("model", AiPrefs.model(context))
            .put("messages", messages)
            .put("tools", tools)
            .put("tool_choice", "auto")
            .put("temperature", 0.2)

        val connection = (URL("$endpoint/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${AiPrefs.apiKey(context)}")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("AI endpoint returned HTTP $code · ${text.take(180)}")
            }
            return JSONObject(text)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?: throw IllegalStateException("AI response contained no message")
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun toolSchema(): JSONArray {
        fun tool(name: String, description: String, properties: JSONObject, required: List<String>): JSONObject =
            JSONObject()
                .put("type", "function")
                .put(
                    "function",
                    JSONObject()
                        .put("name", name)
                        .put("description", description)
                        .put(
                            "parameters",
                            JSONObject()
                                .put("type", "object")
                                .put("properties", properties)
                                .put("required", JSONArray(required)),
                        ),
                )

        val nodeRef = JSONObject()
            .put("type", "string")
            .put("description", "Node name, host or id")
        val url = JSONObject().put("type", "string").put("description", "Full http(s) URL")
        val interval = JSONObject()
            .put("type", "integer")
            .put("description", "Seconds between cycles, clamped to 15-3600")
        val mode = JSONObject()
            .put("type", "string")
            .put("enum", JSONArray().put("LIGHT").put("SESSION").put("MONITOR"))

        return JSONArray()
            .put(
                tool(
                    "list_status",
                    "Read the live farm state: running flag plus every node with counters.",
                    JSONObject(),
                    emptyList(),
                ),
            )
            .put(
                tool(
                    "add_node",
                    "Add a node to the farm.",
                    JSONObject()
                        .put("name", JSONObject().put("type", "string"))
                        .put("url", url)
                        .put("interval_sec", interval)
                        .put("mode", mode)
                        .put("script", JSONObject().put("type", "string")),
                    listOf("url"),
                ),
            )
            .put(
                tool(
                    "remove_node",
                    "Delete a node from the farm.",
                    JSONObject().put("node", nodeRef),
                    listOf("node"),
                ),
            )
            .put(
                tool(
                    "set_interval",
                    "Change how often a node runs.",
                    JSONObject().put("node", nodeRef).put("seconds", interval),
                    listOf("node", "seconds"),
                ),
            )
            .put(
                tool(
                    "set_enabled",
                    "Arm or disarm a node.",
                    JSONObject()
                        .put("node", nodeRef)
                        .put("enabled", JSONObject().put("type", "boolean")),
                    listOf("node", "enabled"),
                ),
            )
            .put(
                tool(
                    "run_node",
                    "Run one cycle of a node immediately.",
                    JSONObject().put("node", nodeRef),
                    listOf("node"),
                ),
            )
            .put(
                tool(
                    "set_farm",
                    "Start or stop the whole farm.",
                    JSONObject().put("running", JSONObject().put("type", "boolean")),
                    listOf("running"),
                ),
            )
    }

    // ---------------------------------------------------------------- actions

    private fun execute(context: Context, name: String, args: JSONObject): String = runCatching {
        when (name) {
            "list_status" -> statusJson()

            "add_node" -> {
                val url = args.optString("url").trim()
                require(url.startsWith("http://") || url.startsWith("https://")) {
                    "url must start with http:// or https://"
                }
                require(FarmEngine.state.value.nodes.size < MAX_NODES) { "node limit reached ($MAX_NODES)" }
                val node = FarmNode(
                    name = args.optString("name").ifBlank {
                        runCatching { java.net.URI(url).host }.getOrNull() ?: url
                    },
                    url = url,
                    intervalSec = args.optInt("interval_sec", 300),
                    mode = modeOf(args.optString("mode")),
                    script = args.optString("script"),
                )
                FarmEngine.upsert(node)
                """{"ok":true,"node":"${node.name}","interval":${node.intervalSec},"mode":"${node.mode.name}"}"""
            }

            "remove_node" -> {
                val node = resolve(args.optString("node"))
                if (node == null) {
                    """{"ok":false,"error":"node not found"}"""
                } else {
                    FarmEngine.delete(node.id)
                    """{"ok":true,"removed":"${node.name}"}"""
                }
            }

            "set_interval" -> {
                val node = resolve(args.optString("node"))
                if (node == null) {
                    """{"ok":false,"error":"node not found"}"""
                } else {
                    val seconds = args.optInt("seconds", node.intervalSec)
                        .coerceIn(FarmNode.MIN_INTERVAL, FarmNode.MAX_INTERVAL)
                    FarmEngine.upsert(node.copy(intervalSec = seconds))
                    """{"ok":true,"node":"${node.name}","interval":$seconds}"""
                }
            }

            "set_enabled" -> {
                val node = resolve(args.optString("node"))
                if (node == null) {
                    """{"ok":false,"error":"node not found"}"""
                } else {
                    val enabled = args.optBoolean("enabled", true)
                    FarmEngine.setEnabled(node.id, enabled)
                    """{"ok":true,"node":"${node.name}","enabled":$enabled}"""
                }
            }

            "run_node" -> {
                val node = resolve(args.optString("node"))
                if (node == null) {
                    """{"ok":false,"error":"node not found"}"""
                } else {
                    FarmEngine.runNow(node.id)
                    """{"ok":true,"node":"${node.name}"}"""
                }
            }

            "set_farm" -> {
                val run = args.optBoolean("running", true)
                if (run) FarmEngine.startFarm("ai") else FarmEngine.stopFarm("ai")
                """{"ok":true,"running":$run}"""
            }

            else -> """{"ok":false,"error":"unknown tool"}"""
        }
    }.getOrElse { error ->
        val message = (error.message ?: "action failed").replace("\"", "'")
        """{"ok":false,"error":"$message"}"""
    }

    private fun resolve(reference: String): FarmNode? {
        val query = reference.trim().lowercase()
        if (query.isEmpty()) return null
        val nodes = FarmEngine.state.value.nodes
        return nodes.firstOrNull { it.id == reference }
            ?: nodes.firstOrNull { it.name.lowercase() == query }
            ?: nodes.firstOrNull { it.name.lowercase().contains(query) }
            ?: nodes.firstOrNull { it.host.lowercase().contains(query) }
    }

    private fun modeOf(value: String): NodeMode = when (value.uppercase()) {
        "LIGHT", "HTTP" -> NodeMode.HTTP
        "SESSION", "WEBVIEW" -> NodeMode.WEBVIEW
        "MONITOR", "PROBE" -> NodeMode.MONITOR
        else -> NodeMode.HTTP
    }

    private fun statusJson(): String {
        val state = FarmEngine.state.value
        val nodes = JSONArray()
        state.nodes.forEach { node ->
            val stats = state.statsOf(node.id)
            nodes.put(
                JSONObject()
                    .put("id", node.id)
                    .put("name", node.name)
                    .put("url", node.url)
                    .put("mode", node.mode.name)
                    .put("enabled", node.enabled)
                    .put("interval", node.intervalSec)
                    .put("cycles", stats.cycles)
                    .put("failures", stats.failures)
                    .put("lastStatus", stats.status)
                    .put("lastError", stats.error ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("running", state.running)
            .put("uptimeSec", state.uptimeMs / 1000)
            .put("nodes", nodes)
            .toString()
    }

    private fun systemPrompt(): String {
        val state = FarmEngine.state.value
        val summary = if (state.nodes.isEmpty()) {
            "no nodes"
        } else {
            state.nodes.joinToString("; ") { node ->
                "${node.name} [${node.mode.label}] ${node.url} every ${node.intervalSec}s " +
                    if (node.enabled) "armed" else "disarmed"
            }
        }
        return """
            You operate Phone Farm, an Android phone farm runtime that keeps web targets warm 24/7.
            Rules: act only through the provided tools; never create more than $MAX_NODES nodes; keep
            intervals between 15 and 3600 seconds; prefer LIGHT for plain HTTP endpoints and SESSION
            for pages that must render; respect each site's terms and rate limits; be terse.
            Farm running: ${state.running}. Nodes: $summary
        """.trimIndent()
    }

    private fun push(kind: String, text: String) {
        _log.value = (_log.value + Entry(kind, text)).takeLast(LOG_LIMIT)
        if (kind == "error") FarmEngine.log(LogLevel.WARN, "ai", text)
    }
}
