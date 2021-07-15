package com.leondeklerk.starling

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.fetch.VideoFrameFileFetcher
import coil.fetch.VideoFrameUriFetcher
import timber.log.Timber

class StarlingApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .crossfade(true)
            .componentRegistry {
                add(VideoFrameFileFetcher(applicationContext))
                add(VideoFrameUriFetcher(applicationContext))
                add(VideoFrameDecoder(applicationContext))
                add(ImageDecoderDecoder(applicationContext))
            }
            .build()
    }
}
