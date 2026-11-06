package com.phonefarm.app

import android.app.Application
import com.phonefarm.app.engine.FarmEngine
import com.phonefarm.app.update.UpdateManager

class PhoneFarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FarmEngine.init(this)
        UpdateManager.init(this)
    }
}
