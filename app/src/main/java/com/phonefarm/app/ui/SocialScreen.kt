package com.phonefarm.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonefarm.app.data.Social
import com.phonefarm.app.data.SocialAccount
import com.phonefarm.app.data.SocialAction
import com.phonefarm.app.data.SocialKind
import com.phonefarm.app.data.SocialProvider
import com.phonefarm.app.engine.SocialRunner
import com.phonefarm.app.util.Fmt

@Composable
fun SocialScreen() {
    val accounts by Social.accounts.collectAsStateWithLifecycle()
    val actions by Social.actions.collectAsStateWithLifecycle()
    val results by SocialRunner.results.collectAsStateWithLifecycle()
    val lines by SocialRunner.lines.collectAsStateWithLifecycle()

    var showAccount by remember { mutableStateOf(false) }
    var draftAccount by remember { mutableStateOf(SocialAccount()) }
    var draft by remember { mutableStateOf(SocialAction()) }
    var composeError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(Ink.Void),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text("SOCIAL", style = monoStyle(10, Ink.Ash, FontWeight.Medium, 3f))
                Spacer(Modifier.height(6.dp))
                Text("Automation desk", style = MaterialTheme.typography.headlineMedium, color = Ink.Chalk)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Accounts talk to their real APIs. Post, reply, like and repost on your own accounts, " +
                        "once or on a cadence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.Dim,
                )
            }
        }

        item {
            SectionHeader("Accounts", action = {
                MiniButton("Add", onClick = {
                    draftAccount = SocialAccount()
                    showAccount = true
                })
            })
        }

        if (accounts.isEmpty()) {
            item {
                Panel {
                    Text("No accounts connected", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Mastodon and Bluesky take an access token; Telegram a bot token; Discord a webhook URL. " +
                            "Everything is stored on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.Ash,
                    )
                }
            }
        }

        items(accounts, key = { it.id }) { account ->
            val check = results["verify:${account.id}"]
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(1.dp, Ink.LineSoft, RoundedCornerShape(4.dp))
                    .background(Ink.Layer, RoundedCornerShape(4.dp))
                    .padding(13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(account.enabled)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink.Chalk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(account.provider.label)
                                if (account.handle.isNotBlank()) append(" · ").append(account.handle)
                            },
                            style = monoStyle(9, Ink.Dim),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(account.provider.note, style = MaterialTheme.typography.bodySmall, color = Ink.Dim)
                check?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it.detail, style = monoStyle(9, if (it.ok) Ink.Ash else Ink.Paper))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniButton("Check", onClick = { SocialRunner.verify(account.id) })
                    Spacer(Modifier.width(8.dp))
                    MiniButton("Remove", onClick = { Social.removeAccount(account.id) })
                    Spacer(Modifier.weight(1f))
                    Text(account.provider.secretLabel.uppercase(), style = monoStyle(8, Ink.Dim, spacing = 1.2f))
                }
            }
        }

        item { SectionHeader("Compose") }

        item {
            Panel(padding = 14.dp) {
                if (accounts.isEmpty()) {
                    Text("Add an account first", style = MaterialTheme.typography.bodySmall, color = Ink.Dim)
                } else {
                    Label("Account", Ink.Dim)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accounts.take(3).forEach { account ->
                            Option(
                                text = account.title,
                                selected = draft.accountId == account.id,
                                modifier = Modifier.weight(1f),
                            ) {
                                draft = draft.copy(accountId = account.id)
                                if (!account.provider.canLike && (draft.kind == SocialKind.LIKE || draft.kind == SocialKind.REPOST)) {
                                    draft = draft.copy(kind = SocialKind.POST)
                                }
                            }
                        }
                    }

                    val selected = accounts.firstOrNull { it.id == draft.accountId }
                    Spacer(Modifier.height(14.dp))
                    Label("Action", Ink.Dim)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SocialKind.entries
                            .filter { kind ->
                                when (kind) {
                                    SocialKind.LIKE -> selected?.provider?.canLike != false
                                    SocialKind.REPOST -> selected?.provider?.canRepost != false
                                    else -> true
                                }
                            }
                            .forEach { kind ->
                                Option(kind.label, draft.kind == kind, Modifier.weight(1f)) {
                                    draft = draft.copy(kind = kind)
                                }
                            }
                    }

                    if (draft.kind != SocialKind.POST) {
                        Spacer(Modifier.height(14.dp))
                        FarmField(
                            label = when {
                                selected?.provider == SocialProvider.BLUESKY -> "Target · at-uri|cid"
                                draft.kind == SocialKind.COMMENT -> "Target · status id to reply to"
                                else -> "Target · status id"
                            },
                            value = draft.target,
                            onChange = { draft = draft.copy(target = it) },
                            placeholder = "109876543210987654",
                        )
                    }

                    if (draft.kind != SocialKind.LIKE && draft.kind != SocialKind.REPOST) {
                        Spacer(Modifier.height(14.dp))
                        FarmField(
                            label = "Text",
                            value = draft.text,
                            onChange = { draft = draft.copy(text = it) },
                            singleLine = false,
                            minLines = 3,
                            placeholder = "What should go out?",
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Label("Repeat", Ink.Dim)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Once", 15 to "15m", 60 to "1h", 360 to "6h", 1440 to "24h").forEach { (minutes, label) ->
                            Option(label, draft.everyMinutes == minutes, Modifier.weight(1f)) {
                                draft = draft.copy(everyMinutes = minutes)
                            }
                        }
                    }

                    composeError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = monoStyle(10, Ink.Paper))
                    }

                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Run now") {
                        val failure = validate(draft)
                        if (failure != null) {
                            composeError = failure
                        } else {
                            composeError = null
                            val created = Social.addAction(draft.copy(accountId = draft.accountId))
                            SocialRunner.run(created.id)
                            draft = SocialAction(accountId = draft.accountId)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Schedule without running") {
                        val failure = validate(draft)
                        if (failure != null) {
                            composeError = failure
                        } else {
                            composeError = null
                            Social.addAction(draft)
                            draft = SocialAction(accountId = draft.accountId)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Actions") }

        if (actions.isEmpty()) {
            item {
                Text("Nothing scheduled yet", style = MaterialTheme.typography.bodySmall, color = Ink.Dim)
            }
        }

        items(actions, key = { it.id }) { action ->
            val account = accounts.firstOrNull { it.id == action.accountId }
            val outcome = results[action.id]
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(1.dp, Ink.LineSoft, RoundedCornerShape(4.dp))
                    .padding(13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${action.kind.label.uppercase()} · ${account?.title ?: "account removed"}",
                        style = monoStyle(9, Ink.Ash, FontWeight.Medium, 1.2f),
                        modifier = Modifier.weight(1f),
                    )
                    Text(Fmt.clock(action.lastAt).takeIf { action.lastAt > 0L } ?: "—", style = monoStyle(9, Ink.Dim))
                }
                if (action.text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(action.text, style = MaterialTheme.typography.bodyMedium, color = Ink.Chalk, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    outcome?.detail ?: action.lastNote.ifBlank { "not run yet" },
                    style = monoStyle(9, if (outcome?.ok == true || (outcome == null && action.lastOk)) Ink.Ash else Ink.Dim),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniButton("Run", onClick = { SocialRunner.run(action.id) })
                    Spacer(Modifier.width(8.dp))
                    MiniButton(if (action.enabled) "Pause" else "Arm") { SocialRunner.setEnabled(action, !action.enabled) }
                    Spacer(Modifier.width(8.dp))
                    MiniButton("Delete", onClick = { Social.removeAction(action.id) })
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (action.everyMinutes > 0) Fmt.every(action.everyMinutes * 60) else "ONCE",
                        style = monoStyle(8, Ink.Dim, spacing = 1.2f),
                    )
                }
            }
        }

        item { SectionHeader("Activity") }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Ink.Layer, RoundedCornerShape(4.dp))
                    .border(1.dp, Ink.LineSoft, RoundedCornerShape(4.dp))
                    .padding(10.dp),
            ) {
                if (lines.isEmpty()) {
                    Text("Nothing has run yet", style = monoStyle(9, Ink.Dim))
                } else {
                    lines.takeLast(8).forEach { line ->
                        Text(line, style = monoStyle(9, Ink.Ash))
                    }
                }
            }
        }
    }

    if (showAccount) {
        AddAccountDialog(
            draft = draftAccount,
            onChange = { draftAccount = it },
            onDismiss = { showAccount = false },
            onAdd = {
                val clean = Social.addAccount(draftAccount)
                draft = draft.copy(accountId = clean.id)
                showAccount = false
            },
        )
    }
}

