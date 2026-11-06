package com.phonefarm.app.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.phonefarm.app.engine.FarmEngine

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext as? Application ?: return
        FarmEngine.init(app)

        val state = FarmEngine.state.value
        if (!state.settings.bootRestore) return
        if (state.nodes.none { it.enabled }) return

        runCatching {
            if (FarmEngine.isRunning) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FarmService::class.java).setAction(FarmService.ACTION_START),
                )
            } else {
                FarmEngine.startFarm("boot")
            }
        }
    }
}
