package com.phonefarm.app.engine

import com.phonefarm.app.data.DeviceProfile
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.data.ProxyEntry
import com.phonefarm.app.engine.net.Net
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Weightless node runner. Uses plain HTTP with a persistent cookie jar so a farm of hundreds of
 * nodes costs almost no RAM compared to a WebView per node.
 *
 * A node can run inside a [DeviceProfile], which gives it that profile's user agent, its own cookie
 * jar and — when the profile has one — a SOCKS5/HTTP proxy.
 */
object HttpRunner {
    private const val MAX_BODY_BYTES = 512L * 1024L
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val cookies = ConcurrentHashMap<String, MutableMap<String, String>>()

    fun visit(node: FarmNode, profile: DeviceProfile? = null, proxy: ProxyEntry? = null): RunResult =
        request(node, "GET", readBody = true, profile = profile, proxy = proxy)

    fun probe(node: FarmNode, profile: DeviceProfile? = null, proxy: ProxyEntry? = null): RunResult =
        request(node, "HEAD", readBody = false, profile = profile, proxy = proxy)

    fun forget(nodeId: String) {
        cookies.remove(nodeId)
    }

    private fun request(
        node: FarmNode,
        method: String,
        readBody: Boolean,
        profile: DeviceProfile?,
        proxy: ProxyEntry?,
    ): RunResult {
        val jarKey = profile?.id?.takeIf { it.isNotBlank() } ?: node.id
        val userAgent = profile?.userAgent?.takeIf { it.isNotBlank() }
            ?: if (node.desktopUa) UA_DESKTOP else UA_MOBILE

        if (proxy != null) {
            val headers = LinkedHashMap<String, String>()
            jarFor(jarKey).takeIf { it.isNotEmpty() }?.let { jar ->
                headers["Cookie"] = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }
            val response = Net.send(
                url = node.url,
                method = method,
                headers = headers,
                proxy = proxy,
                userAgent = userAgent,
            )
            return RunResult(response.ok, response.status, response.body.length.toLong(), response.error)
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(node.url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Cache-Control", "no-cache")
                jarFor(jarKey).takeIf { it.isNotEmpty() }?.let { jar ->
                    setRequestProperty("Cookie", jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            val code = connection.responseCode
            harvestCookies(jarKey, connection.headerFields)

            var read = 0L
            if (readBody && code in 200..299) {
                val stream = runCatching { connection.inputStream }.getOrNull()
                if (stream != null) {
                    val buffer = ByteArray(16 * 1024)
                    stream.use { input ->
                        while (read < MAX_BODY_BYTES) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            read += count
                        }
                    }
                }
            }

            val failure = when {
                code >= 500 -> "server error $code"
                code == 429 -> "rate limited"
                code in 400..499 -> "http $code"
                code in 200..399 -> null
                else -> "http $code"
            }
            RunResult(failure == null, code, read, failure)
        } catch (t: Throwable) {
            val reason = when (t) {
                is java.net.UnknownHostException -> "host not found"
                is java.net.SocketTimeoutException -> "timeout"
                is javax.net.ssl.SSLException -> "tls failure"
                is java.net.MalformedURLException -> "invalid url"
                else -> t.message ?: t.javaClass.simpleName
            }
            RunResult(false, 0, 0, reason)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun jarFor(jarKey: String): MutableMap<String, String> =
        cookies.getOrPut(jarKey) { mutableMapOf() }

    private fun harvestCookies(jarKey: String, headers: Map<String, List<String>>?) {
        headers ?: return
        val jar = jarFor(jarKey)
        headers.forEach { (key, values) ->
            if (key.equals("Set-Cookie", ignoreCase = true)) {
                values.forEach { raw ->
                    val pair = raw.substringBefore(';')
                    val name = pair.substringBefore('=').trim()
                    val value = pair.substringAfter('=', "").trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) jar[name] = value
                }
            }
        }
        while (jar.size > 64) {
            val oldest = jar.keys.firstOrNull() ?: break
            jar.remove(oldest)
        }
    }
}
