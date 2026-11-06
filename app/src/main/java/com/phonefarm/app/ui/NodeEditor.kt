package com.phonefarm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.data.NodeMode
import com.phonefarm.app.data.Profiles
import com.phonefarm.app.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorSheet(
    node: FarmNode?,
    onDismiss: () -> Unit,
    onSave: (FarmNode) -> Unit,
    onDelete: (FarmNode) -> Unit,
) {
    var draft by remember(node?.id) { mutableStateOf(node ?: FarmNode()) }
    var error by remember { mutableStateOf<String?>(null) }
    val profiles by Profiles.profiles.collectAsStateWithLifecycle()
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
        ) {
            Label(if (node == null) "New node" else "Edit node", Ink.Dim)
            Spacer(Modifier.height(6.dp))
            Text(
                draft.name.ifBlank { "Farm slot" },
                style = MaterialTheme.typography.headlineMedium,
                color = Ink.Chalk,
            )
            Spacer(Modifier.height(20.dp))

            FarmField(
                label = "Label",
                value = draft.name,
                onChange = { draft = draft.copy(name = it) },
                placeholder = "Optional name",
            )
            Spacer(Modifier.height(14.dp))
            FarmField(
                label = "Target URL",
                value = draft.url,
                onChange = { draft = draft.copy(url = it) },
                keyboardType = KeyboardType.Uri,
                placeholder = "https://example.com",
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = monoStyle(10, Ink.Paper))
            }

            Spacer(Modifier.height(18.dp))
            Label("Execution mode", Ink.Dim)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NodeMode.entries.forEach { mode ->
                    ModeOption(mode, mode == draft.mode) { draft = draft.copy(mode = mode) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(draft.mode.hint, style = MaterialTheme.typography.bodySmall, color = Ink.Dim)

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Cycle interval", Ink.Dim)
                Spacer(Modifier.weight(1f))
                Text(Fmt.every(draft.intervalSec).uppercase(), style = monoStyle(10, Ink.Chalk))
            }
            Slider(
                value = draft.intervalSec.toFloat(),
                onValueChange = { draft = draft.copy(intervalSec = it.toInt()) },
                valueRange = FarmNode.MIN_INTERVAL.toFloat()..FarmNode.MAX_INTERVAL.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Ink.Paper,
                    activeTrackColor = Ink.Paper,
                    inactiveTrackColor = Ink.Line,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("Jitter", Ink.Dim)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Randomises start times so nodes never fire together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.Dim,
                    )
                }
                Stepper(draft.jitterSec, 0..120, { draft = draft.copy(jitterSec = it) }, suffix = "s")
            }

            Spacer(Modifier.height(16.dp))
            Label("Device", Ink.Dim)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                profiles.take(3).forEach { profile ->
                    Option(
                        text = profile.name,
                        selected = draft.profileId.ifBlank { Profiles.primary().id } == profile.id,
                        modifier = Modifier.weight(1f),
                    ) {
                        draft = draft.copy(profileId = profile.id)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            ToggleLine("Desktop user agent", draft.desktopUa) { draft = draft.copy(desktopUa = it) }
            ToggleLine("Load images", draft.images) { draft = draft.copy(images = it) }
            ToggleLine("Armed", draft.enabled) { draft = draft.copy(enabled = it) }

            if (draft.mode == NodeMode.WEBVIEW) {
                Spacer(Modifier.height(16.dp))
                FarmField(
                    label = "Injected script (optional)",
                    value = draft.script,
                    onChange = { draft = draft.copy(script = it) },
                    singleLine = false,
                    minLines = 4,
                    placeholder = "document.body.dataset.keepAlive = '1'",
                )
            }

            Spacer(Modifier.height(22.dp))
            PrimaryButton(if (node == null) "Add node" else "Save node") {
                val url = draft.url.trim()
                val parsedHost = runCatching { java.net.URI(url).host }.getOrNull()
                val valid = (url.startsWith("http://") || url.startsWith("https://")) && !parsedHost.isNullOrBlank()
                if (!valid) {
                    error = "Target must be a full http:// or https:// URL"
                } else {
                    error = null
                    onSave(draft.copy(url = url))
                }
            }

            if (node != null) {
                Spacer(Modifier.height(10.dp))
                GhostButton("Delete node") { onDelete(node) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SheetHandle() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(38.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Ink.Line))
    }
}

@Composable
private fun ToggleLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Ash,
            modifier = Modifier.weight(1f),
        )
        PFSwitch(checked = checked, onChange = onChange)
    }
}

@Composable
private fun RowScope.ModeOption(mode: NodeMode, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) Ink.Paper else Color.Transparent)
            .border(1.dp, if (selected) Ink.Paper else Ink.Line, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            mode.label,
            style = monoStyle(10, if (selected) Ink.Void else Ink.Ash, FontWeight.Medium, 1f),
        )
    }
}
