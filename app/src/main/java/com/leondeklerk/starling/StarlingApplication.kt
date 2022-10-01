package com.leondeklerk.starling

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import kotlinx.coroutines.delay
import timber.log.Timber

class StarlingApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .logger(DebugLogger())
            .components {
                add { chain ->
                    delay(0)
                    chain.proceed(chain.request)
                }
            }.build()
    }
}
