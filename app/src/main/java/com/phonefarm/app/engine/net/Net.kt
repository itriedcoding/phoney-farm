package com.phonefarm.app.engine.net

import com.phonefarm.app.data.Profiles
import com.phonefarm.app.data.ProxyEntry
import com.phonefarm.app.data.ProxyKind
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URI
import java.net.URL
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class NetResponse(
    val ok: Boolean,
    val status: Int,
    val body: String,
    val latencyMs: Long,
    val error: String? = null,
) {
    val snippet: String
        get() = body.replace(Regex("\\s+"), " ").trim().take(160)
}

/**
 * One HTTP path for every caller. HTTP proxies go through [HttpURLConnection]; SOCKS5 proxies are
 * tunnelled by [Socks5] and spoken to directly, including TLS, so traffic really leaves through the
 * proxy instead of silently falling back to a direct connection.
 */
object Net {

    private const val LIMIT = 128 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    fun send(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        proxy: ProxyEntry? = null,
        userAgent: String = Profiles.UA_MOBILE,
    ): NetResponse {
        if (url.isBlank()) return NetResponse(false, 0, "", 0, "empty url")
        val merged = LinkedHashMap<String, String>()
        merged["User-Agent"] = userAgent
        merged["Accept"] = "application/json, text/plain, */*"
        merged["Accept-Language"] = "en-US,en;q=0.9"
        merged.putAll(headers)
        return if (proxy != null && proxy.kind == ProxyKind.SOCKS5) {
            tunneled(url, method, merged, body, proxy)
        } else {
            direct(url, method, merged, body, proxy)
        }
    }

    fun sendJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        proxy: ProxyEntry? = null,
        userAgent: String = Profiles.UA_MOBILE,
    ): NetResponse = send(
        url = url,
        method = "POST",
        headers = headers + mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
        body = json,
        proxy = proxy,
        userAgent = userAgent,
    )

    fun sendForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        proxy: ProxyEntry? = null,
        userAgent: String = Profiles.UA_MOBILE,
    ): NetResponse = send(
        url = url,
        method = "POST",
        headers = headers + mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        body = form.entries.joinToString("&") {
            "${enc(it.key)}=${enc(it.value)}"
        },
        proxy = proxy,
        userAgent = userAgent,
    )

    private fun direct(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        proxy: ProxyEntry?,
    ): NetResponse {
        val started = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        return try {
            val javaProxy = if (proxy != null && proxy.kind == ProxyKind.HTTP && proxy.host.isNotBlank()) {
                Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port))
            } else {
                Proxy.NO_PROXY
            }
            connection = (URL(url).openConnection(javaProxy) as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                if (proxy != null && proxy.kind == ProxyKind.HTTP && proxy.username.isNotEmpty()) {
                    val raw = "${proxy.username}:${proxy.password}"
                    setRequestProperty(
                        "Proxy-Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(raw.toByteArray()),
                    )
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", headers["Content-Type"] ?: "application/x-www-form-urlencoded")
                }
            }
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { readText(it, LIMIT) }.orEmpty()
            val ms = System.currentTimeMillis() - started
            NetResponse(code in 200..399, code, text, ms, if (code in 200..399) null else "http $code")
        } catch (t: Throwable) {
            NetResponse(false, 0, "", System.currentTimeMillis() - started, describe(t))
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun tunneled(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        proxy: ProxyEntry,
    ): NetResponse {
        val started = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            val uri = URI(url)
            val secure = uri.scheme.equals("https", true)
            val host = uri.host ?: throw IllegalArgumentException("invalid url")
            val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
            val path = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")

            val tunnel = Socks5.open(
                host = host,
                port = port,
                proxyHost = proxy.host,
                proxyPort = proxy.port,
                username = proxy.username,
                password = proxy.password,
                timeoutMs = CONNECT_TIMEOUT_MS,
            )
            socket = tunnel
            val channel: Socket = if (secure) {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(tunnel, host, port, true) as SSLSocket
                val params = ssl.sslParameters
                params.endpointIdentificationAlgorithm = "HTTPS"
                ssl.sslParameters = params
                ssl.startHandshake()
                ssl
            } else {
                tunnel
            }

            val request = StringBuilder()
            request.append("$method $path HTTP/1.1\r\n")
            request.append("Host: ").append(if ((secure && port == 443) || (!secure && port == 80)) host else "$host:$port").append("\r\n")
            request.append("Connection: close\r\n")
            request.append("Accept-Encoding: identity\r\n")
            headers.forEach { (key, value) -> request.append(key).append(": ").append(value).append("\r\n") }
            if (body != null) request.append("Content-Length: ").append(body.toByteArray(Charsets.UTF_8).size).append("\r\n")
            request.append("\r\n")

            val output = channel.getOutputStream()
            output.write(request.toString().toByteArray(Charsets.UTF_8))
            if (body != null) output.write(body.toByteArray(Charsets.UTF_8))
            output.flush()

            val input = BufferedInputStream(channel.getInputStream())
            val statusLine = readLine(input)
            val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            var contentLength = -1
            var chunked = false
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                val name = line.substringBefore(':').trim()
                val value = line.substringAfter(':', "").trim()
                if (name.equals("Content-Length", true)) contentLength = value.toIntOrNull() ?: -1
                if (name.equals("Transfer-Encoding", true) && value.contains("chunked", true)) chunked = true
            }

            val text = when {
                chunked -> readChunked(input, LIMIT)
                contentLength >= 0 -> readExact(input, minOf(contentLength, LIMIT))
                else -> readText(input, LIMIT)
            }
            val ms = System.currentTimeMillis() - started
            NetResponse(code in 200..399, code, text, ms, if (code in 200..399) null else "http $code")
        } catch (t: Throwable) {
            NetResponse(false, 0, "", System.currentTimeMillis() - started, describe(t))
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun readLine(input: InputStream): String {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) break
            if (byte == '\n'.code) break
            if (byte != '\r'.code) buffer.append(byte.toChar())
            if (buffer.length > 8_192) break
        }
        return buffer.toString()
    }

    private fun readExact(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count <= 0) break
            read += count
        }
        return if (read == 0) "" else String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun readChunked(input: InputStream, limit: Int): String {
        val text = StringBuilder()
        while (text.length < limit) {
            val sizeLine = readLine(input).trim()
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) {
                readLine(input)
                break
            }
            text.append(readExact(input, minOf(size, limit - text.length)))
            readLine(input)
        }
        return text.toString()
    }

    private fun readText(input: InputStream, limit: Int): String {
        val bytes = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val count = input.read(bytes, read, limit - read)
            if (count <= 0) break
            read += count
        }
        return if (read == 0) "" else String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun describe(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "host not found"
        is java.net.SocketTimeoutException -> "timeout"
        is javax.net.ssl.SSLException -> "tls failure · ${t.message ?: "handshake"}"
        is java.net.MalformedURLException -> "invalid url"
        is java.net.ConnectException -> "proxy refused the connection"
        else -> t.message ?: t.javaClass.simpleName
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
