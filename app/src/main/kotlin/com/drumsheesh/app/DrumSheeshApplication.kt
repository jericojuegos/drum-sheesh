package com.drumsheesh.app

import android.app.Application

class DrumSheeshApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(applicationContext)
    }
}
