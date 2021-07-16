package com.leondeklerk.starling

import android.app.Application
import timber.log.Timber

class StarlingApplication : Application() /*ImageLoaderFactory*/ {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }

    // override fun newImageLoader(): ImageLoader {
    // TODO: Enable coil when possible
    // Enable Video and Gif features in Coil
    // return ImageLoader.Builder(applicationContext)
    //     .crossfade(true)
    //     .componentRegistry {
    //         add(VideoFrameFileFetcher(applicationContext))
    //         add(VideoFrameUriFetcher(applicationContext))
    //         add(VideoFrameDecoder(applicationContext))
    //         add(SvgDecoder(applicationContext))
    //         add(ImageDecoderDecoder(applicationContext))
    //         add(GifDecoder())
    //     }
    //     .launchInterceptorChainOnMainThread(false)
    //     .build()
    // }
}
