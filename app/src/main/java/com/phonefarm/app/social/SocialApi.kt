package com.phonefarm.app.social

import com.phonefarm.app.data.ProxyEntry
import com.phonefarm.app.data.SocialAccount
import com.phonefarm.app.data.SocialAction
import com.phonefarm.app.data.SocialKind
import com.phonefarm.app.data.SocialProvider
import com.phonefarm.app.engine.net.Net
import com.phonefarm.app.engine.net.NetResponse
import java.time.Instant
import org.json.JSONObject

data class SocialOutcome(
    val ok: Boolean,
    val status: Int,
    val detail: String,
    val at: Long = System.currentTimeMillis(),
)

/**
 * Talks to the real APIs. Nothing is simulated: if a provider rejects the token you get its actual
 * status code and message back, and a successful call returns the object the server created.
 */
object SocialApi {

    fun execute(account: SocialAccount, action: SocialAction, proxy: ProxyEntry?): SocialOutcome = when (account.provider) {
        SocialProvider.MASTODON -> mastodon(account, action, proxy)
        SocialProvider.BLUESKY -> bluesky(account, action, proxy)
        SocialProvider.TELEGRAM -> telegram(account, action, proxy)
        SocialProvider.DISCORD -> discord(account, action, proxy)
        SocialProvider.GENERIC -> generic(account, action, proxy)
    }

    fun verify(account: SocialAccount, proxy: ProxyEntry?): SocialOutcome {
        val response = when (account.provider) {
            SocialProvider.MASTODON -> {
                val base = mastodonBase(account)
                Net.send("$base/api/v1/accounts/verify_credentials", headers = bearer(account.secret), proxy = proxy)
            }

            SocialProvider.BLUESKY -> {
                if (account.handle.isBlank() || account.secret.isBlank()) {
                    return SocialOutcome(false, 0, "handle and app password required")
                }
                Net.sendJson(
                    "${blueskyBase(account)}/xrpc/com.atproto.server.createSession",
                    JSONObject().put("identifier", account.handle.trim()).put("password", account.secret.trim()).toString(),
                    proxy = proxy,
                )
            }

            SocialProvider.TELEGRAM -> {
                val token = account.secret.trim()
                if (token.isEmpty()) return SocialOutcome(false, 0, "bot token required")
                Net.send("https://api.telegram.org/bot$token/getMe", proxy = proxy)
            }

            SocialProvider.DISCORD -> {
                val url = account.secret.trim().ifBlank { account.baseUrl.trim() }
                if (url.isEmpty()) return SocialOutcome(false, 0, "webhook URL required")
                Net.send(url, proxy = proxy)
            }

            SocialProvider.GENERIC -> {
                if (account.baseUrl.isBlank()) return SocialOutcome(false, 0, "endpoint URL required")
                Net.send(account.baseUrl.trim(), headers = bearer(account.secret), proxy = proxy)
            }
        }
        return if (response.ok) {
            SocialOutcome(true, response.status, summary(account, response))
        } else {
            SocialOutcome(false, response.status, "${response.status} · ${response.snippet.ifBlank { response.error ?: "failed" }}")
        }
    }

    // ------------------------------------------------------------------ providers

    private fun mastodon(account: SocialAccount, action: SocialAction, proxy: ProxyEntry?): SocialOutcome {
        val token = account.secret.trim()
        if (token.isEmpty()) return SocialOutcome(false, 0, "no access token saved")
        val base = mastodonBase(account)
        val auth = bearer(token)
        val target = action.target.trim()
        val statuses = "$base/api/v1/statuses"

        val response = when (action.kind) {
            SocialKind.POST -> Net.sendForm(statuses, mapOf("status" to action.text), auth, proxy)
            SocialKind.COMMENT -> {
                if (target.isEmpty()) return SocialOutcome(false, 0, "reply needs the status id")
                Net.sendForm(statuses, mapOf("status" to action.text, "in_reply_to_id" to target), auth, proxy)
            }

            SocialKind.LIKE -> {
                if (target.isEmpty()) return SocialOutcome(false, 0, "like needs the status id")
                Net.send("$statuses/$target/favourite", "POST", auth, proxy = proxy)
            }

            SocialKind.REPOST -> {
                if (target.isEmpty()) return SocialOutcome(false, 0, "repost needs the status id")
                Net.send("$statuses/$target/reblog", "POST", auth, proxy = proxy)
            }
        }
        return outcome(response, account, action)
    }

