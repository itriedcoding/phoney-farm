package com.phonefarm.app.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.phonefarm.app.data.Governor
import com.phonefarm.app.data.Settings

/**
 * Reads live battery / thermal state and decides whether the farm should run throttled.
 * This is what keeps a 24/7 farm from cooking a phone.
 */
object PowerGovernor {

    @Volatile
    private var last = Governor()

    val current: Governor get() = last

    fun read(context: Context, settings: Settings): Governor {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

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

        val level = if (capacity in 0..100) capacity else 100
        var throttled = false
        var reason: String? = null

        if (settings.powerAware) {
            if (!charging && level <= settings.batteryFloor) {
                throttled = true
                reason = "battery $level%"
            } else if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
                throttled = true
                reason = "thermal ${thermalName(thermal).lowercase()}"
            }
        }

        val governor = Governor(level, charging, thermal, throttled, reason)
        last = governor
        return governor
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
