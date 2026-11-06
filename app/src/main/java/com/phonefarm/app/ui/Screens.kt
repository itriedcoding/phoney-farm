package com.phonefarm.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonefarm.app.BuildConfig
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.data.FarmState
import com.phonefarm.app.data.LogEntry
import com.phonefarm.app.data.LogLevel
import com.phonefarm.app.data.Settings
import com.phonefarm.app.data.UpdateChannel
import com.phonefarm.app.engine.FarmEngine
import com.phonefarm.app.update.UpdateManager
import com.phonefarm.app.util.Fmt

// ---------------------------------------------------------------------------- farm dashboard

@Composable
fun FarmScreen(
    state: FarmState,
    update: UpdateManager.UpdateState,
    onToggleFarm: (Boolean) -> Unit,
    onAddNode: () -> Unit,
    onEditNode: (FarmNode) -> Unit,
    onToggleNode: (FarmNode, Boolean) -> Unit,
    onRunNow: (FarmNode) -> Unit,
    onOpenUpdates: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Ink.Void),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
        item {
            HeaderBlock(state, onToggleFarm)
        }

        if (state.governor.throttled) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, Ink.Line, RoundedCornerShape(3.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("THROTTLED", style = monoStyle(9, Ink.Paper, FontWeight.Medium, 1.4f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${state.governor.reason} · one node at a slower cadence",
                        style = monoStyle(10, Ink.Ash),
                    )
                }
            }
        }

        if (needsAttention(update)) {
            item {
                Spacer(Modifier.height(12.dp))
                UpdateBanner(update, onOpenUpdates)
            }
        }

        item {
            SectionHeader("Nodes", action = { MiniButton("Add", onClick = onAddNode) })
        }

        if (state.nodes.isEmpty()) {
            item { EmptyState(onAddNode) }
        }

        items(state.nodes, key = { it.id }) { node ->
            NodeCard(
                node = node,
                stats = state.statsOf(node.id),
                running = state.running,
                busy = state.busy.contains(node.id),
                nextInMs = state.nextRunInMs[node.id] ?: 0L,
                onEdit = { onEditNode(node) },
                onToggle = { onToggleNode(node, it) },
                onRunNow = { onRunNow(node) },
            )
            Spacer(Modifier.height(10.dp))
        }

        item {
            Spacer(Modifier.height(10.dp))
            Hairline(Ink.LineSoft)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("DATA", style = monoStyle(9, Ink.Dim, spacing = 1.4f))
                Spacer(Modifier.weight(1f))
                Text(Fmt.bytes(state.totalBytes), style = monoStyle(9, Ink.Dim))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("RUNTIME", style = monoStyle(9, Ink.Dim, spacing = 1.4f))
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.running) "FOREGROUND SERVICE · WAKELOCK" else "STOPPED",
                    style = monoStyle(9, Ink.Dim),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("BUILD", style = monoStyle(9, Ink.Dim, spacing = 1.4f))
                Spacer(Modifier.weight(1f))
                Text(BuildConfig.VERSION_NAME, style = monoStyle(9, Ink.Dim))
            }
        }
    }
}

@Composable
private fun HeaderBlock(state: FarmState, onToggleFarm: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PHONE FARM", style = monoStyle(10, Ink.Ash, FontWeight.Medium, 3f))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (state.running) "Farm online" else "Farm idle",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink.Chalk,
                )
            }
            PFSwitch(checked = state.running, onChange = onToggleFarm)
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Ink.Line, RoundedCornerShape(4.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Session uptime", Ink.Dim)
                Spacer(Modifier.weight(1f))
                PulseDot(state.running)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.running) "LIVE" else "PAUSED",
                    style = monoStyle(9, if (state.running) Ink.Chalk else Ink.Dim, FontWeight.Medium, 1.4f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                Fmt.uptime(state.uptimeMs),
                style = MaterialTheme.typography.displayLarge,
                color = Ink.Chalk,
            )
            Spacer(Modifier.height(14.dp))
            Row {
                StatCell("Nodes", "${state.enabledCount}/${state.nodes.size}", Modifier.weight(1f))
                StatCell("Cycles", "${state.totalCycles}", Modifier.weight(1f))
                StatCell("Errors", "${state.totalFailures}", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.governor.charging) "CHARGING" else "BATTERY ${state.governor.batteryPct}%",
                style = monoStyle(9, Ink.Dim, spacing = 1.4f),
            )
            Spacer(Modifier.width(10.dp))
            Text("·", style = monoStyle(9, Ink.Dim))
            Spacer(Modifier.width(10.dp))
            Text(
                "THERMAL ${state.governor.thermalLabel.uppercase()}",
                style = monoStyle(9, Ink.Dim, spacing = 1.4f),
            )
        }
    }
}

