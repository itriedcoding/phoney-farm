package com.phonefarm.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.phonefarm.app.engine.FarmEngine
import com.phonefarm.app.ui.FarmApp
import com.phonefarm.app.ui.PhoneFarmTheme
import com.phonefarm.app.update.UpdateManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        FarmEngine.init(application)
        UpdateManager.init(application)
        resumeRuntime()
        requestNotificationPermission()

        setContent {
            PhoneFarmTheme {
                FarmApp()
            }
        }

        UpdateManager.checkQuietly()
    }

    override fun onResume() {
        super.onResume()
        UpdateManager.checkQuietly()
    }

    private fun resumeRuntime() {
        val state = FarmEngine.state.value
        when {
            state.running -> FarmEngine.startFarm("relaunch")
            state.settings.resumeOnLaunch && state.nodes.any { it.enabled } -> FarmEngine.startFarm("launch")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41) }
        }
    }
}
