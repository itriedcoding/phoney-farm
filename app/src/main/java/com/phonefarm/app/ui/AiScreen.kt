package com.phonefarm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonefarm.app.ai.AiCopilot
import com.phonefarm.app.ai.AiPrefs
import kotlinx.coroutines.launch

@Composable
fun AiScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AiCopilot.state.collectAsStateWithLifecycle()
    val log by AiCopilot.log.collectAsStateWithLifecycle()

    var endpoint by remember { mutableStateOf(AiPrefs.endpoint(context)) }
    var model by remember { mutableStateOf(AiPrefs.model(context)) }
    var key by remember { mutableStateOf(AiPrefs.apiKey(context)) }
    var supervisor by remember { mutableStateOf(AiPrefs.supervisor(context)) }
    var showConfig by remember { mutableStateOf(!AiPrefs.configured(context)) }
    var prompt by remember { mutableStateOf("") }

    val configured = AiPrefs.configured(context)
    val thinking = state is AiCopilot.AiState.Thinking

    LazyColumn(
        Modifier.background(Ink.Void),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("AI", style = monoStyle(10, Ink.Ash, FontWeight.Medium, 3f))
            Spacer(Modifier.height(6.dp))
            Text("Operator", style = MaterialTheme.typography.headlineMedium, color = Ink.Chalk)
            Spacer(Modifier.height(4.dp))
            Text(
                "An OpenAI-compatible model with real tools: it adds, edits, arms and runs your nodes. Your key stays on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Dim,
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, if (configured) Ink.Line else Ink.Paper, RoundedCornerShape(4.dp))
                    .background(Ink.Layer)
                    .clickable { showConfig = !showConfig }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseDot(configured)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (configured) "MODEL CONNECTED" else "NO API KEY",
                        style = monoStyle(9, if (configured) Ink.Ash else Ink.Paper, FontWeight.Medium, 1.4f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (configured) "${AiPrefs.model(context)} · ${AiPrefs.endpoint(context)}" else "Tap to configure endpoint, model and key",
                        style = monoStyle(9, Ink.Dim),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(if (showConfig) "HIDE" else "CONFIG", style = monoStyle(9, Ink.Ash, FontWeight.Medium, 1.4f))
            }
        }

        if (showConfig) {
            item {
                Spacer(Modifier.height(10.dp))
                Panel {
                    FarmField(
                        label = "Endpoint",
                        value = endpoint,
                        onChange = { endpoint = it },
                        keyboardType = KeyboardType.Uri,
                        placeholder = AiPrefs.DEFAULT_ENDPOINT,
                    )
                    Spacer(Modifier.height(12.dp))
                    FarmField(
                        label = "Model",
                        value = model,
                        onChange = { model = it },
                        placeholder = AiPrefs.DEFAULT_MODEL,
                    )
                    Spacer(Modifier.height(12.dp))
                    FarmField(
                        label = "API key",
                        value = key,
                        onChange = { key = it },
                        keyboardType = KeyboardType.Password,
                        secret = true,
                        placeholder = "sk-…",
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Autonomous supervisor", style = MaterialTheme.typography.bodyMedium, color = Ink.Chalk)
                            Text(
                                "Runs a supervision pass every 30 minutes while the farm is live",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink.Dim,
                            )
                        }
                        PFSwitch(checked = supervisor, onChange = { supervisor = it })
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Save settings") {
                        AiPrefs.save(context, endpoint, model, key, supervisor)
                        showConfig = false
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            FarmField(
                label = "Instruction",
                value = prompt,
                onChange = { prompt = it },
                singleLine = false,
                minLines = 3,
                placeholder = "Keep the news preset warm every 4 minutes and watch Wikipedia hourly",
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = if (thinking) "Working…" else "Run operator",
                enabled = !thinking && configured && prompt.isNotBlank(),
            ) {
                val instruction = prompt.trim()
                prompt = ""
                scope.launch { AiCopilot.operate(context, instruction) }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniButton("Fix failing nodes") {
                    scope.launch { AiCopilot.operate(context, "Look at the farm, fix every failing node and report what you changed.") }
                }
                MiniButton("Tune intervals") {
                    scope.launch { AiCopilot.operate(context, "Tune each node's interval for a 24/7 phone farm: light on battery, no busy loops.") }
                }
            }
        }

        item {
            state.let { current ->
                val line = when (current) {
                    is AiCopilot.AiState.Idle -> "IDLE"
                    is AiCopilot.AiState.Thinking -> "THINKING · STEP ${current.step}"
                    is AiCopilot.AiState.Answer -> "ANSWER READY"
                    is AiCopilot.AiState.Failed -> "FAILED · ${current.message}"
                    else -> ""
                }
                Spacer(Modifier.height(14.dp))
                Text(line, style = monoStyle(9, if (current is AiCopilot.AiState.Failed) Ink.Paper else Ink.Dim, spacing = 1.4f))
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            HaLine()
        }

        if (log.isEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "No operations yet. Every tool call the model makes appears here verbatim.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.Dim,
                )
            }
        }

        items(log.size) { index ->
            val entry = log[index]
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(
                    entry.kind.uppercase(),
                    style = monoStyle(8, if (entry.kind == "error") Ink.Paper else Ink.Dim, spacing = 1.2f),
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    entry.text,
                    style = monoStyle(11, if (entry.kind == "error") Ink.Chalk else Ink.Ash),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            if (log.isNotEmpty()) {
                MiniButton("Clear transcript") { AiCopilot.clear() }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!AiPrefs.configured(context)) showConfig = true
    }
}

@Composable
private fun HaLine() {
    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Ink.LineSoft))
}
