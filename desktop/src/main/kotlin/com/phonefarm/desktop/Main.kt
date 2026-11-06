package com.phonefarm.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import kotlin.random.Random

fun main(args: Array<String>) {
    val home = File(System.getProperty("user.home"), ".phonefarm")
    home.mkdirs()

    val core = FarmCore(File(home, "nodes.properties"))
    val token = TokenStore(File(home, "token.txt")).get()
    val api = ApiServer(core, token)
    api.start()

    if (args.contains("--server")) {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { api.stop() }
                runCatching { core.stop() }
            },
        )
        core.start()
        println(
            "PHONE_FARM_AGENT port=${ApiServer.DEFAULT_PORT} token=$token " +
                "lan=${lanAddresses().joinToString(",")} version=${FarmCore.VERSION}",
        )
        while (true) Thread.sleep(60_000)
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { api.stop() }
            runCatching { core.stop() }
        },
    )

    application {
        Window(
            onCloseRequest = { exitApplication() },
            title = "Phone Farm Desktop",
            state = rememberWindowState(width = 1060.dp, height = 820.dp),
        ) {
            DesktopApp(core, token)
        }
    }
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
