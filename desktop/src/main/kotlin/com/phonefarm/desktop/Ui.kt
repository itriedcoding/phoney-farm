package com.phonefarm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Void = Color(0xFF000000)
private val Card = Color(0xFF0B0B0B)
private val Chalk = Color(0xFFF5F5F5)
private val Ash = Color(0xFF8A8A8A)
private val LineColor = Color(0xFF242424)
private val Mono = FontFamily.Monospace

@Composable
fun DesktopApp(core: FarmCore, token: String) {
    val state by core.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("300") }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val address = "http://${lanAddresses().firstOrNull() ?: "127.0.0.1"}:${ApiServer.DEFAULT_PORT}"

    MaterialTheme(colorScheme = darkColorScheme(primary = Chalk)) {
        Column(Modifier.fillMaxSize().background(Void).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (state.running) Chalk else Color(0xFF3A3A3A), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "PHONE FARM",
                        color = Chalk,
                        fontSize = 15.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.2.sp,
                    )
                    Text(
                        "DESKTOP AGENT · ${FarmCore.VERSION}",
                        color = Ash,
                        fontSize = 9.sp,
                        fontFamily = Mono,
                        letterSpacing = 1.4.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.running) "${state.nodes.count { it.enabled }} NODES ACTIVE" else "IDLE",
                    color = Ash,
                    fontSize = 9.sp,
                    fontFamily = Mono,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.width(14.dp))
                if (state.running) {
                    Action("STOP", primary = false) { core.stop() }
                } else {
                    Action("START FARM", primary = true) { core.start() }
                }
            }

            Spacer(Modifier.height(14.dp))
            Rule()
            Spacer(Modifier.height(14.dp))

            Row {
                PairCard("PAIR FROM PHONE", address, Modifier.weight(1f)) {
                    clipboard.setText(AnnotatedString(address))
                }
                Spacer(Modifier.width(12.dp))
                PairCard("ACCESS TOKEN", token, Modifier.weight(1f)) {
                    clipboard.setText(AnnotatedString(token))
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "ADD NODE",
                color = Ash,
                fontSize = 9.sp,
                fontFamily = Mono,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(name, { name = it }, Modifier.weight(1f), "name")
                Spacer(Modifier.width(8.dp))
                Field(url, { url = it }, Modifier.weight(2.2f), "https://example.com")
                Spacer(Modifier.width(8.dp))
                Field(interval, { interval = it }, Modifier.width(90.dp), "300")
                Spacer(Modifier.width(8.dp))
                Action("ADD", primary = true) {
                    runCatching {
                        core.addNode(name, url, interval.toIntOrNull() ?: 300)
                    }.onSuccess {
                        name = ""
                        url = ""
                        interval = "300"
                        error = null
                    }.onFailure {
                        error = it.message ?: "could not add node"
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Ash, fontSize = 11.sp, fontFamily = Mono)
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Head("NODE", Modifier.weight(2.4f))
                Head("CYCLES", Modifier.width(70.dp))
                Head("FAILS", Modifier.width(56.dp))
                Head("LATENCY", Modifier.width(80.dp))
                Head("LAST", Modifier.width(78.dp))
                Spacer(Modifier.width(196.dp))
            }
            Rule()

            if (state.nodes.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No nodes yet. Add one above, or pair the phone and send presets from the Live tab.",
                        color = Ash,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(state.nodes, key = { it.id }) { node ->
                        NodeRow(node, state.stats[node.id], core)
                        Rule()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("ACTIVITY", color = Ash, fontSize = 9.sp, fontFamily = Mono, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(Card, RoundedCornerShape(4.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(4.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                state.log.takeLast(60).forEach { line ->
                    Text(line, color = Ash, fontSize = 10.sp, fontFamily = Mono)
                }
                if (state.log.isEmpty()) {
                    Text("waiting for the first cycle", color = Color(0xFF4A4A4A), fontSize = 10.sp, fontFamily = Mono)
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: DesktopNode, stat: FarmCore.NodeStat?, core: FarmCore) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2.4f)) {
            Text(
                node.name,
                color = if (node.enabled) Chalk else Color(0xFF5C5C5C),
                fontSize = 12.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                node.url,
                color = Ash,
                fontSize = 9.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${stat?.cycles ?: 0}",
            modifier = Modifier.width(70.dp),
            color = if (node.enabled) Chalk else Ash,
            fontSize = 11.sp,
            fontFamily = Mono,
        )
        Text(
            "${stat?.failures ?: 0}",
            modifier = Modifier.width(56.dp),
            color = if ((stat?.failures ?: 0) > 0) Chalk else Ash,
            fontSize = 11.sp,
            fontFamily = Mono,
        )
        Text(
            if (stat == null || stat.lastAt == 0L) "—" else "${stat.latencyMs}ms",
            modifier = Modifier.width(80.dp),
            color = Ash,
            fontSize = 11.sp,
            fontFamily = Mono,
        )
        Text(
            if (stat == null || stat.lastAt == 0L) "—" else stamp(stat.lastAt),
            modifier = Modifier.width(78.dp),
            color = Ash,
            fontSize = 11.sp,
            fontFamily = Mono,
        )
        Spacer(Modifier.width(12.dp))
        Row(Modifier.width(184.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Action(if (node.enabled) "PAUSE" else "RESUME", primary = false) { core.setEnabled(node.id, !node.enabled) }
            Spacer(Modifier.width(6.dp))
            Action("RUN", primary = false) { core.runNow(node.id) }
            Spacer(Modifier.width(6.dp))
            Action("DEL", primary = false) { core.removeNode(node.id) }
        }
    }
}

@Composable
private fun PairCard(label: String, value: String, modifier: Modifier = Modifier, onCopy: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .background(Card, shape)
            .border(1.dp, LineColor, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = Ash, fontSize = 9.sp, fontFamily = Mono, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                color = Chalk,
                fontSize = 13.sp,
                fontFamily = Mono,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "COPY",
                color = Ash,
                fontSize = 9.sp,
                fontFamily = Mono,
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .clickable { onCopy() }
                    .padding(start = 10.dp, top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .background(Void, shape)
            .border(1.dp, LineColor, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Color(0xFF4A4A4A), fontSize = 11.sp, fontFamily = Mono)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Chalk, fontSize = 11.sp, fontFamily = Mono),
            cursorBrush = SolidColor(Chalk),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Action(label: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .background(if (primary) Chalk else Color.Transparent, shape)
            .border(1.dp, if (primary) Chalk else LineColor, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color = if (primary) Void else Chalk,
            fontSize = 9.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun Head(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Ash,
        fontSize = 9.sp,
        fontFamily = Mono,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LineColor))
}

private fun stamp(time: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))
