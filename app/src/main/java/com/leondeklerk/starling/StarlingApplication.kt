package com.leondeklerk.starling

import android.app.Application
import timber.log.Timber

class StarlingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