@Composable
private fun NodeCard(
    node: FarmNode,
    stats: com.phonefarm.app.data.NodeStatsView,
    running: Boolean,
    busy: Boolean,
    nextInMs: Long,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
) {
    val live = running && node.enabled
    val status = when {
        !node.enabled -> "DISARMED"
        !running -> "IDLE"
        busy -> "RUNNING"
        stats.error != null -> "ERR ${stats.status}".trim()
        else -> "NEXT ${Fmt.countdown(nextInMs)}"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Ink.Layer)
            .border(1.dp, if (busy) Ink.Line else Ink.LineSoft, RoundedCornerShape(4.dp))
            .clickable { onEdit() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(live)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    node.name.ifBlank { node.host },
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    node.host,
                    style = monoStyle(10, Ink.Dim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${stats.cycles}", style = monoStyle(16, Ink.Chalk, FontWeight.Medium))
                Text("CYCLES", style = monoStyle(8, Ink.Dim, spacing = 1.2f))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeChip(node.mode.label)
            Spacer(Modifier.width(8.dp))
            Text(Fmt.every(node.intervalSec), style = monoStyle(9, Ink.Ash))
            Spacer(Modifier.weight(1f))
            Text(
                status,
                style = monoStyle(9, if (stats.error != null) Ink.Paper else Ink.Ash, spacing = 0.8f),
            )
        }

        Spacer(Modifier.height(10.dp))
        Sparkline(stats.history, live)
        Spacer(Modifier.height(12.dp))
        Hairline(Ink.LineSoft)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniButton("Run now", onClick = onRunNow)
            Spacer(Modifier.width(8.dp))
            MiniButton(if (node.enabled) "Disarm" else "Arm") { onToggle(!node.enabled) }
            Spacer(Modifier.weight(1f))
            Text(
                if (stats.latencyMs > 0) "${stats.latencyMs} ms" else "—",
                style = monoStyle(9, Ink.Dim),
            )
        }
    }
}

@Composable
private fun ModeChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(text, style = monoStyle(8, Ink.Ash, FontWeight.Medium, 1.2f))
    }
}

@Composable
private fun EmptyState(onAddNode: () -> Unit) {
    Panel {
        Text("No nodes yet", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add a URL and Phone Farm keeps it alive around the clock. LIGHT nodes are the cheapest — " +
                "they speak HTTP only and never start a browser engine.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Ash,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Add first node", onClick = onAddNode)
    }
}

@Composable
private fun UpdateBanner(update: UpdateManager.UpdateState, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Ink.Paper, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(true)
        Spacer(Modifier.width(10.dp))
        Text("UPDATE", style = monoStyle(9, Ink.Paper, FontWeight.Medium, 1.6f))
        Spacer(Modifier.width(8.dp))
        Text(
            when (update) {
                is UpdateManager.UpdateState.Available -> "${update.release.version} available"
                is UpdateManager.UpdateState.Downloading -> "downloading ${update.progress}%"
                is UpdateManager.UpdateState.Ready -> "${update.release.version} ready to install"
                is UpdateManager.UpdateState.Installing -> "installing"
                else -> ""
            },
            style = monoStyle(10, Ink.Ash),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text("OPEN", style = monoStyle(9, Ink.Chalk, FontWeight.Medium, 1.4f))
    }
}

// ---------------------------------------------------------------------------- activity