private fun validate(action: SocialAction): String? {
    if (action.accountId.isBlank()) return "Pick an account"
    if (action.kind != SocialKind.LIKE && action.kind != SocialKind.REPOST && action.text.isBlank()) {
        return "Write something to send"
    }
    if (action.kind != SocialKind.POST && action.target.isBlank()) return "This action needs a target"
    return null
}

@Composable
private fun AddAccountDialog(
    draft: SocialAccount,
    onChange: (SocialAccount) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Layer2,
        title = { Text("Connect account", style = MaterialTheme.typography.titleMedium, color = Ink.Chalk) },
        text = {
            Column {
                Text(
                    "Provider",
                    style = monoStyle(9, Ink.Dim, spacing = 1.4f),
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SocialProvider.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { provider ->
                                Option(provider.label, draft.provider == provider, Modifier.weight(1f)) {
                                    onChange(draft.copy(provider = provider, baseUrl = provider.baseHint))
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FarmField("Label", draft.label, { onChange(draft.copy(label = it)) }, placeholder = "Personal")
                Spacer(Modifier.height(10.dp))
                FarmField(
                    if (draft.provider == SocialProvider.BLUESKY) "Handle" else "Handle or chat id",
                    draft.handle,
                    { onChange(draft.copy(handle = it)) },
                    placeholder = if (draft.provider == SocialProvider.BLUESKY) "you.bsky.social" else "optional",
                )
                if (draft.provider == SocialProvider.MASTODON || draft.provider == SocialProvider.BLUESKY || draft.provider == SocialProvider.GENERIC) {
                    Spacer(Modifier.height(10.dp))
                    FarmField("Instance / endpoint", draft.baseUrl, { onChange(draft.copy(baseUrl = it)) }, placeholder = draft.provider.baseHint)
                }
                Spacer(Modifier.height(10.dp))
                FarmField(
                    draft.provider.secretLabel,
                    draft.secret,
                    { onChange(draft.copy(secret = it)) },
                    placeholder = "stored on this device",
                    secret = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) {
                Text("Connect", color = Ink.Chalk, style = monoStyle(10, Ink.Chalk, FontWeight.Medium, 1.4f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Ink.Dim, style = monoStyle(10, Ink.Dim, FontWeight.Medium, 1.4f))
            }
        },
    )
}

@Composable
internal fun Option(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (selected) Ink.Paper else Color.Transparent, RoundedCornerShape(3.dp))
            .border(1.dp, if (selected) Ink.Paper else Ink.Line, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = monoStyle(8, if (selected) Ink.Void else Ink.Ash, FontWeight.Medium, 1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
