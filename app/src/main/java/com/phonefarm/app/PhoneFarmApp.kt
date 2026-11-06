package com.phonefarm.app

import android.app.Application
import com.phonefarm.app.data.Remotes
import com.phonefarm.app.engine.FarmEngine
import com.phonefarm.app.engine.LiveHub
import com.phonefarm.app.update.UpdateManager

class PhoneFarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FarmEngine.init(this)
        UpdateManager.init(this)
        LiveHub.init(this)
        Remotes.init(this)
    }
}
