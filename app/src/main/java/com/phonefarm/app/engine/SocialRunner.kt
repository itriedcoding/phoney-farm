package com.phonefarm.app.engine

import com.phonefarm.app.data.Proxies
import com.phonefarm.app.data.Social
import com.phonefarm.app.data.SocialAction
import com.phonefarm.app.social.SocialApi
import com.phonefarm.app.social.SocialOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Executes social actions through the real provider APIs and repeats the ones with an interval.
 * The loop is idle-cheap: it wakes every 20 seconds and only touches actions that are actually due.
 */
object SocialRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = ConcurrentHashMap.newKeySet<String>()

    private val _results = MutableStateFlow<Map<String, SocialOutcome>>(emptyMap())
    val results: StateFlow<Map<String, SocialOutcome>> = _results.asStateFlow()

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(20_000L)
            }
        }
    }

    fun run(id: String) {
        val action = Social.actions.value.firstOrNull { it.id == id } ?: return
        val account = Social.account(action.accountId) ?: run {
            Social.record(id, false, "account was removed")
            log("skipped · account missing")
            return
        }
        if (!busy.add(id)) return
        log("${account.provider.label} ${action.kind.label.lowercase()} · sending")
        scope.launch {
            val proxy = Proxies.find(account.proxyId)
            val outcome = runCatching { SocialApi.execute(account, action, proxy) }
                .getOrElse { SocialOutcome(false, 0, it.message ?: it.javaClass.simpleName) }
            Social.record(id, outcome.ok, outcome.detail)
            _results.value = _results.value + (id to outcome)
            log("${if (outcome.ok) "ok" else "fail"} · ${account.title} · ${outcome.detail.take(120)}")
            busy.remove(id)
        }
    }

    fun verify(accountId: String) {
        val account = Social.account(accountId) ?: return
        val key = "verify:$accountId"
        if (!busy.add(key)) return
        log("checking ${account.provider.label} credentials · ${account.title}")
        scope.launch {
            val outcome = runCatching { SocialApi.verify(account, Proxies.find(account.proxyId)) }
                .getOrElse { SocialOutcome(false, 0, it.message ?: it.javaClass.simpleName) }
            _results.value = _results.value + (key to outcome)
            log("${if (outcome.ok) "credentials ok" else "credentials fail"} · ${account.title} · ${outcome.detail.take(120)}")
            busy.remove(key)
        }
    }

    fun setEnabled(action: SocialAction, enabled: Boolean) {
        Social.updateAction(action.copy(enabled = enabled))
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        Social.actions.value
            .filter { it.enabled && it.everyMinutes > 0 }
            .filter { it.id !in busy }
            .filter { it.lastAt == 0L || now - it.lastAt >= it.everyMinutes * 60_000L }
            .take(2)
            .forEach { run(it.id) }
    }

    private fun log(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _lines.value = (_lines.value + "$stamp  $line").takeLast(120)
    }
}
