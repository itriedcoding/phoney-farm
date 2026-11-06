package com.phonefarm.app.engine

import com.phonefarm.app.data.RemoteHost
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Talks to a real Phone Farm Desktop agent over the LAN. The protocol is plain JSON over HTTP with
 * a shared token — the same contract the desktop app's ApiServer implements.
 */
object RemoteClient {

    data class Node(
        val id: String,
        val name: String,
        val url: String,
        val enabled: Boolean,
        val mode: String,
        val intervalSec: Int,
        val cycles: Long,
        val failures: Long,
        val latencyMs: Long,
        val status: Int,
        val error: String?,
    )

    data class Status(
        val running: Boolean,
        val uptimeSec: Long,
        val nodes: List<Node>,
    )

    suspend fun status(host: RemoteHost): Result<Status> = call(host, "GET", "/api/status").map { body ->
        val root = JSONObject(body)
        val nodes = ArrayList<Node>()
        val array = root.optJSONArray("nodes")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                nodes += Node(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    url = item.optString("url"),
                    enabled = item.optBoolean("enabled", true),
                    mode = item.optString("mode", "HTTP"),
                    intervalSec = item.optInt("intervalSec", 300),
                    cycles = item.optLong("cycles"),
                    failures = item.optLong("failures"),
                    latencyMs = item.optLong("latencyMs"),
                    status = item.optInt("status"),
                    error = if (item.isNull("error")) null else item.optString("error"),
                )
            }
        }
        Status(root.optBoolean("running"), root.optLong("uptimeSec"), nodes)
    }

    suspend fun start(host: RemoteHost): Result<Unit> =
        call(host, "POST", "/api/start").map { }

    suspend fun stop(host: RemoteHost): Result<Unit> =
        call(host, "POST", "/api/stop").map { }

    suspend fun addNode(host: RemoteHost, name: String, url: String, intervalSec: Int): Result<Unit> =
        call(
            host,
            "POST",
            "/api/nodes",
            JSONObject()
                .put("name", name)
                .put("url", url)
                .put("intervalSec", intervalSec)
                .toString(),
        ).map { }

    suspend fun removeNode(host: RemoteHost, id: String): Result<Unit> =
        call(host, "DELETE", "/api/nodes/$id").map { }

    suspend fun runNode(host: RemoteHost, id: String): Result<Unit> =
        call(host, "POST", "/api/nodes/$id/run").map { }

    private suspend fun call(
        host: RemoteHost,
        method: String,
        path: String,
        body: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = host.baseUrl.trim().trimEnd('/')
            require(base.startsWith("http://") || base.startsWith("https://")) {
                "agent address must start with http://"
            }
            val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 6_000
                readTimeout = 8_000
                setRequestProperty("X-Farm-Token", host.token)
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                if (body != null) {
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    throw IllegalStateException("agent returned HTTP $code ${text.take(140)}")
                }
                text
            } finally {
                runCatching { connection.disconnect() }
            }
        }
    }
}
