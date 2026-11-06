package com.phonefarm.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.phonefarm.app.MainActivity
import com.phonefarm.app.R
import com.phonefarm.app.data.FarmState
import com.phonefarm.app.engine.FarmEngine
import com.phonefarm.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FarmService : Service() {

    companion object {
        const val ACTION_START = "com.phonefarm.app.action.START"
        const val ACTION_STOP = "com.phonefarm.app.action.STOP"
        const val ACTION_TOGGLE = "com.phonefarm.app.action.TOGGLE"
        private const val CHANNEL_ID = "farm_runtime"
        private const val NOTIFICATION_ID = 4101
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastWakeRefresh = 0L
    private var lastLine = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(FarmEngine.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        scope.launch {
            FarmEngine.state.collect { state ->
                val line = statusLine(state)
                if (line != lastLine) {
                    lastLine = line
                    safeNotify(buildNotification(state))
                }
                syncWakeLock(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                FarmEngine.stopFarm("notification")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                if (FarmEngine.isRunning) {
                    FarmEngine.stopFarm("notification")
                } else {
                    FarmEngine.startFarm("notification")
                }
                if (!FarmEngine.isRunning) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        if (!FarmEngine.isRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    private fun statusLine(state: FarmState): String = if (state.running) {
        "${state.enabledCount} node(s) live · ${Fmt.uptime(state.uptimeMs)} · ${state.totalCycles} cycles"
    } else {
        "Paused"
    }

    private fun buildNotification(state: FarmState): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this,
            2,
            Intent(this, FarmService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            3,
            Intent(this, FarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_farm)
            .setContentTitle("Phone Farm")
            .setContentText(statusLine(state))
            .setSubText(if (state.governor.throttled) "Throttled · ${state.governor.reason}" else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(Color.WHITE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, if (state.running) "Pause" else "Resume", toggle)
            .addAction(0, "Stop", stop)

        if (state.totalFailures > 0) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${statusLine(state)} · ${state.totalFailures} failure(s)"),
            )
        }
        return builder.build()
    }

    private fun safeNotify(notification: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Farm runtime",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the farm alive in the background"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun syncWakeLock(state: FarmState) {
        val needed = state.running && state.settings.wakeLock
        if (!needed) {
            runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
            return
        }
        if (wakeLock == null) {
            val power = getSystemService(POWER_SERVICE) as? PowerManager ?: return
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneFarm::engine").apply {
                setReferenceCounted(false)
            }
        }
        val now = SystemClock.elapsedRealtime()
        if (wakeLock?.isHeld != true || now - lastWakeRefresh > 9 * 60 * 1000L) {
            runCatching { wakeLock?.acquire(10 * 60 * 1000L) }
            lastWakeRefresh = now
        }
    }
}
