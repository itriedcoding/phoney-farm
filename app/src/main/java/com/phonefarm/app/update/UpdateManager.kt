package com.phonefarm.app.update

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phonefarm.app.BuildConfig
import com.phonefarm.app.MainActivity
import com.phonefarm.app.R
import com.phonefarm.app.data.LogLevel
import com.phonefarm.app.data.UpdateChannel
import com.phonefarm.app.engine.FarmEngine
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * In-app updater wired directly to the project's GitHub Releases feed.
 * Checks the release page, downloads the attached APK, verifies that it is genuinely this app
 * and hands it to the system installer.
 */
object UpdateManager {

    const val ACTION_INSTALL_STATUS = "com.phonefarm.app.action.INSTALL_STATUS"

    private const val CHANNEL_ID = "farm_updates"
    private const val NOTIFICATION_ID = 4102
    private const val CHECK_INTERVAL_MS = 3L * 60L * 60L * 1_000L

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data class UpToDate(val version: String) : UpdateState
        data class Available(val release: Release) : UpdateState
        data class Downloading(val release: Release, val progress: Int) : UpdateState
        data class Ready(val release: Release, val file: File) : UpdateState
        data object Installing : UpdateState
        data class Failed(val message: String) : UpdateState
    }

    data class Release(
        val version: String,
        val tag: String,
        val notes: String,
        val apkUrl: String,
        val size: Long,
        val prerelease: Boolean,
        val published: String,
        val pageUrl: String,
    )

    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    @Volatile
    private var lastCheck = 0L

    fun init(application: Application) {
        if (::app.isInitialized) return
        app = application
        createChannel()
    }

    fun checkQuietly() {
        if (!::app.isInitialized) return
        if (!FarmEngine.state.value.settings.autoUpdate) return
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        if (!::app.isInitialized) return
        when (_state.value) {
            is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> return
            else -> Unit
        }
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val channel = FarmEngine.state.value.settings.channel
                val release = fetchReleases(channel).firstOrNull {
                    isNewer(it.version, BuildConfig.VERSION_NAME)
                }
                lastCheck = System.currentTimeMillis()
                if (release == null) {
                    _state.value = UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                    if (manual) log(LogLevel.INFO, "already on the newest release (${BuildConfig.VERSION_NAME})")
                } else {
                    _state.value = UpdateState.Available(release)
                    log(LogLevel.INFO, "release ${release.version} available")
                    if (FarmEngine.state.value.settings.autoDownload) {
                        download(release)
                    } else {
                        notifyRelease(release)
                    }
                }
            } catch (t: Throwable) {
                _state.value = UpdateState.Failed(t.message ?: "update check failed")
                if (manual) log(LogLevel.WARN, t.message ?: "update check failed")
            }
        }
    }

    fun download(release: Release) {
        if (!::app.isInitialized) return
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(release, 0)
        log(LogLevel.INFO, "downloading ${release.version}")

        scope.launch {
            var connection: HttpURLConnection? = null
            try {
                val directory = File(app.cacheDir, "updates").apply { mkdirs() }
                directory.listFiles()?.forEach { runCatching { it.delete() } }
                val target = File(directory, "PhoneFarm-${release.version}.apk")

                connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PhoneFarm/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("download failed · HTTP $code")

                val total = if (release.size > 0) release.size else connection.contentLengthLong
                var written = 0L
                var lastReported = -1
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val progress = ((written * 100) / total).toInt().coerceIn(0, 100)
                                if (progress != lastReported && progress % 2 == 0) {
                                    lastReported = progress
                                    _state.value = UpdateState.Downloading(release, progress)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                validate(target)
                _state.value = UpdateState.Ready(release, target)
                log(LogLevel.INFO, "update ${release.version} verified · ${target.length() / 1024} KB")
                notifyRelease(release)
            } catch (t: Throwable) {
                _state.value = UpdateState.Failed(t.message ?: "download failed")
                log(LogLevel.WARN, "update failed · ${t.message ?: "unknown"}")
            } finally {
                runCatching { connection?.disconnect() }
            }
        }
    }

    fun install(file: File) {
        if (!::app.isInitialized) return
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.Failed("Allow “Install unknown apps” for Phone Farm, then retry")
            openInstallSettings()
            return
        }
        scope.launch {
            try {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("phonefarm.apk", 0, file.length()).use { output ->
                        file.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                        session.fsync(output)
                    }
                    val callback = Intent(app, InstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                    val pending = PendingIntent.getBroadcast(
                        app,
                        sessionId,
                        callback,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pending.intentSender)
                }
                _state.value = UpdateState.Installing
                log(LogLevel.INFO, "installer session opened")
            } catch (t: Throwable) {
                _state.value = UpdateState.Failed(t.message ?: "install failed")
                log(LogLevel.ERROR, "install failed · ${t.message ?: "unknown"}")
            }
        }
    }

    fun onInstalled() {
        log(LogLevel.INFO, "update installed")
        _state.value = UpdateState.UpToDate(BuildConfig.VERSION_NAME)
    }

    fun onInstallFailed(message: String) {
        _state.value = UpdateState.Failed(message)
    }

    fun openInstallSettings() {
        if (!::app.isInitialized) return
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }
    }

    fun openReleasePage() {
        if (!::app.isInitialized) return
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.RELEASES_PAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // ---------------------------------------------------------------- internals

    @Suppress("DEPRECATION")
    private fun validate(file: File) {
        if (!file.exists() || file.length() < 100_000L) throw IllegalStateException("download incomplete")
        val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IllegalStateException("downloaded file is not an APK")
        if (info.packageName != app.packageName) throw IllegalStateException("APK belongs to another app")
        if (info.versionCode.toLong() < BuildConfig.VERSION_CODE.toLong()) {
            throw IllegalStateException("APK is older than the installed build")
        }
    }

    private fun fetchReleases(channel: UpdateChannel): List<Release> {
        val connection = (URL("${BuildConfig.RELEASES_API}?per_page=20").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "PhoneFarm/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = connection.responseCode
            when {
                code == 403 || code == 429 -> throw IllegalStateException("GitHub rate limit · retry later")
                code == 404 -> throw IllegalStateException("no releases published yet")
                code != 200 -> throw IllegalStateException("release feed returned HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            val releases = ArrayList<Release>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optBoolean("draft")) continue
                val prerelease = item.optBoolean("prerelease")
                if (channel == UpdateChannel.STABLE && prerelease) continue
                val assets = item.optJSONArray("assets") ?: continue

                var apkUrl = ""
                var size = 0L
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        size = asset.optLong("size")
                        break
                    }
                }
                if (apkUrl.isEmpty()) continue

                releases += Release(
                    version = item.optString("tag_name").removePrefix("v").trim(),
                    tag = item.optString("tag_name"),
                    notes = item.optString("body").trim(),
                    apkUrl = apkUrl,
                    size = size,
                    prerelease = prerelease,
                    published = item.optString("published_at"),
                    pageUrl = item.optString("html_url"),
                )
            }
            return releases
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        fun parse(value: String): List<Int> =
            value.substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }

        val a = parse(candidate)
        val b = parse(current)
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun notifyRelease(release: Release) {
        val open = PendingIntent.getActivity(
            app,
            20,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_farm)
            .setContentTitle("Phone Farm ${release.version}")
            .setContentText("Update ready · tap to review")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification) }
    }

    private fun createChannel() {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "New Phone Farm builds from GitHub Releases"
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun log(level: LogLevel, message: String) {
        FarmEngine.log(level, "update", message)
    }
}
