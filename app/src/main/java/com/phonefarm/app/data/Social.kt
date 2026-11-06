package com.phonefarm.app.data

import android.app.Application
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class SocialProvider(
    val label: String,
    val baseHint: String,
    val secretLabel: String,
    val canLike: Boolean,
    val canRepost: Boolean,
    val note: String,
) {
    MASTODON(
        label = "Mastodon",
        baseHint = "https://mastodon.social",
        secretLabel = "Access token",
        canLike = true,
        canRepost = true,
        note = "Real Mastodon API · post, reply, favourite, boost",
    ),
    BLUESKY(
        label = "Bluesky",
        baseHint = "https://bsky.social",
        secretLabel = "App password",
        canLike = true,
        canRepost = true,
        note = "Real AT Protocol · post, like, repost",
    ),
    TELEGRAM(
        label = "Telegram",
        baseHint = "",
        secretLabel = "Bot token",
        canLike = false,
        canRepost = false,
        note = "Bot API · sendMessage to a chat or channel you own",
    ),
    DISCORD(
        label = "Discord",
        baseHint = "",
        secretLabel = "Webhook URL",
        canLike = false,
        canRepost = false,
        note = "Webhook · posts into a channel you control",
    ),
    GENERIC(
        label = "Custom API",
        baseHint = "",
        secretLabel = "Bearer token",
        canLike = false,
        canRepost = false,
        note = "Any JSON endpoint you own, sent with your bearer token",
    ),
}

enum class SocialKind(val label: String) {
    POST("Post"),
    COMMENT("Reply"),
    LIKE("Like"),
    REPOST("Repost"),
}

data class SocialAccount(
    val id: String = UUID.randomUUID().toString(),
    val provider: SocialProvider = SocialProvider.MASTODON,
    val label: String = "",
    val handle: String = "",
    val baseUrl: String = "",
    val secret: String = "",
    val proxyId: String = "",
    val enabled: Boolean = true,
) {
    val title: String get() = label.ifBlank { handle.ifBlank { provider.label } }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("provider", provider.name)
        put("label", label)
        put("handle", handle)
        put("baseUrl", baseUrl)
        put("secret", secret)
        put("proxyId", proxyId)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(o: JSONObject) = SocialAccount(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            provider = runCatching { SocialProvider.valueOf(o.optString("provider", "MASTODON")) }
                .getOrDefault(SocialProvider.MASTODON),
            label = o.optString("label"),
            handle = o.optString("handle"),
            baseUrl = o.optString("baseUrl"),
            secret = o.optString("secret"),
            proxyId = o.optString("proxyId"),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

data class SocialAction(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String = "",
    val kind: SocialKind = SocialKind.POST,
    val target: String = "",
    val text: String = "",
    val everyMinutes: Int = 0,
    val enabled: Boolean = true,
    val lastAt: Long = 0L,
    val lastOk: Boolean = false,
    val lastNote: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("accountId", accountId)
        put("kind", kind.name)
        put("target", target)
        put("text", text)
        put("everyMinutes", everyMinutes)
        put("enabled", enabled)
        put("lastAt", lastAt)
        put("lastOk", lastOk)
        put("lastNote", lastNote)
    }

    companion object {
        fun fromJson(o: JSONObject) = SocialAction(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            accountId = o.optString("accountId"),
            kind = runCatching { SocialKind.valueOf(o.optString("kind", "POST")) }.getOrDefault(SocialKind.POST),
            target = o.optString("target"),
            text = o.optString("text"),
            everyMinutes = o.optInt("everyMinutes", 0),
            enabled = o.optBoolean("enabled", true),
            lastAt = o.optLong("lastAt", 0L),
            lastOk = o.optBoolean("lastOk", false),
            lastNote = o.optString("lastNote"),
        )
    }
}

/** Accounts and their scheduled actions. Everything stays on this device. */
object Social {

    private val _accounts = MutableStateFlow<List<SocialAccount>>(emptyList())
    val accounts: StateFlow<List<SocialAccount>> = _accounts.asStateFlow()

    private val _actions = MutableStateFlow<List<SocialAction>>(emptyList())
    val actions: StateFlow<List<SocialAction>> = _actions.asStateFlow()

    private lateinit var file: File

    fun init(application: Application) {
        if (::file.isInitialized) return
        val dir = File(application.filesDir, "farm").apply { mkdirs() }
        file = File(dir, "social.json")
        runCatching {
            val root = JSONObject(file.readText())
            val accounts = root.optJSONArray("accounts") ?: JSONArray()
            val actions = root.optJSONArray("actions") ?: JSONArray()
            _accounts.value = (0 until accounts.length()).mapNotNull { index ->
                accounts.optJSONObject(index)?.let { SocialAccount.fromJson(it) }
            }
            _actions.value = (0 until actions.length()).mapNotNull { index ->
                actions.optJSONObject(index)?.let { SocialAction.fromJson(it) }
            }
        }
    }

    fun account(id: String): SocialAccount? = _accounts.value.firstOrNull { it.id == id }

    fun addAccount(account: SocialAccount): SocialAccount {
        val clean = account.copy(
            label = account.label.trim().take(40),
            handle = account.handle.trim(),
            baseUrl = account.baseUrl.trim().trimEnd('/'),
            secret = account.secret.trim(),
        )
        _accounts.value = _accounts.value + clean
        persist()
        return clean
    }

    fun updateAccount(account: SocialAccount) {
        _accounts.value = _accounts.value.map { if (it.id == account.id) account else it }
        persist()
    }

    fun removeAccount(id: String) {
        _accounts.value = _accounts.value.filterNot { it.id == id }
        _actions.value = _actions.value.filterNot { it.accountId == id }
        persist()
    }

    fun addAction(action: SocialAction): SocialAction {
        val clean = action.copy(text = action.text.trim(), target = action.target.trim())
        _actions.value = _actions.value + clean
        persist()
        return clean
    }

    fun updateAction(action: SocialAction) {
        _actions.value = _actions.value.map { if (it.id == action.id) action else it }
        persist()
    }

    fun removeAction(id: String) {
        _actions.value = _actions.value.filterNot { it.id == id }
        persist()
    }

    fun record(id: String, ok: Boolean, note: String) {
        _actions.value = _actions.value.map {
            if (it.id == id) it.copy(lastAt = System.currentTimeMillis(), lastOk = ok, lastNote = note.take(220)) else it
        }
        persist()
    }

    private fun persist() {
        if (!::file.isInitialized) return
        runCatching {
            file.parentFile?.mkdirs()
            val root = JSONObject()
            root.put("accounts", JSONArray().apply { _accounts.value.forEach { put(it.toJson()) } })
            root.put("actions", JSONArray().apply { _actions.value.forEach { put(it.toJson()) } })
            file.writeText(root.toString())
        }
    }
}
