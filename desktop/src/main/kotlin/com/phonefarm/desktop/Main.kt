package com.phonefarm.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import kotlin.random.Random

fun main(args: Array<String>) {
    val home = File(System.getProperty("user.home"), ".phonefarm").apply { mkdirs() }
    val logFile = File(home, "desktop.log")

    // Software rendering is the default: the window then opens on VMs, RDP sessions and machines
    // without a usable GPU, instead of failing during Skia/Direct3D start-up. Pass --gpu to opt in
    // to hardware rendering.
    if (!args.contains("--gpu") && System.getProperty("skiko.renderApi") == null) {
        System.setProperty("skiko.renderApi", "SOFTWARE")
    }
    log(
        logFile,
        "launch args=[${args.joinToString(" ")}] renderApi=${System.getProperty("skiko.renderApi") ?: "default"} " +
            "java=${System.getProperty("java.version")} os=${System.getProperty("os.name")} " +
            "headless=${java.awt.GraphicsEnvironment.isHeadless()}",
    )

    val core = FarmCore(File(home, "nodes.properties"))
    val token = TokenStore(File(home, "token.txt")).get()
    val api = ApiServer(core, token)
    runCatching { api.start() }.onFailure { log(logFile, "api server failed · ${it.message}") }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { api.stop() }
            runCatching { core.stop() }
        },
    )

    if (args.contains("--server")) {
        core.start()
        println(
            "PHONE_FARM_AGENT port=${ApiServer.DEFAULT_PORT} token=$token " +
                "lan=${lanAddresses().joinToString(",")} version=${FarmCore.VERSION}",
        )
        log(logFile, "agent running headless on port ${ApiServer.DEFAULT_PORT}")
        while (true) Thread.sleep(60_000)
    }

    try {
        application {
            Window(
                onCloseRequest = { exitApplication() },
                title = "Phone Farm Desktop",
                state = rememberWindowState(width = 1060.dp, height = 820.dp),
            ) {
                DesktopApp(core, token)
            }
        }
    } catch (t: Throwable) {
        log(logFile, "window failed · ${stack(t)}")
        System.err.println("Phone Farm Desktop could not open its window: ${t.message}")
        System.err.println("The farm agent is still running locally. Log: ${logFile.absolutePath}")
        while (true) Thread.sleep(60_000)
    }
}

private fun log(file: File, line: String) {
    runCatching {
        file.parentFile?.mkdirs()
        file.appendText("${LocalDateTime.now()}  $line\n")
    }
    println(line)
}

private fun stack(t: Throwable): String {
    val writer = StringWriter()
    t.printStackTrace(PrintWriter(writer))
    return writer.toString().lineSequence().take(24).joinToString(" | ")
}

private class TokenStore(private val file: File) {
    fun get(): String {
        if (file.exists()) {
            val existing = file.readText().trim()
            if (existing.length >= 8) return existing
        }
        val fresh = (1..12).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
        file.parentFile?.mkdirs()
        file.writeText(fresh)
        return fresh
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
