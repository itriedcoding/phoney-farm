package com.phonefarm.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phonefarm.app.data.FarmNode
import com.phonefarm.app.engine.HttpRunner

/** A real, attached WebView so the page is interactively live rather than a still frame. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NodeViewer(node: FarmNode, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var web by remember { mutableStateOf<WebView?>(null) }

        Box(Modifier.fillMaxSize().background(Ink.Void)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.userAgentString =
                            if (node.desktopUa) HttpRunner.UA_DESKTOP else HttpRunner.UA_MOBILE
                        loadUrl(node.url)
                        web = this
                    }
                },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Ink.Void.copy(alpha = 0.94f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(true)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            node.name.ifBlank { node.host },
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink.Chalk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "LIVE · ${node.mode.label} · ${node.host}",
                            style = monoStyle(8, Ink.Dim, spacing = 1.2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MiniButton("Reload") { web?.reload() }
                    Spacer(Modifier.width(6.dp))
                    MiniButton("Close") { onDismiss() }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                runCatching {
                    web?.stopLoading()
                    web?.destroy()
                }
                web = null
            }
        }
    }
}
