package com.phonefarm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.engine.FarmEngine
import com.phonefarm.app.update.UpdateManager

enum class FarmTab(val label: String) {
    FARM("Farm"),
    LIVE("Live"),
    AI("AI"),
    ACTIVITY("Log"),
    SETTINGS("Setup"),
}

@Composable
fun FarmApp() {
    val state by FarmEngine.state.collectAsStateWithLifecycle()
    val logs by FarmEngine.logs.collectAsStateWithLifecycle()
    val update by UpdateManager.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(FarmTab.FARM) }
    var editorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    var watching by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = Ink.Void) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    FarmTab.FARM -> FarmScreen(
                        state = state,
                        update = update,
                        onToggleFarm = { run ->
                            if (run) FarmEngine.startFarm("dashboard") else FarmEngine.stopFarm("dashboard")
                        },
                        onAddNode = { editorTarget = "new" },
                        onEditNode = { editorTarget = it.id },
                        onToggleNode = { node, enabled -> FarmEngine.setEnabled(node.id, enabled) },
                        onRunNow = { FarmEngine.runNow(it.id) },
                        onOpenUpdates = { showUpdates = true },
                    )

                    FarmTab.LIVE -> LiveScreen(
                        state = state,
                        onAddPreset = { preset ->
                            FarmEngine.upsert(
                                FarmNode(
                                    name = preset.name,
                                    url = preset.url,
                                    mode = preset.mode,
                                    intervalSec = preset.intervalSec,
                                ),
                            )
                        },
                        onWatch = { watching = it.id },
                    )

                    FarmTab.AI -> AiScreen()

                    FarmTab.ACTIVITY -> ActivityScreen(
                        logs = logs,
                        onClear = { FarmEngine.clearLogs() },
                    )

                    FarmTab.SETTINGS -> SettingsScreen(
                        state = state,
                        update = update,
                        onSettings = { next: com.phonefarm.app.data.Settings -> FarmEngine.updateSettings(next) },
                    )
                }
            }
            BottomBar(tab) { tab = it }
        }
    }

    if (editorTarget != null) {
        val editing = if (editorTarget == "new") {
            null
        } else {
            state.nodes.firstOrNull { it.id == editorTarget }
        }
        NodeEditorSheet(
            node = editing,
            onDismiss = { editorTarget = null },
            onSave = {
                FarmEngine.upsert(it)
                editorTarget = null
            },
            onDelete = {
                FarmEngine.delete(it.id)
                editorTarget = null
            },
        )
    }

    if (watching != null) {
        val node = state.nodes.firstOrNull { it.id == watching }
        if (node != null) {
            NodeViewer(node) { watching = null }
        }
    }

    if (showUpdates) {
        UpdateSheet(update) { showUpdates = false }
    }
}

@Composable
private fun BottomBar(current: FarmTab, onSelect: (FarmTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink.Void).navigationBarsPadding()) {
        Hairline(Ink.Line)
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            FarmTab.entries.forEach { item ->
                val selected = item == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(item) }
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .height(2.dp)
                            .width(if (selected) 18.dp else 0.dp)
                            .background(Ink.Paper),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        item.label.uppercase(),
                        style = monoStyle(8, if (selected) Ink.Chalk else Ink.Dim, FontWeight.Medium, 1.4f),
                    )
                }
            }
        }
    }
}