    private fun bluesky(account: SocialAccount, action: SocialAction, proxy: ProxyEntry?): SocialOutcome {
        val handle = account.handle.trim()
        val password = account.secret.trim()
        if (handle.isEmpty() || password.isEmpty()) return SocialOutcome(false, 0, "handle and app password required")
        val base = blueskyBase(account)
        val session = Net.sendJson(
            "$base/xrpc/com.atproto.server.createSession",
            JSONObject().put("identifier", handle).put("password", password).toString(),
            proxy = proxy,
        )
        if (!session.ok) {
            return SocialOutcome(false, session.status, "sign-in failed · ${session.snippet.ifBlank { session.error ?: "" }}")
        }
        val json = runCatching { JSONObject(session.body) }.getOrNull()
            ?: return SocialOutcome(false, session.status, "unreadable session response")
        val jwt = json.optString("accessJwt")
        val did = json.optString("did")
        if (jwt.isEmpty() || did.isEmpty()) return SocialOutcome(false, session.status, "session had no token")

        val createdAt = Instant.now().toString()
        val record = JSONObject()
        val collection: String
        when (action.kind) {
            SocialKind.POST, SocialKind.COMMENT -> {
                collection = "app.bsky.feed.post"
                record.put("\$type", collection).put("text", action.text).put("createdAt", createdAt)
            }

            SocialKind.LIKE, SocialKind.REPOST -> {
                val uri = action.target.substringBefore('|').trim()
                val cid = action.target.substringAfter('|', "").trim()
                if (uri.isEmpty() || cid.isEmpty()) return SocialOutcome(false, 0, "paste the post as at-uri|cid")
                collection = if (action.kind == SocialKind.LIKE) "app.bsky.feed.like" else "app.bsky.feed.repost"
                record.put("\$type", collection)
                    .put("subject", JSONObject().put("uri", uri).put("cid", cid))
                    .put("createdAt", createdAt)
            }
        }

        val body = JSONObject()
            .put("repo", did)
            .put("collection", collection)
            .put("record", record)
            .toString()
        val response = Net.sendJson(
            "$base/xrpc/com.atproto.repo.createRecord",
            body,
            bearer(jwt),
            proxy,
        )
        return outcome(response, account, action)
    }

    private fun telegram(account: SocialAccount, action: SocialAction, proxy: ProxyEntry?): SocialOutcome {
        val token = account.secret.trim()
        if (token.isEmpty()) return SocialOutcome(false, 0, "bot token required")
        if (action.kind != SocialKind.POST && action.kind != SocialKind.COMMENT) {
            return SocialOutcome(false, 0, "Telegram bots cannot like or repost")
        }
        val chat = action.target.trim().ifBlank { account.handle.trim() }
        if (chat.isEmpty()) return SocialOutcome(false, 0, "chat id or @channel required")
        val response = Net.sendForm(
            "https://api.telegram.org/bot$token/sendMessage",
            mapOf(
                "chat_id" to chat,
                "text" to action.text,
                "disable_web_page_preview" to "true",
            ),
            proxy = proxy,
        )
        return outcome(response, account, action)
    }

    private fun discord(account: SocialAccount, action: SocialAction, proxy: ProxyEntry?): SocialOutcome {
        val url = account.secret.trim().ifBlank { account.baseUrl.trim() }
        if (url.isEmpty()) return SocialOutcome(false, 0, "webhook URL required")
        if (action.kind != SocialKind.POST && action.kind != SocialKind.COMMENT) {
            return SocialOutcome(false, 0, "webhooks can only post")
        }
        val payload = JSONObject()
            .put("content", action.text.take(1_900))
            .put("username", account.label.ifBlank { "Phone Farm" }.take(80))
            .toString()
        val response = Net.sendJson(url, payload, proxy = proxy)
        return outcome(response, account, action)
    }

    private fun generic(account: SocialAccount, action: SocialAction, proxy: ProxyEntry?): SocialOutcome {
        val url = account.baseUrl.trim()
        if (url.isEmpty()) return SocialOutcome(false, 0, "endpoint URL required")
        val payload = JSONObject()
            .put("kind", action.kind.name.lowercase())
            .put("text", action.text)
            .put("target", action.target)
            .put("account", account.handle)
            .toString()
        val response = Net.sendJson(url, payload, bearer(account.secret), proxy)
        return outcome(response, account, action)
    }

    // ------------------------------------------------------------------ helpers

    private fun outcome(response: NetResponse, account: SocialAccount, action: SocialAction): SocialOutcome {
        if (!response.ok) {
            val message = response.snippet.ifBlank { response.error ?: "request failed" }
            return SocialOutcome(false, response.status, "${response.status} · $message")
        }
        val created = runCatching { JSONObject(response.body) }.getOrNull()
        val url = created?.optString("url").orEmpty()
            .ifBlank { created?.optString("uri").orEmpty() }
        val verb = when (action.kind) {
            SocialKind.POST -> "posted"
            SocialKind.COMMENT -> "replied"
            SocialKind.LIKE -> "liked"
            SocialKind.REPOST -> "reposted"
        }
        val detail = buildString {
            append(account.provider.label).append(' ').append(verb).append(" · ").append(response.status)
            if (url.isNotBlank()) append(" · ").append(url)
            append(" · ").append(response.latencyMs).append("ms")
        }
        return SocialOutcome(true, response.status, detail)
    }

    private fun summary(account: SocialAccount, response: NetResponse): String {
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        val name = json?.optString("username").orEmpty()
            .ifBlank { json?.optString("display_name").orEmpty() }
            .ifBlank { json?.optString("did").orEmpty() }
        return buildString {
            append(account.provider.label).append(" ok · ").append(response.status)
            if (name.isNotBlank()) append(" · ").append(name)
            append(" · ").append(response.latencyMs).append("ms")
        }
    }

    private fun bearer(token: String): Map<String, String> =
        if (token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${token.trim()}")

    private fun mastodonBase(account: SocialAccount): String =
        account.baseUrl.trim().trimEnd('/').ifBlank { "https://mastodon.social" }

    private fun blueskyBase(account: SocialAccount): String =
        account.baseUrl.trim().trimEnd('/').ifBlank { "https://bsky.social" }
}
