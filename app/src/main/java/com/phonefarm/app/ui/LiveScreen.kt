package com.phonefarm.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.data.FarmState
import com.phonefarm.app.data.Preset
import com.phonefarm.app.data.Presets
import com.phonefarm.app.data.RemoteHost
import com.phonefarm.app.data.Remotes
import com.phonefarm.app.engine.LiveHub
import com.phonefarm.app.engine.RemoteClient
import com.phonefarm.app.engine.ScreenShot
import com.phonefarm.app.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(
    state: FarmState,
    onAddPreset: (Preset) -> Unit,
    onWatch: (FarmNode) -> Unit,
) {
    val probes by LiveHub.probes.collectAsStateWithLifecycle()
    val probing by LiveHub.probing.collectAsStateWithLifecycle()
    val shots by LiveHub.shots.collectAsStateWithLifecycle()
    val capturing by LiveHub.capturing.collectAsStateWithLifecycle()
    val hosts by Remotes.hosts.collectAsStateWithLifecycle()

    var showAddHost by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("Desktop") }
    var newUrl by remember { mutableStateOf("http://192.168.1.20:8787") }
    var newToken by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (probes.isEmpty()) LiveHub.probePresets()
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Ink.Void),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("LIVE", style = monoStyle(10, Ink.Ash, FontWeight.Medium, 3f))
            Spacer(Modifier.height(6.dp))
            Text("Live feed", style = MaterialTheme.typography.headlineMedium, color = Ink.Chalk)
            Spacer(Modifier.height(4.dp))
            Text(
                "Presets are probed with real requests. Tiles are screenshots rendered from real WebViews.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Dim,
            )
        }

        item {
            SectionHeader("Screens", action = {
                MiniButton("Capture all") { LiveHub.captureAll(state.nodes) }
            })
        }

        if (state.nodes.isEmpty()) {
            item {
                Panel {
                    Text("Nothing to watch yet", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add a node from a preset below or from the farm tab, then capture to see its real page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.Ash,
                    )
                }
            }
        } else {
            items(state.nodes.chunked(2).size) { rowIndex ->
                val row = state.nodes.chunked(2)[rowIndex]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { node ->
                        ScreenTile(
                            node = node,
                            shot = shots[node.id],
                            capturing = capturing.contains(node.id),
                            modifier = Modifier.weight(1f),
                            onCapture = { LiveHub.capture(node) },
                            onWatch = { onWatch(node) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        item {
            SectionHeader("Presets", action = {
                MiniButton(if (probing) "Probing…" else "Probe", active = !probing) {
                    if (!probing) LiveHub.probePresets()
                }
            })
        }

        items(Presets.all.size) { index ->
            val preset = Presets.all[index]
            PresetRow(preset, probes[preset.id], onAdd = { onAddPreset(preset) })
            Spacer(Modifier.height(8.dp))
        }

        item {
            SectionHeader("Desktop agents", action = { MiniButton("Add host") { showAddHost = true } })
            if (hosts.isEmpty()) {
                Panel {
                    Text("No desktop connected", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Run Phone Farm Desktop on Windows, then add its LAN address and pairing token here to drive it from this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.Ash,
                    )
                }
            }
        }

        items(hosts.size) { index ->
            HostCard(hosts[index])
            Spacer(Modifier.height(10.dp))
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showAddHost) {
        AlertDialog(
            onDismissRequest = { showAddHost = false },
            containerColor = Ink.Layer2,
            title = { Text("Add desktop agent", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk) },
            text = {
                Column {
                    FarmField("Name", newName, { newName = it })
                    Spacer(Modifier.height(10.dp))
                    FarmField("Address", newUrl, { newUrl = it })
                    Spacer(Modifier.height(10.dp))
                    FarmField("Pairing token", newToken, { newToken = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Remotes.add(RemoteHost(name = newName.ifBlank { "Desktop" }, baseUrl = newUrl.trim(), token = newToken.trim()))
                    showAddHost = false
                }) {
                    Text("ADD", color = Ink.Chalk, style = monoStyle(10, Ink.Chalk, FontWeight.Medium, 1.4f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHost = false }) {
                    Text("CANCEL", color = Ink.Dim, style = monoStyle(10, Ink.Dim, FontWeight.Medium, 1.4f))
                }
            },
        )
    }
}

@Composable
private fun ScreenTile(
    node: FarmNode,
    shot: ScreenShot?,
    capturing: Boolean,
    modifier: Modifier = Modifier,
    onCapture: () -> Unit,
    onWatch: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Ink.Layer)
            .border(1.dp, Ink.LineSoft, RoundedCornerShape(4.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Ink.Layer2)
                .clickable { if (shot != null) onWatch() else onCapture() },
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = remember(shot?.path, shot?.at) {
                shot?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "${node.name} screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    if (capturing) "RENDERING…" else "NO FRAME",
                    style = monoStyle(9, Ink.Dim, spacing = 1.6f),
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                node.name.ifBlank { node.host },
                style = MaterialTheme.typography.titleMedium,
                color = Ink.Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                shot?.let { "CAPTURED ${Fmt.clock(it.at)}" } ?: "NOT CAPTURED",
                style = monoStyle(8, Ink.Dim, spacing = 1.2f),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                MiniButton("Capture") { onCapture() }
                Spacer(Modifier.width(6.dp))
                MiniButton("Watch") { onWatch() }
            }
        }
    }
}

@Composable
private fun PresetRow(preset: Preset, probe: com.phonefarm.app.engine.PresetProbe?, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Ink.Layer)
            .border(1.dp, Ink.LineSoft, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(probe?.ok == true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(preset.category, style = monoStyle(8, Ink.Dim, spacing = 1.2f))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                preset.url,
                style = monoStyle(9, Ink.Dim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    probe == null -> "NOT PROBED"
                    probe.ok -> "LIVE ${probe.status} · ${probe.latencyMs}ms · ${Fmt.clock(probe.checkedAt)}"
                    else -> "FAILED · ${probe.error?.uppercase() ?: "ERROR"}"
                },
                style = monoStyle(9, if (probe?.ok == false) Ink.Paper else Ink.Ash, spacing = 0.6f),
            )
        }
        Spacer(Modifier.width(10.dp))
        MiniButton("Add", onClick = onAdd)
    }
}

@Composable
private fun HostCard(host: RemoteHost) {
    val scope = rememberCoroutineScope()
    var status by remember(host.id) { mutableStateOf<RemoteClient.Status?>(null) }
    var error by remember(host.id) { mutableStateOf<String?>(null) }
    var busy by remember(host.id) { mutableStateOf(false) }
    var showAddNode by remember(host.id) { mutableStateOf(false) }
    var nodeName by remember(host.id) { mutableStateOf("") }
    var nodeUrl by remember(host.id) { mutableStateOf("https://") }

    fun refresh() {
        busy = true
        scope.launch {
            RemoteClient.status(host)
                .onSuccess { status = it; error = null }
                .onFailure { error = it.message ?: "connection failed"; status = null }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Ink.Layer)
            .border(1.dp, Ink.LineSoft, RoundedCornerShape(4.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(status?.running == true)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(host.name, style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                Spacer(Modifier.height(2.dp))
                Text(host.baseUrl, style = monoStyle(9, Ink.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniButton(if (busy) "…" else "Connect", active = !busy) { refresh() }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = monoStyle(9, Ink.Paper))
        }

        status?.let { remote ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (remote.running) "ONLINE · ${Fmt.uptime(remote.uptimeSec * 1000)}" else "IDLE",
                    style = monoStyle(9, Ink.Ash, spacing = 1.2f),
                )
                Spacer(Modifier.weight(1f))
                Text("${remote.nodes.size} NODE(S)", style = monoStyle(9, Ink.Dim, spacing = 1.2f))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                MiniButton(if (remote.running) "Stop" else "Start") {
                    busy = true
                    scope.launch {
                        (if (remote.running) RemoteClient.stop(host) else RemoteClient.start(host))
                            .onFailure { error = it.message }
                        busy = false
                        refresh()
                    }
                }
                Spacer(Modifier.width(6.dp))
                MiniButton("Add node") { showAddNode = true }
            }
            remote.nodes.forEach { node ->
                Spacer(Modifier.height(9.dp))
                Hairline(Ink.LineSoft)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            node.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.Chalk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${node.mode} · ${node.cycles} CYCLES · ${node.status}",
                            style = monoStyle(8, Ink.Dim, spacing = 1f),
                        )
                    }
                    MiniButton("Run") {
                        scope.launch { RemoteClient.runNode(host, node.id); refresh() }
                    }
                    Spacer(Modifier.width(6.dp))
                    MiniButton("Delete") {
                        scope.launch { RemoteClient.removeNode(host, node.id); refresh() }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        MiniButton("Remove host") { Remotes.remove(host.id) }
    }

    if (showAddNode) {
        AlertDialog(
            onDismissRequest = { showAddNode = false },
            containerColor = Ink.Layer2,
            title = { Text("Add remote node", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk) },
            text = {
                Column {
                    FarmField("Name", nodeName, { nodeName = it })
                    Spacer(Modifier.height(10.dp))
                    FarmField("URL", nodeUrl, { nodeUrl = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = nodeUrl.trim()
                    if (url.startsWith("http")) {
                        scope.launch {
                            RemoteClient.addNode(host, nodeName.ifBlank { url }, url, 300)
                                .onFailure { error = it.message }
                            showAddNode = false
                            refresh()
                        }
                    }
                }) {
                    Text("ADD", color = Ink.Chalk, style = monoStyle(10, Ink.Chalk, FontWeight.Medium, 1.4f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNode = false }) {
                    Text("CANCEL", color = Ink.Dim, style = monoStyle(10, Ink.Dim, FontWeight.Medium, 1.4f))
                }
            },
        )
    }
}
