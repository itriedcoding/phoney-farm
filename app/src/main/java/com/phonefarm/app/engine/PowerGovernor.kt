package com.phonefarm.app.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.phonefarm.app.data.Governor
import com.phonefarm.app.data.Settings

/**
 * Reads live battery / thermal state and decides whether the farm should run throttled.
 * This is what keeps a 24/7 farm from cooking a phone.
 */
object PowerGovernor {

    private const val SAMPLE_INTERVAL_MS = 10_000L

    private data class Hardware(val battery: Int, val charging: Boolean, val thermal: Int)

    @Volatile
    private var last = Governor()

    @Volatile
    private var hardware = Hardware(100, false, 0)

    @Volatile
    private var sampledAt = 0L

    val current: Governor get() = last

    fun read(context: Context, settings: Settings): Governor {
        val now = SystemClock.elapsedRealtime()
        if (now - sampledAt > SAMPLE_INTERVAL_MS || sampledAt == 0L) {
            hardware = sample(context)
            sampledAt = now
        }
        val state = hardware

        var throttled = false
        var reason: String? = null
        if (settings.powerAware) {
            if (!state.charging && state.battery <= settings.batteryFloor) {
                throttled = true
                reason = "battery ${state.battery}%"
            } else if (state.thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
                throttled = true
                reason = "thermal ${thermalName(state.thermal).lowercase()}"
            }
        }

        val governor = Governor(state.battery, state.charging, state.thermal, throttled, reason)
        last = governor
        return governor
    }

    private fun sample(context: Context): Hardware {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) {
            power.currentThermalStatus
        } else {
            0
        }

        return Hardware(if (capacity in 0..100) capacity else 100, charging, thermal)
    }

    fun thermalName(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Nominal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
        else -> "Unknown"
    }
}
