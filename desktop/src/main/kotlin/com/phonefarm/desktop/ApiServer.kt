package com.phonefarm.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * Token-protected JSON API over the LAN so the phone (or the AI operator, or curl) can drive
 * this desktop farm. Same contract the Android app's desktop cards speak.
 */
class ApiServer(
    private val core: FarmCore,
    private val token: String,
    private val port: Int = DEFAULT_PORT,
) {
    private var server: HttpServer? = null

    @Synchronized
    fun start() {
        if (server != null) return
        val instance = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
        instance.executor = Executors.newFixedThreadPool(4)

        instance.createContext("/api/status") { ex ->
            guard(ex) { respond(ex, 200, core.statusJson()) }
        }
        instance.createContext("/api/start") { ex ->
            guard(ex) {
                core.start()
                respond(ex, 200, "{\"ok\":true,\"running\":true}")
            }
        }
        instance.createContext("/api/stop") { ex ->
            guard(ex) {
                core.stop()
                respond(ex, 200, "{\"ok\":true,\"running\":false}")
            }
        }
        instance.createContext("/api/nodes") { ex ->
            guard(ex) {
                when (ex.requestMethod.uppercase()) {
                    "GET" -> respond(ex, 200, core.statusJson())
                    "POST" -> {
                        val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
                        val url = flatString(body, "url")
                        if (url.isBlank()) {
                            respond(ex, 400, "{\"ok\":false,\"error\":\"url required\"}")
                        } else {
                            val interval = flatInt(body, "intervalSec", 300)
                            val node = core.addNode(flatString(body, "name"), url, interval)
                            respond(ex, 200, "{\"ok\":true,\"id\":\"${FarmCore.esc(node.id)}\"}")
                        }
                    }

                    else -> respond(ex, 405, "{\"ok\":false,\"error\":\"method not allowed\"}")
                }
            }
        }
        instance.createContext("/api/nodes/") { ex ->
            guard(ex) {
                val tail = ex.requestURI.path.removePrefix("/api/nodes/").trim('/')
                val parts = tail.split('/').filter { it.isNotEmpty() }
                val id = parts.firstOrNull().orEmpty()
                if (id.isEmpty()) {
                    respond(ex, 400, "{\"ok\":false,\"error\":\"node id required\"}")
                    return@guard
                }
                when {
                    ex.requestMethod.equals("DELETE", true) -> {
                        core.removeNode(id)
                        respond(ex, 200, "{\"ok\":true}")
                    }

                    parts.size > 1 && parts[1] == "run" -> {
                        core.runNow(id)
                        respond(ex, 200, "{\"ok\":true}")
                    }

                    else -> respond(ex, 404, "{\"ok\":false,\"error\":\"unknown node action\"}")
                }
            }
        }

        instance.start()
        server = instance
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun guard(ex: HttpExchange, block: () -> Unit) {
        try {
            val supplied = ex.requestHeaders.getFirst("X-Farm-Token")
            if (supplied != token) {
                respond(ex, 401, "{\"ok\":false,\"error\":\"unauthorized\"}")
                return
            }
            block()
        } catch (t: Throwable) {
            respond(ex, 500, "{\"ok\":false,\"error\":\"${FarmCore.esc(t.message ?: "error")}\"}")
        } finally {
            ex.close()
        }
    }

    private fun respond(ex: HttpExchange, code: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.responseHeaders.add("Cache-Control", "no-store")
        runCatching {
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
    }

    private fun flatString(body: String, key: String): String {
        val marker = "\"$key\""
        val start = body.indexOf(marker)
        if (start < 0) return ""
        val colon = body.indexOf(':', start + marker.length)
        if (colon < 0) return ""
        val quote = body.indexOf('"', colon + 1)
        if (quote < 0) return ""
        val sb = StringBuilder()
        var index = quote + 1
        while (index < body.length) {
            val c = body[index]
            if (c == '\\' && index + 1 < body.length) {
                sb.append(body[index + 1])
                index += 2
                continue
            }
            if (c == '"') return sb.toString()
            sb.append(c)
            index++
        }
        return sb.toString()
    }

    private fun flatInt(body: String, key: String, fallback: Int): Int {
        val marker = "\"$key\""
        val start = body.indexOf(marker)
        if (start < 0) return fallback
        val colon = body.indexOf(':', start + marker.length)
        if (colon < 0) return fallback
        val digits = body.substring(colon + 1).trimStart().takeWhile { it.isDigit() || it == '-' }
        return digits.toIntOrNull() ?: fallback
    }

    companion object {
        const val DEFAULT_PORT = 8787
    }
}

/** Every non-loopback IPv4 address this machine can be reached on. */
fun lanAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<java.net.Inet4Address>()
        .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
        .map { it.hostAddress }
        .distinct()
}.getOrDefault(emptyList())
