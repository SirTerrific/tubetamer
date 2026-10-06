package com.sirterrific.tubetamer

import android.app.Application

class TubeTamerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
