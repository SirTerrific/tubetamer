package com.sirterrific.tubetamer.mobile

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sirterrific.tubetamer.AppContainer

class MobileApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader(context)
}