@Composable
fun ActivityScreen(logs: List<LogEntry>, onClear: () -> Unit) {
    var filter by remember { mutableStateOf<LogLevel?>(null) }
    val shown = remember(logs, filter) {
        if (filter == null) logs else logs.filter { it.level == filter }
    }

    Column(Modifier.fillMaxSize().background(Ink.Void)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("ACTIVITY", style = monoStyle(10, Ink.Ash, FontWeight.Medium, 3f))
            Spacer(Modifier.height(6.dp))
            Text("Event log", style = MaterialTheme.typography.headlineMedium, color = Ink.Chalk)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip("All", filter == null) { filter = null }
                Spacer(Modifier.width(6.dp))
                FilterChip("Info", filter == LogLevel.INFO) { filter = LogLevel.INFO }
                Spacer(Modifier.width(6.dp))
                FilterChip("Warn", filter == LogLevel.WARN) { filter = LogLevel.WARN }
                Spacer(Modifier.width(6.dp))
                FilterChip("Error", filter == LogLevel.ERROR) { filter = LogLevel.ERROR }
                Spacer(Modifier.weight(1f))
                MiniButton("Clear", onClick = onClear)
            }
            Spacer(Modifier.height(12.dp))
            Hairline(Ink.LineSoft)
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO EVENTS", style = monoStyle(10, Ink.Dim, spacing = 2f))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                items(shown.asReversed(), key = { "${it.at}-${it.source}-${it.message.hashCode()}" }) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) Ink.Paper else Color.Transparent)
            .border(1.dp, if (selected) Ink.Paper else Ink.Line, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(
            label.uppercase(),
            style = monoStyle(9, if (selected) Ink.Void else Ink.Ash, FontWeight.Medium, 1.2f),
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(Fmt.clock(entry.at), style = monoStyle(9, Ink.Dim), modifier = Modifier.width(58.dp))
        Text(
            entry.level.name.take(1),
            style = monoStyle(
                9,
                when (entry.level) {
                    LogLevel.INFO -> Ink.Dim
                    LogLevel.WARN -> Ink.Ash
                    LogLevel.ERROR -> Ink.Paper
                },
                FontWeight.Medium,
            ),
            modifier = Modifier.width(14.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(entry.source.uppercase(), style = monoStyle(8, Ink.Dim, spacing = 1.2f))
            Spacer(Modifier.height(2.dp))
            Text(
                entry.message,
                style = monoStyle(
                    11,
                    if (entry.level == LogLevel.ERROR) Ink.Chalk else Ink.Ash,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------- settings

@Composable
fun SettingsScreen(
    state: FarmState,
    update: UpdateManager.UpdateState,
    onSettings: (Settings) -> Unit,
) {
    val context = LocalContext.current
    val settings = state.settings
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val exempt = power?.isIgnoringBatteryOptimizations(context.packageName) == true

    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importNote by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("SETTINGS", style = monoStyle(10, Ink.Ash, FontWeight.Medium, 3f))
        Spacer(Modifier.height(6.dp))
        Text("Control room", style = MaterialTheme.typography.headlineMedium, color = Ink.Chalk)

        SectionHeader("Updates")
        Panel(padding = 14.dp) {
            ToggleRow(
                "Auto update",
                "Quietly check GitHub Releases and pull new builds",
                settings.autoUpdate,
            ) { onSettings(settings.copy(autoUpdate = it)) }
            Hairline(Ink.LineSoft)
            ToggleRow(
                "Download automatically",
                "Fetch the APK in the background, then ask to install",
                settings.autoDownload,
            ) { onSettings(settings.copy(autoDownload = it)) }
            Hairline(Ink.LineSoft)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Channel", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (settings.channel == UpdateChannel.BETA) "Stable + pre-releases" else "Stable builds only",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.Dim,
                    )
                }
                FilterChip("Stable", settings.channel == UpdateChannel.STABLE) {
                    onSettings(settings.copy(channel = UpdateChannel.STABLE))
                }
                Spacer(Modifier.width(6.dp))
                FilterChip("Beta", settings.channel == UpdateChannel.BETA) {
                    onSettings(settings.copy(channel = UpdateChannel.BETA))
                }
            }
            Hairline(Ink.LineSoft)
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Version", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        updateSummary(update),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (update is UpdateManager.UpdateState.Failed) Ink.Paper else Ink.Dim,
                    )
                }
                MiniButton(
                    "Check",
                    active = update !is UpdateManager.UpdateState.Checking,
                ) { UpdateManager.checkForUpdates(manual = true) }
            }

            if (update is UpdateManager.UpdateState.Downloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { update.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Ink.Paper,
                    trackColor = Ink.Line,
                )
            }

            when (update) {
                is UpdateManager.UpdateState.Ready -> {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Install ${update.release.version}") { UpdateManager.install(update.file) }
                }
                is UpdateManager.UpdateState.Available -> {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Download ${update.release.version}") { UpdateManager.download(update.release) }
                }
                else -> Unit
            }

            if (update is UpdateManager.UpdateState.Available || update is UpdateManager.UpdateState.Ready) {
                Spacer(Modifier.height(8.dp))
                GhostButton("Release notes") { UpdateManager.openReleasePage() }
            }
        }

        SectionHeader("Runtime")
        Panel(padding = 14.dp) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Parallel nodes", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(3.dp))
                    Text("How many nodes may run in the same second", style = MaterialTheme.typography.bodySmall, color = Ink.Dim)
                }
                Stepper(settings.maxConcurrent, 1..8, { onSettings(settings.copy(maxConcurrent = it)) })
            }
            Hairline(Ink.LineSoft)
            ToggleRow(
                "Power aware",
                "Throttle on heat or low battery instead of stopping",
                settings.powerAware,
            ) { onSettings(settings.copy(powerAware = it)) }
            Hairline(Ink.LineSoft)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Battery floor", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(3.dp))
                    Text("Throttle below this charge level", style = MaterialTheme.typography.bodySmall, color = Ink.Dim)
                }
                Stepper(settings.batteryFloor, 5..50, { onSettings(settings.copy(batteryFloor = it)) }, suffix = "%")
            }
            Hairline(Ink.LineSoft)
            ToggleRow(
                "Hold wake lock",
                "Keeps the CPU awake while nodes are scheduled",
                settings.wakeLock,
            ) { onSettings(settings.copy(wakeLock = it)) }
        }

        SectionHeader("Always on")
        Panel(padding = 14.dp) {
            ToggleRow(
                "Resume on launch",
                "Start the farm automatically when the app opens",
                settings.resumeOnLaunch,
            ) { onSettings(settings.copy(resumeOnLaunch = it)) }
            Hairline(Ink.LineSoft)
            ToggleRow(
                "Restore after reboot",
                "Bring the farm back up after a restart or an update",
                settings.bootRestore,
            ) { onSettings(settings.copy(bootRestore = it)) }
            Hairline(Ink.LineSoft)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { openBatterySettings(context) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Battery optimisation", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (exempt) "Exempt · the system will not sleep the farm" else "Restricted · the system may pause the farm",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exempt) Ink.Dim else Ink.Ash,
                    )
                }
                Text("OPEN", style = monoStyle(9, Ink.Ash, FontWeight.Medium, 1.4f))
            }
        }

        SectionHeader("Configuration")
        Panel(padding = 14.dp) {
            GhostButton("Share configuration") {
                runCatching {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Phone Farm configuration")
                        .putExtra(Intent.EXTRA_TEXT, FarmEngine.exportConfig())
                    context.startActivity(Intent.createChooser(send, "Share configuration"))
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("Import configuration") {
                importNote = null
                showImport = true
            }
            importNote?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = monoStyle(10, Ink.Ash))
            }
        }

        SectionHeader("About")
        Panel(padding = 14.dp) {
            InfoRow("Version", "${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}")
            Hairline(Ink.LineSoft)
            InfoRow("Repository", BuildConfig.REPOSITORY)
            Hairline(Ink.LineSoft)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { UpdateManager.openReleasePage() }
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Releases", style = MaterialTheme.typography.bodyMedium, color = Ink.Ash, modifier = Modifier.weight(1f))
                Text("GITHUB", style = monoStyle(9, Ink.Ash, FontWeight.Medium, 1.4f))
            }
            Hairline(Ink.LineSoft)
            Text(
                "No analytics. No accounts. No telemetry. Node configuration, statistics and logs stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Dim,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("MIT LICENSE", style = monoStyle(9, Ink.Dim, spacing = 1.4f))
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            containerColor = Ink.Layer2,
            title = { Text("Import configuration", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                    textStyle = monoStyle(11, Ink.Chalk),
                    placeholder = { Text("Paste Phone Farm JSON", style = monoStyle(11, Ink.Dim)) },
                    shape = RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ink.Paper,
                        unfocusedBorderColor = Ink.Line,
                        cursorColor = Ink.Paper,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = runCatching { FarmEngine.importConfig(importText) }
                    importNote = result.fold(
                        onSuccess = { "Imported $it node(s)" },
                        onFailure = { "Import failed · ${it.message}" },
                    )
                    if (result.isSuccess) {
                        showImport = false
                        importText = ""
                    }
                }) {
                    Text("Apply", color = Ink.Chalk, style = monoStyle(10, Ink.Chalk, FontWeight.Medium, 1.4f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) {
                    Text("Cancel", color = Ink.Dim, style = monoStyle(10, Ink.Dim, FontWeight.Medium, 1.4f))
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
            Spacer(Modifier.height(3.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Ink.Dim)
        }
        Spacer(Modifier.width(12.dp))
        PFSwitch(checked = checked, onChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink.Ash, modifier = Modifier.weight(1f))
        Text(value, style = monoStyle(9, Ink.Dim))
    }
}

// ---------------------------------------------------------------------------- update sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(state: UpdateManager.UpdateState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.Void,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text("RELEASES · GITHUB", style = monoStyle(10, Ink.Ash, FontWeight.Medium, 2.4f))
            Spacer(Modifier.height(8.dp))
            Text("Software update", style = MaterialTheme.typography.headlineMedium, color = Ink.Chalk)
            Spacer(Modifier.height(16.dp))

            when (state) {
                is UpdateManager.UpdateState.Available -> {
                    Text(state.release.version, style = monoStyle(28, Ink.Chalk, FontWeight.Light))
                    Spacer(Modifier.height(4.dp))
                    Text("BUILD ${state.release.published.take(10)}", style = monoStyle(9, Ink.Dim, spacing = 1.4f))
                    ReleaseNotes(state.release.notes)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Download & install") { UpdateManager.download(state.release) }
                }

                is UpdateManager.UpdateState.Downloading -> {
                    Text("Downloading ${state.release.version}", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Ink.Paper,
                        trackColor = Ink.Line,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("${state.progress}%", style = monoStyle(11, Ink.Ash))
                }

                is UpdateManager.UpdateState.Ready -> {
                    Text(state.release.version, style = monoStyle(28, Ink.Chalk, FontWeight.Light))
                    Spacer(Modifier.height(6.dp))
                    Text("Downloaded and signature-checked.", style = MaterialTheme.typography.bodySmall, color = Ink.Ash)
                    ReleaseNotes(state.release.notes)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Install now") { UpdateManager.install(state.file) }
                }

                is UpdateManager.UpdateState.Installing -> {
                    Text("Installing", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(8.dp))
                    Text("Confirm the system installer prompt.", style = MaterialTheme.typography.bodySmall, color = Ink.Ash)
                }

                is UpdateManager.UpdateState.UpToDate -> {
                    Text("Up to date", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(8.dp))
                    Text("You are on ${state.version}, the newest build on this channel.", style = MaterialTheme.typography.bodySmall, color = Ink.Ash)
                }

                is UpdateManager.UpdateState.Failed -> {
                    Text("Update failed", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = Ink.Ash)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Try again") { UpdateManager.checkForUpdates(manual = true) }
                }

                else -> {
                    Text("Checking for updates", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                }
            }

            Spacer(Modifier.height(12.dp))
            GhostButton("Open release page") { UpdateManager.openReleasePage() }
            Spacer(Modifier.height(8.dp))
            GhostButton("Close", onClick = onDismiss)
        }
    }
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    Spacer(Modifier.height(16.dp))
    Text("WHAT CHANGED", style = monoStyle(9, Ink.Dim, spacing = 1.6f))
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Ink.LineSoft, RoundedCornerShape(3.dp))
            .padding(12.dp),
    ) {
        Text(notes, style = monoStyle(11, Ink.Ash))
    }
}

// ---------------------------------------------------------------------------- helpers

private fun updateSummary(state: UpdateManager.UpdateState): String = when (state) {
    is UpdateManager.UpdateState.Idle -> "Not checked yet"
    is UpdateManager.UpdateState.Checking -> "Checking GitHub Releases…"
    is UpdateManager.UpdateState.UpToDate -> "Up to date · ${state.version}"
    is UpdateManager.UpdateState.Available -> "${state.release.version} available"
    is UpdateManager.UpdateState.Downloading -> "Downloading ${state.progress}%"
    is UpdateManager.UpdateState.Ready -> "${state.release.version} ready to install"
    is UpdateManager.UpdateState.Installing -> "Installing…"
    is UpdateManager.UpdateState.Failed -> state.message
}

private fun needsAttention(state: UpdateManager.UpdateState): Boolean = when (state) {
    is UpdateManager.UpdateState.Available,
    is UpdateManager.UpdateState.Downloading,
    is UpdateManager.UpdateState.Ready,
    is UpdateManager.UpdateState.Installing,
    -> true
    else -> false
}

private fun openBatterySettings(context: Context) {
    runCatching {
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
