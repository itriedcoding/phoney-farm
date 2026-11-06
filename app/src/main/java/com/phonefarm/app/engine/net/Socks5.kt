package com.phonefarm.app.engine.net

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal but complete SOCKS5 client (RFC 1928 + RFC 1929 username/password auth).
 *
 * It performs the real greeting, optional auth subnegotiation and CONNECT request, so nodes and
 * social calls genuinely leave through the proxy — there is no fallback to a direct connection.
 */
object Socks5 {

    fun open(host: String, port: Int, proxyHost: String, proxyPort: Int, username: String, password: String, timeoutMs: Int = 15_000): Socket {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(proxyHost, proxyPort), timeoutMs)
            socket.soTimeout = timeoutMs
            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            val useAuth = username.isNotEmpty()
            val methods = if (useAuth) byteArrayOf(0x00, 0x02) else byteArrayOf(0x00)
            out.write(0x05)
            out.write(methods.size)
            out.write(methods)
            out.flush()

            val version = input.readUnsignedByte()
            val method = input.readUnsignedByte()
            if (version != 0x05) throw IOException("proxy is not SOCKS5 (replied 0x${version.toString(16)})")
            if (method == 0xFF) throw IOException("proxy rejected all auth methods")

            if (method == 0x02) {
                val user = username.toByteArray()
                val pass = password.toByteArray()
                if (user.size > 255 || pass.size > 255) throw IOException("proxy credentials are too long")
                val request = ByteArray(3 + user.size + pass.size)
                request[0] = 0x01
                request[1] = user.size.toByte()
                user.copyInto(request, 2)
                request[2 + user.size] = pass.size.toByte()
                pass.copyInto(request, 3 + user.size)
                out.write(request)
                out.flush()
                input.readUnsignedByte()
                val status = input.readUnsignedByte()
                if (status != 0x00) throw IOException("proxy rejected the username or password (${status.toInt()})")
            } else if (method != 0x00) {
                throw IOException("proxy requires an unsupported auth method ($method)")
            }

            val hostBytes = host.toByteArray()
            if (hostBytes.size > 255) throw IOException("host name is too long for SOCKS5")
            val request = ByteArray(7 + hostBytes.size)
            request[0] = 0x05
            request[1] = 0x01
            request[2] = 0x00
            request[3] = 0x03
            request[4] = hostBytes.size.toByte()
            hostBytes.copyInto(request, 5)
            request[5 + hostBytes.size] = (port ushr 8).toByte()
            request[6 + hostBytes.size] = (port and 0xFF).toByte()
            out.write(request)
            out.flush()

            val replyVersion = input.readUnsignedByte()
            if (replyVersion != 0x05) throw IOException("bad SOCKS5 reply (0x${replyVersion.toString(16)})")
            val reply = input.readUnsignedByte()
            if (reply != 0x00) throw IOException(describe(reply))
            input.readUnsignedByte()
            when (input.readUnsignedByte()) {
                0x01 -> repeat(4) { input.readByte() }
                0x04 -> repeat(16) { input.readByte() }
                0x03 -> repeat(input.readUnsignedByte()) { input.readByte() }
                else -> throw IOException("proxy sent an unknown address type")
            }
            repeat(2) { input.readByte() }
            socket.soTimeout = 0
            return socket
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    private fun describe(code: Int): String = when (code) {
        0x01 -> "proxy failure (general)"
        0x02 -> "proxy denies connections (ruleset)"
        0x03 -> "proxy reports the network is unreachable"
        0x04 -> "proxy reports the host is unreachable"
        0x05 -> "proxy refused the connection"
        0x06 -> "proxy reports TTL expired"
        0x07 -> "proxy does not support the CONNECT command"
        0x08 -> "proxy does not support that address type"
        else -> "proxy rejected the request (code $code)"
    }
}
