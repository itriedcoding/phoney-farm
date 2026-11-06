package com.phonefarm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonefarm.app.data.DeviceProfile
import com.phonefarm.app.data.Profiles
import com.phonefarm.app.data.Proxies
import com.phonefarm.app.data.ProxyEntry
import com.phonefarm.app.data.ProxyKind

/** SOCKS5 / HTTP proxy pool, stored on device. */
@Composable
fun ProxyPanel() {
    val proxies by Proxies.entries.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ProxyEntry()) }

    SectionHeader("SOCKS5 & proxies")
    Panel(padding = 14.dp) {
        Text(
            "SOCKS5 proxies are tunnelled with a real handshake, username and password included. " +
                "HTTP proxies are spoken to directly.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Dim,
        )
        if (proxies.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("No proxies added", style = monoStyle(9, Ink.Dim))
        }
        proxies.forEach { entry ->
            Hairline(Ink.LineSoft)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseDot(entry.enabled)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink.Chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(entry.display)
                            if (entry.username.isNotBlank()) append(" · auth")
                        },
                        style = monoStyle(9, Ink.Dim),
                    )
                }
                MiniButton("Remove", onClick = { Proxies.remove(entry.id) })
            }
        }
        Spacer(Modifier.height(12.dp))
        GhostButton("Add SOCKS5 proxy") {
            draft = ProxyEntry()
            show = true
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            containerColor = Ink.Layer2,
            title = { Text("Add proxy", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProxyKind.entries.forEach { kind ->
                            Option(kind.label, draft.kind == kind, Modifier.weight(1f)) {
                                draft = draft.copy(kind = kind, port = kind.defaultPort)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FarmField("Label", draft.label, { draft = draft.copy(label = it) }, placeholder = "Optional")
                    Spacer(Modifier.height(10.dp))
                    FarmField("Host", draft.host, { draft = draft.copy(host = it) }, placeholder = "127.0.0.1")
                    Spacer(Modifier.height(10.dp))
                    FarmField(
                        "Port",
                        draft.port.toString(),
                        { draft = draft.copy(port = it.toIntOrNull() ?: draft.port) },
                        keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(10.dp))
                    FarmField("Username", draft.username, { draft = draft.copy(username = it) }, placeholder = "Optional")
                    Spacer(Modifier.height(10.dp))
                    FarmField(
                        "Password",
                        draft.password,
                        { draft = draft.copy(password = it) },
                        placeholder = "Optional",
                        secret = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draft.host.isNotBlank()) {
                        Proxies.add(draft)
                        show = false
                    }
                }) {
                    Text("Add", color = Ink.Chalk, style = monoStyle(10, Ink.Chalk, FontWeight.Medium, 1.4f))
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) {
                    Text("Cancel", color = Ink.Dim, style = monoStyle(10, Ink.Dim, FontWeight.Medium, 1.4f))
                }
            },
        )
    }
}

/** Device profiles: several independent identities from one phone. */
@Composable
fun DevicePanel() {
    val profiles by Profiles.profiles.collectAsStateWithLifecycle()
    val proxies by Proxies.entries.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(DeviceProfile()) }

    SectionHeader("Devices")
    Panel(padding = 14.dp) {
        Text(
            "Each device keeps its own user agent, its own cookie jar and, optionally, its own proxy. " +
                "Run five devices from one phone without sharing a login.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Dim,
        )
        profiles.forEach { profile ->
            Hairline(Ink.LineSoft)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseDot(profile.enabled)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink.Chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(uaLabel(profile.userAgent))
                            val proxy = proxies.firstOrNull { it.id == profile.proxyId }
                            if (proxy != null) append(" · ").append(proxy.display) else append(" · direct")
                        },
                        style = monoStyle(9, Ink.Dim),
                    )
                }
                if (profile.id != DeviceProfile.PRIMARY) {
                    MiniButton("Remove", onClick = { Profiles.remove(profile.id) })
                } else {
                    Text("PRIMARY", style = monoStyle(8, Ink.Dim, spacing = 1.2f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GhostButton("Add device") {
            draft = DeviceProfile(name = "Device ${profiles.size + 1}")
            show = true
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            containerColor = Ink.Layer2,
            title = { Text("Add device", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk) },
            text = {
                Column {
                    FarmField("Name", draft.name, { draft = draft.copy(name = it) })
                    Spacer(Modifier.height(12.dp))
                    Text("Profile", style = monoStyle(9, Ink.Dim, spacing = 1.4f))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Android" to Profiles.UA_MOBILE,
                            "Desktop" to Profiles.UA_DESKTOP,
                            "iPhone" to Profiles.UA_IPHONE,
                        ).forEach { (label, ua) ->
                            Option(label, draft.userAgent == ua, Modifier.weight(1f)) {
                                draft = draft.copy(userAgent = ua)
                            }
                        }
                    }
                    if (proxies.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("Proxy", style = monoStyle(9, Ink.Dim, spacing = 1.4f))
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Option("Direct", draft.proxyId.isBlank(), Modifier.weight(1f)) {
                                    draft = draft.copy(proxyId = "")
                                }
                                proxies.take(1).forEach { proxy ->
                                    Option(proxy.kind.label, draft.proxyId == proxy.id, Modifier.weight(1f)) {
                                        draft = draft.copy(proxyId = proxy.id)
                                    }
                                }
                            }
                            if (proxies.size > 1) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    proxies.drop(1).take(2).forEach { proxy ->
                                        Option(proxy.host, draft.proxyId == proxy.id, Modifier.weight(1f)) {
                                            draft = draft.copy(proxyId = proxy.id)
                                        }
                                    }
                                    if (proxies.size == 2) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draft.name.isNotBlank()) {
                        Profiles.add(draft)
                        show = false
                    }
                }) {
                    Text("Add", color = Ink.Chalk, style = monoStyle(10, Ink.Chalk, FontWeight.Medium, 1.4f))
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) {
                    Text("Cancel", color = Ink.Dim, style = monoStyle(10, Ink.Dim, FontWeight.Medium, 1.4f))
                }
            },
        )
    }
}

internal fun uaLabel(userAgent: String): String = when (userAgent) {
    Profiles.UA_MOBILE -> "Android UA"
    Profiles.UA_DESKTOP -> "Desktop UA"
    Profiles.UA_IPHONE -> "iPhone UA"
    else -> "Custom UA"
}
